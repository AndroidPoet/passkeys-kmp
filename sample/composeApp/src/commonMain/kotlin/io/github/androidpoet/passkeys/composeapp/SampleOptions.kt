@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package io.github.androidpoet.passkeys.composeapp

import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * The relying party every platform's ceremony runs against. Injected at build
 * time from the `passkeysSampleRpId` Gradle property (default `example.com`);
 * publish your AASA / assetlinks under that domain's `/.well-known/`.
 */
val RP_ID: String = SampleConfig.RP_ID

private const val CHALLENGE_BYTES = 32

private fun randomChallenge(): String =
    Base64.UrlSafe.encode(Random.nextBytes(CHALLENGE_BYTES)).trimEnd('=')

/** Self-contained WebAuthn registration options (no backend needed for the demo). */
fun registrationOptions(rpId: String = RP_ID): String =
    """
    {
      "challenge": "${randomChallenge()}",
      "rp": { "id": "$rpId", "name": "Passkeys KMP" },
      "user": { "id": "cGFzc2tleXMta21wLXVzZXI", "name": "demo@$rpId", "displayName": "Passkeys KMP Demo" },
      "pubKeyCredParams": [
        { "type": "public-key", "alg": -7 },
        { "type": "public-key", "alg": -257 }
      ],
      "authenticatorSelection": {
        "residentKey": "required",
        "requireResidentKey": true,
        "userVerification": "required"
      },
      "attestation": "none",
      "timeout": 60000
    }
    """.trimIndent()

/** Self-contained WebAuthn authentication options (discoverable credential). */
fun authenticationOptions(rpId: String = RP_ID): String =
    """
    {
      "challenge": "${randomChallenge()}",
      "rpId": "$rpId",
      "allowCredentials": [],
      "userVerification": "required",
      "timeout": 60000
    }
    """.trimIndent()
