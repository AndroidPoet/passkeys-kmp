package io.github.androidpoet.passkeys.internal

import io.github.androidpoet.passkeys.PasskeyException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PasskeyPayloadMapperTest {
    @Test
    fun test_creationRequestJson_whenWrappedPublicKey_normalizesChallengeAndUserId() {
        val requestJson =
            """
            {
              "publicKey": {
                "challenge": "aGVsbG8=",
                "rp": { "id": "example.com", "name": "Example" },
                "user": { "id": "dXNlci0x", "name": "user@example.com", "displayName": "User" },
                "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
                "authenticatorSelection": { "userVerification": "preferred" }
              }
            }
            """.trimIndent()

        val normalized = PasskeyPayloadMapper.creationRequestJson(requestJson)

        val root = Json.parseToJsonElement(normalized).jsonObject
        assertEquals("aGVsbG8", root["challenge"]?.jsonPrimitive?.content)
        assertEquals(
            "dXNlci0x",
            root["user"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun test_authenticationRequestJson_whenWrappedPublicKey_unwrapsCredentialOptions() {
        val requestJson =
            """
            {
              "publicKey": {
                "challenge": "aGVsbG8=",
                "rpId": "example.com",
                "allowCredentials": [],
                "userVerification": "preferred"
              }
            }
            """.trimIndent()

        val normalized = PasskeyPayloadMapper.authenticationRequestJson(requestJson)

        val root = Json.parseToJsonElement(normalized).jsonObject
        assertEquals("aGVsbG8", root["challenge"]?.jsonPrimitive?.content)
        assertEquals(null, root["publicKey"])
    }

    @Test
    fun test_nativeCreationOptions_whenWrappedPublicKey_extractsIosRequiredFields() {
        val requestJson =
            """
            {
              "publicKey": {
                "challenge": "aGVsbG8=",
                "rp": { "id": "example.com", "name": "Example" },
                "user": { "id": "dXNlci0x", "name": "user@example.com", "displayName": "User" },
                "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
                "attestation": "none",
                "authenticatorSelection": { "userVerification": "required" },
                "extensions": {
                  "largeBlob": { "support": "required" },
                  "prf": { "eval": { "first": "c2FsdC0x", "second": "c2FsdC0y" } }
                }
              }
            }
            """.trimIndent()

        val options = PasskeyPayloadMapper.nativeCreationOptions(requestJson)

        assertEquals("aGVsbG8", options.challenge)
        assertEquals("example.com", options.rp.id)
        assertEquals("dXNlci0x", options.user.id)
        assertEquals("user@example.com", options.user.name)
        assertEquals("User", options.user.displayName)
        assertEquals("none", options.attestation)
        assertEquals("required", options.authenticatorSelection?.userVerification)
        assertEquals("required", options.extensions?.largeBlob?.support)
        assertEquals(
            "c2FsdC0x",
            options.extensions
                ?.prf
                ?.eval
                ?.first,
        )
        assertEquals(
            "c2FsdC0y",
            options.extensions
                ?.prf
                ?.eval
                ?.second,
        )
    }

    @Test
    fun test_nativeAuthenticationOptions_whenDirectPayload_extractsIosRequiredFields() {
        val requestJson =
            """
            {
              "challenge": "aGVsbG8=",
              "rpId": "example.com",
              "allowCredentials": [
                { "id": "Y3JlZC0x", "type": "public-key", "transports": ["internal"] }
              ],
              "userVerification": "preferred",
              "extensions": {
                "largeBlob": { "write": "YmxvYi12YWx1ZQ" },
                "prf": { "eval": { "first": "c2FsdC0x" } }
              }
            }
            """.trimIndent()

        val options = PasskeyPayloadMapper.nativeAuthenticationOptions(requestJson)

        assertEquals("aGVsbG8", options.challenge)
        assertEquals("example.com", options.rpId)
        assertEquals("Y3JlZC0x", options.allowCredentials.single().id)
        assertEquals("preferred", options.userVerification)
        assertEquals("YmxvYi12YWx1ZQ", options.extensions?.largeBlob?.write)
        assertEquals(
            "c2FsdC0x",
            options.extensions
                ?.prf
                ?.eval
                ?.first,
        )
    }

    @Test
    fun test_creationResponse_whenCredentialManagerJson_returnsFlattenedModel() {
        val responseJson =
            """
            {
              "id": "credential-id",
              "rawId": "credential-id",
              "type": "public-key",
              "authenticatorAttachment": "platform",
              "clientExtensionResults": {
                "largeBlob": { "supported": true },
                "prf": { "enabled": true }
              },
              "response": {
                "attestationObject": "attestation",
                "clientDataJSON": "client-data",
                "transports": ["internal"]
              }
            }
            """.trimIndent()

        val response = PasskeyPayloadMapper.creationResponse(responseJson)

        assertEquals("credential-id", response.id)
        assertEquals("platform", response.authenticatorAttachment)
        assertEquals("attestation", response.attestationObject)
        assertEquals(listOf("internal"), response.transports)
        val extensionResults = Json.parseToJsonElement(response.clientExtensionResultsJson.orEmpty()).jsonObject
        assertEquals(
            "true",
            extensionResults["largeBlob"]
                ?.jsonObject
                ?.get("supported")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(responseJson, response.rawJson)
    }

    @Test
    fun test_authenticationResponse_whenCredentialManagerJson_returnsFlattenedModel() {
        val responseJson =
            """
            {
              "id": "credential-id",
              "rawId": "credential-id",
              "type": "public-key",
              "clientExtensionResults": {
                "largeBlob": { "blob": "YmxvYg", "written": false },
                "prf": { "results": { "first": "cmVzdWx0LTE" } }
              },
              "response": {
                "clientDataJSON": "client-data",
                "authenticatorData": "auth-data",
                "signature": "signature",
                "userHandle": "user"
              }
            }
            """.trimIndent()

        val response = PasskeyPayloadMapper.authenticationResponse(responseJson)

        assertEquals("credential-id", response.id)
        assertEquals(null, response.authenticatorAttachment)
        assertEquals("auth-data", response.authenticatorData)
        assertEquals("signature", response.signature)
        assertEquals("user", response.userHandle)
        val extensionResults = Json.parseToJsonElement(response.clientExtensionResultsJson.orEmpty()).jsonObject
        assertEquals(
            "cmVzdWx0LTE",
            extensionResults["prf"]
                ?.jsonObject
                ?.get("results")
                ?.jsonObject
                ?.get("first")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun test_nativeCreationOptions_preservesCeremonyConstraints() {
        val requestJson =
            """
            {
              "publicKey": {
                "challenge": "aGVsbG8",
                "rp": { "id": "example.com" },
                "user": { "id": "dXNlci0x", "name": "user" },
                "pubKeyCredParams": [{ "type": "public-key", "alg": -8 }, { "type": "public-key", "alg": -257 }],
                "excludeCredentials": [{ "id": "Y3JlZC0x", "type": "public-key", "transports": ["internal"] }],
                "timeout": 120000,
                "authenticatorSelection": { "residentKey": "required", "authenticatorAttachment": "platform" }
              }
            }
            """.trimIndent()

        val options = PasskeyPayloadMapper.nativeCreationOptions(requestJson)

        assertEquals(listOf(-8L, -257L), options.algorithms)
        assertEquals("Y3JlZC0x", options.excludeCredentials.single().id)
        assertEquals(listOf("internal"), options.excludeCredentials.single().transports)
        assertEquals(120_000L, options.timeout)
        assertEquals(true, options.requiresDiscoverableCredential)
        assertEquals("platform", options.authenticatorSelection?.authenticatorAttachment)
    }

    @Test
    fun test_nativeCreationOptions_requireResidentKeyFallsBackToLegacyFlag() {
        val legacy =
            PasskeyPayloadMapper.nativeCreationOptions(
                creationJson("""{"requireResidentKey":true}"""),
            )
        val preferred =
            PasskeyPayloadMapper.nativeCreationOptions(
                creationJson("""{"residentKey":"preferred","requireResidentKey":true}"""),
            )

        assertEquals(true, legacy.requiresDiscoverableCredential)
        // An explicit residentKey wins over the legacy boolean.
        assertEquals(false, preferred.requiresDiscoverableCredential)
    }

    @Test
    fun test_authenticationResponse_withoutSignature_throwsInvalidPayload() {
        val responseJson =
            """{"id":"c","rawId":"c","type":"public-key","response":{"clientDataJSON":"cd","authenticatorData":"ad"}}"""

        assertFailsWith<PasskeyException.InvalidPayload> {
            PasskeyPayloadMapper.authenticationResponse(responseJson)
        }
    }

    @Test
    fun test_creationRequestJson_whenMalformedJson_throwsInvalidPayload() {
        assertFailsWith<PasskeyException.InvalidPayload> {
            PasskeyPayloadMapper.creationRequestJson("{")
        }
    }

    private fun creationJson(authenticatorSelection: String): String =
        """{"challenge":"aGVsbG8","rp":{"id":"e.com"},"user":{"id":"dXNlci0x","name":"u"},""" +
            """"authenticatorSelection":$authenticatorSelection}"""
}
