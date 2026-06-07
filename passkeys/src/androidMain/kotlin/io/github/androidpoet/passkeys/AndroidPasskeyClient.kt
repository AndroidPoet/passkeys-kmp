package io.github.androidpoet.passkeys

import android.app.Activity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse

public class AndroidPasskeyClient : PasskeyClient {
    private val credentialProvider: AndroidCredentialProvider

    public constructor(activity: Activity) : this(AndroidCredentialManagerProvider(activity))

    internal constructor(credentialProvider: AndroidCredentialProvider) {
        this.credentialProvider = credentialProvider
    }

    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        runCatching {
            val requestJson = PasskeyPayloadMapper.creationRequestJson(options.requestJson)
            credentialProvider.create(
                requestJson = requestJson,
                preferImmediatelyAvailableCredentials = options.preferImmediatelyAvailableCredentials,
                isConditionalCreateRequest = options.isConditionalCreateRequest,
            )
        }.fold(
            onSuccess = { PasskeyResult.Success(PasskeyPayloadMapper.creationResponse(it)) },
            onFailure = { PasskeyResult.Failure(it.toPasskeyException()) },
        )

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        runCatching {
            val requestJson = PasskeyPayloadMapper.authenticationRequestJson(options.requestJson)
            credentialProvider.authenticate(requestJson)
        }.fold(
            onSuccess = { PasskeyResult.Success(PasskeyPayloadMapper.authenticationResponse(it)) },
            onFailure = { PasskeyResult.Failure(it.toPasskeyException()) },
        )

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

    suspend fun authenticate(requestJson: String): String
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
        val response = credentialManager.createCredential(
            context = activity,
            request = CreatePublicKeyCredentialRequest(
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

    override suspend fun authenticate(requestJson: String): String {
        val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson)))
        val response = credentialManager.getCredential(context = activity, request = request)
        return (response.credential as? PublicKeyCredential)?.authenticationResponseJson
            ?: throw PasskeyException.Unexpected(
                IllegalStateException("Credential Manager returned ${response.credential::class.qualifiedName}"),
            )
    }
}
