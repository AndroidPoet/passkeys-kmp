package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.ApplePasskeyNativeBridge
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.passkeyResult
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val COSE_ES256 = -7L

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
        return passkeyResult {
            // Same normalization as every other platform: unwrap `{"publicKey":…}`
            // and canonicalize base64url before the Swift side parses it.
            val nativeOptions = PasskeyPayloadMapper.nativeCreationOptions(options.requestJson)
            requireSupported(nativeOptions.authenticatorSelection?.authenticatorAttachment != "cross-platform") {
                "only platform passkeys are supported on macOS; cross-platform (security key) requests are not"
            }
            requireSupported(nativeOptions.algorithms.isEmpty() || COSE_ES256 in nativeOptions.algorithms) {
                "macOS passkeys are ES256 only, but pubKeyCredParams does not allow alg -7"
            }
            val requestJson = PasskeyPayloadMapper.creationRequestJson(options.requestJson)
            val raw = withContext(Dispatchers.IO) { ApplePasskeyNativeBridge.create(requestJson, windowHandle()) }
            nativeError(raw)?.let { throw it }
            PasskeyPayloadMapper.creationResponse(raw)
        }
    }

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> {
        if (!nativeSupported) return unsupported()
        return passkeyResult {
            val requestJson = PasskeyPayloadMapper.authenticationRequestJson(options.requestJson)
            val raw = withContext(Dispatchers.IO) { ApplePasskeyNativeBridge.authenticate(requestJson, windowHandle()) }
            nativeError(raw)?.let { throw it }
            PasskeyPayloadMapper.authenticationResponse(raw)
        }
    }

    private val nativeSupported: Boolean
        get() = ApplePasskeyNativeBridge.available

    /**
     * Detects the native backend's `{"__error__":{"code","message"}}` sentinel
     * and maps it. Codes are either the bridge's own (`parse`, `credential`,
     * `serialize`, `unknown`) or `<NSError domain>:<code>`.
     */
    private fun nativeError(raw: String): PasskeyException? {
        val error =
            runCatching { json.parseToJsonElement(raw).jsonObject["__error__"]?.jsonObject }.getOrNull()
                ?: return null
        val code = error["code"]?.jsonPrimitive?.content.orEmpty()
        val message = error["message"]?.jsonPrimitive?.content ?: "passkey ceremony failed"
        val cause = IllegalStateException(if (code.isBlank()) message else "$message ($code)")
        return when {
            code == "parse" -> PasskeyException.InvalidPayload(cause)
            code.startsWith(AUTHORIZATION_ERROR_DOMAIN) ->
                when (code.substringAfter(':').toIntOrNull()) {
                    AS_ERROR_CANCELED -> PasskeyException.UserCanceled(cause)
                    AS_ERROR_INVALID_RESPONSE -> PasskeyException.DomError(DOM_MESSAGE, cause)
                    AS_ERROR_MATCHED_EXCLUDED_CREDENTIAL -> PasskeyException.CredentialExcluded(cause)
                    else -> PasskeyException.Unexpected(cause)
                }
            else -> PasskeyException.Unexpected(cause)
        }
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

    private inline fun requireSupported(condition: Boolean, message: () -> String) {
        if (!condition) throw PasskeyException.Unsupported(IllegalStateException(message()))
    }

    private companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val AUTHORIZATION_ERROR_DOMAIN = "com.apple.AuthenticationServices.AuthorizationError:"
        private const val AS_ERROR_CANCELED = 1001
        private const val AS_ERROR_INVALID_RESPONSE = 1002
        private const val AS_ERROR_MATCHED_EXCLUDED_CREDENTIAL = 1006
        private const val DOM_MESSAGE =
            "DOM error from WebAuthn. Check your apple-app-site-association file and the app's associated-domains entitlement."
    }
}
