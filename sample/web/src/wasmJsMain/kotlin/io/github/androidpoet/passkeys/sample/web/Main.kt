@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package io.github.androidpoet.passkeys.sample.web

import io.github.androidpoet.passkeys.PasskeyResult
import io.github.androidpoet.passkeys.WasmJsPasskeyClient
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import kotlin.io.encoding.Base64
import kotlin.random.Random

private val scope = MainScope()
private val passkeys = WasmJsPasskeyClient()

/**
 * Minimal browser demo for [WasmJsPasskeyClient]. Generates self-contained
 * WebAuthn options (random challenge, `rpId` = current host) so the create /
 * authenticate ceremonies can be exercised with no backend — pair it with
 * Chrome DevTools' virtual authenticator. Verification of the returned response
 * is a server concern and intentionally out of scope here.
 */
fun main() {
    val rpId = window.location.hostname.ifBlank { "localhost" }

    val registrationField = field("registrationOptions")
    val authenticationField = field("authenticationOptions")
    val output = document.getElementById("output") as HTMLElement

    registrationField.value = registrationOptions(rpId)
    authenticationField.value = authenticationOptions(rpId)

    button("createButton").addEventListener("click") {
        run("✅ Passkey created successfully", output) { passkeys.create(registrationField.value) }
    }
    button("authenticateButton").addEventListener("click") {
        run("✅ Login successful", output) { passkeys.authenticate(authenticationField.value) }
    }
}

private fun run(successMessage: String, output: HTMLElement, block: suspend () -> PasskeyResult<*>) {
    output.textContent = "Contacting authenticator…"
    scope.launch {
        output.textContent =
            when (val result = block()) {
                is PasskeyResult.Success -> "$successMessage\n\n${result.value}"
                is PasskeyResult.Failure -> "❌ Failed (code ${result.error.code})\n\n${result.error.message}"
            }
    }
}

private fun field(id: String): HTMLTextAreaElement = document.getElementById(id) as HTMLTextAreaElement

private fun button(id: String): HTMLButtonElement = document.getElementById(id) as HTMLButtonElement

private fun randomChallenge(): String = Base64.UrlSafe.encode(Random.nextBytes(CHALLENGE_BYTES)).trimEnd('=')

private fun registrationOptions(rpId: String): String =
    """
    {
      "challenge": "${randomChallenge()}",
      "rp": { "id": "$rpId", "name": "Passkeys KMP Web Demo" },
      "user": { "id": "dXNlci0xMjM", "name": "demo@example.com", "displayName": "Demo User" },
      "pubKeyCredParams": [
        { "type": "public-key", "alg": -7 },
        { "type": "public-key", "alg": -257 }
      ],
      "authenticatorSelection": { "residentKey": "required", "userVerification": "required" },
      "attestation": "none",
      "timeout": 60000
    }
    """.trimIndent()

private fun authenticationOptions(rpId: String): String =
    """
    {
      "challenge": "${randomChallenge()}",
      "rpId": "$rpId",
      "userVerification": "required",
      "timeout": 60000
    }
    """.trimIndent()

private const val CHALLENGE_BYTES = 32
