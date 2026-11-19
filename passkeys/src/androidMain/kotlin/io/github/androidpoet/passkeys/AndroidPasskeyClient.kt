package io.github.androidpoet.passkeys

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PendingGetCredentialRequest
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.pendingGetCredentialRequest
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.passkeyResult
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

public class AndroidPasskeyClient : PasskeyClient {
    private val credentialProvider: AndroidCredentialProvider

    public constructor(activity: Activity) : this(AndroidCredentialManagerProvider(activity))

    internal constructor(credentialProvider: AndroidCredentialProvider) {
        this.credentialProvider = credentialProvider
    }

    // Normalization, the Credential Manager call and response parsing all sit
    // inside one boundary: a malformed provider response is a Failure, never a
    // crash, and coroutine cancellation propagates instead of becoming one.
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        passkeyResult(mapError = { it.toPasskeyException() }) {
            val requestJson = PasskeyPayloadMapper.creationRequestJson(options.requestJson)
            val responseJson =
                credentialProvider.create(
                    requestJson = requestJson,
                    preferImmediatelyAvailableCredentials = options.preferImmediatelyAvailableCredentials,
                    isConditionalCreateRequest = options.isConditionalCreateRequest,
                )
            PasskeyPayloadMapper.creationResponse(responseJson)
        }

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        passkeyResult(mapError = { it.toPasskeyException() }) {
            val requestJson = PasskeyPayloadMapper.authenticationRequestJson(options.requestJson)
            PasskeyPayloadMapper.authenticationResponse(
                credentialProvider.authenticate(requestJson, options.preferImmediatelyAvailableCredentials),
            )
        }

    /**
     * Offers the user's passkeys in the keyboard's autofill suggestions for
     * [view] (typically the username field) instead of opening a sheet.
     *
     * Suspends until the user picks a passkey there; nothing is shown or
     * returned otherwise, so launch it when the sign-in screen opens and
     * cancel the coroutine when it closes. Requires Android 15+; older
     * versions fail with [PasskeyException.Unsupported].
     */
    public suspend fun authenticateWithAutofill(
        view: View,
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        passkeyResult(mapError = { it.toPasskeyException() }) {
            val requestJson = PasskeyPayloadMapper.authenticationRequestJson(options.requestJson)
            PasskeyPayloadMapper.authenticationResponse(credentialProvider.authenticateWithAutofill(view, requestJson))
        }

    private fun Throwable.toPasskeyException(): PasskeyException =
        when (this) {
            is CreateCredentialException -> toCreatePasskeyException()
            is GetCredentialException -> toGetPasskeyException()
            else -> PasskeyPayloadMapper.exception(this)
        }
}

internal interface AndroidCredentialProvider {
    suspend fun create(
        requestJson: String,
        preferImmediatelyAvailableCredentials: Boolean,
        isConditionalCreateRequest: Boolean,
    ): String

    suspend fun authenticate(
        requestJson: String,
        preferImmediatelyAvailableCredentials: Boolean,
    ): String

    suspend fun authenticateWithAutofill(
        view: View,
        requestJson: String,
    ): String
}

private class AndroidCredentialManagerProvider(
    private val activity: Activity,
    private val credentialManager: CredentialManager = CredentialManager.create(activity),
) : AndroidCredentialProvider {
    override suspend fun create(
        requestJson: String,
        preferImmediatelyAvailableCredentials: Boolean,
        isConditionalCreateRequest: Boolean,
    ): String {
        val response =
            credentialManager.createCredential(
                context = activity,
                request =
                    CreatePublicKeyCredentialRequest(
                        requestJson,
                        null,
                        preferImmediatelyAvailableCredentials,
                        null,
                        false,
                        isConditionalCreateRequest,
                    ),
            )
        return (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
            ?: throw PasskeyException.Unexpected(IllegalStateException("Credential Manager returned ${response::class.qualifiedName}"))
    }

    override suspend fun authenticate(
        requestJson: String,
        preferImmediatelyAvailableCredentials: Boolean,
    ): String {
        val request =
            GetCredentialRequest(
                credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson)),
                preferImmediatelyAvailableCredentials = preferImmediatelyAvailableCredentials,
            )
        return credentialManager.getCredential(context = activity, request = request).authenticationResponseJson()
    }

    override suspend fun authenticateWithAutofill(
        view: View,
        requestJson: String,
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            throw PasskeyException.Unsupported(IllegalStateException("passkey autofill suggestions require Android 15 or newer"))
        }
        val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson)))
        val response =
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    var pending: PendingGetCredentialRequest? = null
                    // Detach only our own request: a newer call may already have
                    // replaced it on the same view.
                    val detach =
                        Runnable {
                            if (view.pendingGetCredentialRequest === pending) view.pendingGetCredentialRequest = null
                        }
                    pending =
                        PendingGetCredentialRequest(request) { response ->
                            view.post(detach)
                            if (continuation.isActive) continuation.resume(response)
                        }
                    continuation.invokeOnCancellation { view.post(detach) }
                    view.pendingGetCredentialRequest = pending
                }
            }
        return response.authenticationResponseJson()
    }

    private fun GetCredentialResponse.authenticationResponseJson(): String =
        (credential as? PublicKeyCredential)?.authenticationResponseJson
            ?: throw PasskeyException.Unexpected(
                IllegalStateException("Credential Manager returned ${credential::class.qualifiedName}"),
            )
}
