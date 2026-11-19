package io.github.androidpoet.passkeys

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.SignalAllAcceptedCredentialIdsRequest
import androidx.credentials.SignalCredentialStateRequest
import androidx.credentials.SignalCurrentUserDetailsRequest
import androidx.credentials.SignalUnknownCredentialRequest
import androidx.credentials.exceptions.publickeycredential.SignalCredentialSecurityException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialStateProviderConfigurationException
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.PasskeySignalPayloads
import io.github.androidpoet.passkeys.internal.passkeyResult

private const val SIGNAL_SECURITY_MESSAGE =
    "The app is not allowed to signal for this rpId. Check your Digital Asset Links at /.well-known/assetlinks.json."

/**
 * [PasskeySignals] for Android, backed by Credential Manager's signal API.
 *
 * Signals reach the provider on Android 15+; on a device or provider without
 * support the call fails with [PasskeyException.Unsupported], which is safe to
 * ignore.
 *
 * @param context any context; no UI is shown.
 */
public class AndroidPasskeySignals(
    context: Context,
) : PasskeySignals {
    private val credentialManager = CredentialManager.create(context)

    override suspend fun unknownCredential(
        rpId: String,
        credentialId: String,
    ): PasskeyResult<Unit> = signal { SignalUnknownCredentialRequest(PasskeySignalPayloads.unknownCredential(rpId, credentialId)) }

    override suspend fun allAcceptedCredentials(
        rpId: String,
        userId: String,
        acceptedCredentialIds: List<String>,
    ): PasskeyResult<Unit> =
        signal {
            SignalAllAcceptedCredentialIdsRequest(
                PasskeySignalPayloads.allAcceptedCredentials(rpId, userId, acceptedCredentialIds),
            )
        }

    override suspend fun currentUserDetails(
        rpId: String,
        userId: String,
        name: String,
        displayName: String,
    ): PasskeyResult<Unit> =
        signal {
            SignalCurrentUserDetailsRequest(PasskeySignalPayloads.currentUserDetails(rpId, userId, name, displayName))
        }

    // The request is built inside the boundary: its constructor validates the
    // ids and throws on a malformed one, which must be a Failure, not a crash.
    private suspend fun signal(request: () -> SignalCredentialStateRequest): PasskeyResult<Unit> =
        passkeyResult(mapError = { it.toSignalPasskeyException() }) {
            credentialManager.signalCredentialState(request())
        }
}

internal fun Throwable.toSignalPasskeyException(): PasskeyException =
    when (this) {
        is SignalCredentialStateProviderConfigurationException -> PasskeyException.Unsupported(this)
        is SignalCredentialSecurityException -> PasskeyException.DomError(SIGNAL_SECURITY_MESSAGE, this)
        else -> PasskeyPayloadMapper.exception(this)
    }
