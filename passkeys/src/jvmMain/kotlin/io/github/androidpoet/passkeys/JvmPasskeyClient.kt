package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.ApplePasskeyNativeBridge
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PasskeyClient] for JVM desktop (Compose Desktop).
 *
 * On **macOS** this drives the real platform authenticator (Touch ID / saved
 * passkeys) through a bundled native backend — `libPasskeysNative.dylib`, a
 * Swift + JNI shim over `AuthenticationServices` (see `jvmMain/native/macos`).
 * The ceremony presents on the app's window; pass a Compose `windowHandle`
 * (`ComposeWindow.windowHandle`) via [windowHandle] so the sheet anchors
 * correctly, otherwise the native side falls back to the key window.
 *
 * On **Windows / Linux**, or on macOS when the native backend could not load,
 * both ceremonies return [PasskeyException.Unsupported] — use [PasskeyBrowserHandoff]
 * to complete the ceremony in the system browser there.
 *
 * > macOS note: a passkey ceremony requires the host process to be a signed
 * > `.app` bundle carrying the `associated-domains` (`webcredentials:<rpId>`)
 * > entitlement. A bare `java` process from the terminal will be rejected by the
 * > system; package + sign the Compose Desktop app to test.
 */
public class JvmPasskeyClient(
    private val windowHandle: () -> Long = { 0L },
) : PasskeyClient {
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> {
        if (!nativeSupported) return unsupported()
        return withContext(Dispatchers.IO) {
            runCatching {
                val raw =
                    ApplePasskeyNativeBridge.nCreate(options.requestJson, windowHandle())
                        ?: error("native passkey backend returned no result")
                nativeError(raw)?.let { return@runCatching PasskeyResult.Failure(it) }
                PasskeyResult.Success(PasskeyPayloadMapper.creationResponse(raw))
            }.getOrElse { PasskeyResult.Failure(PasskeyPayloadMapper.exception(it)) }
        }
    }

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> {
        if (!nativeSupported) return unsupported()
        return withContext(Dispatchers.IO) {
            runCatching {
                val raw =
                    ApplePasskeyNativeBridge.nAuthenticate(options.requestJson, windowHandle())
                        ?: error("native passkey backend returned no result")
                nativeError(raw)?.let { return@runCatching PasskeyResult.Failure(it) }
                PasskeyResult.Success(PasskeyPayloadMapper.authenticationResponse(raw))
            }.getOrElse { PasskeyResult.Failure(PasskeyPayloadMapper.exception(it)) }
        }
    }

    private val nativeSupported: Boolean
        get() = ApplePasskeyNativeBridge.available

    /** Detects the native backend's `{"__error__":{...}}` sentinel and maps it. */
    private fun nativeError(raw: String): PasskeyException? {
        if (!raw.contains("\"__error__\"")) return null
        val message = ERROR_MESSAGE.find(raw)?.groupValues?.get(1) ?: "passkey ceremony failed"
        val code =
            ERROR_CODE
                .find(raw)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        val detail = if (code.isBlank()) message else "$message ($code)"
        return PasskeyException.Unexpected(IllegalStateException(detail))
    }

    private fun <T> unsupported(): PasskeyResult<T> =
        PasskeyResult.Failure(
            PasskeyException.Unsupported(
                IllegalStateException(
                    "No in-process passkey authenticator on this desktop OS. " +
                        "Use browser handoff (PasskeyBrowserHandoff) to complete the ceremony in the system browser.",
                ),
            ),
        )

    private companion object {
        private val ERROR_MESSAGE = """"message"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        private val ERROR_CODE = """"code"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
    }
}
