package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.passkeyResult
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise

/**
 * [PasskeyClient] for the browser (Kotlin/Wasm), backed by the WebAuthn
 * `navigator.credentials` API.
 *
 * Options and responses are marshalled as JSON across the JS boundary using the
 * browser's own `PublicKeyCredential.parseCreationOptionsFromJSON` /
 * `parseRequestOptionsFromJSON` and `PublicKeyCredential.toJSON()` (Baseline
 * March 2025), so the library never has to hand-convert base64url to
 * `ArrayBuffer` and back.
 *
 * Requires a secure context (HTTPS or `localhost`) and a browser that supports
 * the WebAuthn JSON serialization methods; older browsers fail with
 * [PasskeyException.Unsupported]. Cancelling the calling coroutine aborts the
 * in-flight browser ceremony.
 */
public class WasmJsPasskeyClient : PasskeyClient {
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        passkeyResult {
            val requestJson = PasskeyPayloadMapper.creationRequestJson(options.requestJson)
            PasskeyPayloadMapper.creationResponse(runCeremony(CREATE, requestJson))
        }

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        passkeyResult {
            val requestJson = PasskeyPayloadMapper.authenticationRequestJson(options.requestJson)
            PasskeyPayloadMapper.authenticationResponse(runCeremony(GET, requestJson))
        }

    // Drives the ceremony in JS and returns the WebAuthn response JSON, or
    // throws a typed PasskeyException. The JS side never rejects: it resolves
    // an {ok,...} wrapper so DOMException names survive the boundary
    // (Kotlin/Wasm can't read them off a thrown JS value directly).
    private suspend fun runCeremony(type: String, requestJson: String): String {
        val controller = newAbortController()
        val raw =
            try {
                runCeremonyJs(requestJson, type, controller).await<JsString>().toString()
            } catch (e: CancellationException) {
                abort(controller)
                throw e
            }
        val outcome = json.decodeFromString<CeremonyOutcome>(raw)
        if (outcome.ok && outcome.json != null) return outcome.json
        throw domException(type, outcome.name, outcome.message)
    }

    private companion object {
        const val CREATE = "create"
        const val GET = "get"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class CeremonyOutcome(
    val ok: Boolean,
    val json: String? = null,
    val name: String = "Error",
    val message: String = "",
)

// Maps WebAuthn DOMException names to the shared PasskeyException hierarchy,
// matching the distinctions the Android/iOS clients already surface.
private fun domException(type: String, name: String, message: String): PasskeyException {
    val cause = IllegalStateException("$name: $message")
    return when (name) {
        "NotAllowedError" -> PasskeyException.UserCanceled(cause)
        "AbortError" -> PasskeyException.Interrupted(cause)
        "NotSupportedError" -> PasskeyException.Unsupported(cause)
        // On create, InvalidStateError means an excludeCredentials entry matched.
        "InvalidStateError" -> if (type == "create") PasskeyException.CredentialExcluded(cause) else PasskeyException.DomError(message, cause)
        "SecurityError" -> PasskeyException.DomError(message, cause)
        else -> PasskeyException.Unexpected(cause)
    }
}

private fun newAbortController(): JsAny = js("new AbortController()")

@Suppress("UnusedPrivateMember")
private fun abort(controller: JsAny): Unit = js("controller.abort()")

// Runs the WebAuthn ceremony entirely in JS and resolves a JSON wrapper string.
// Uses the browser's JSON (de)serialization so base64url <-> ArrayBuffer
// conversion is handled natively; the AbortController signal lets Kotlin
// cancellation dismiss the browser's passkey UI.
@Suppress("UnusedPrivateMember")
private fun runCeremonyJs(requestJson: String, type: String, controller: JsAny): Promise<JsString> =
    js(
        """
        (async () => {
          try {
            if (typeof PublicKeyCredential === 'undefined' ||
                !PublicKeyCredential.parseCreationOptionsFromJSON) {
              return JSON.stringify({ ok: false, name: 'NotSupportedError', message: 'WebAuthn JSON API unavailable' });
            }
            let cred;
            if (type === 'create') {
              const options = PublicKeyCredential.parseCreationOptionsFromJSON(JSON.parse(requestJson));
              cred = await navigator.credentials.create({ publicKey: options, signal: controller.signal });
            } else {
              const options = PublicKeyCredential.parseRequestOptionsFromJSON(JSON.parse(requestJson));
              cred = await navigator.credentials.get({ publicKey: options, signal: controller.signal });
            }
            return JSON.stringify({ ok: true, json: JSON.stringify(cred.toJSON()) });
          } catch (e) {
            return JSON.stringify({ ok: false, name: (e && e.name) || 'Error', message: (e && e.message) || String(e) });
          }
        })()
        """,
    )
