package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse

/**
 * [PasskeyClient] for plain JVM desktop.
 *
 * There is **no in-process way** to drive a platform authenticator from a JVM
 * desktop app without per-OS native bindings (JNA to `webauthn.dll` on Windows,
 * a JNI/Objective-C shim with a window anchor on macOS, libfido2 for Linux
 * security keys) — and macOS cannot present its passkey sheet headlessly at all.
 * Rather than pretend, this client **fails loud**: both ceremonies return
 * [PasskeyException.Unsupported].
 *
 * The supported desktop pattern is **browser handoff** — open your relying
 * party's web page in the system browser and let the real platform
 * authenticator complete the ceremony there. See [PasskeyBrowserHandoff].
 */
public class JvmPasskeyClient : PasskeyClient {
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> = unsupported()

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> = unsupported()

    private fun <T> unsupported(): PasskeyResult<T> =
        PasskeyResult.Failure(
            PasskeyException.Unsupported(
                IllegalStateException(
                    "Passkeys have no in-process authenticator on JVM desktop. " +
                        "Use browser handoff (PasskeyBrowserHandoff) to complete the ceremony in the system browser.",
                ),
            ),
        )
}
