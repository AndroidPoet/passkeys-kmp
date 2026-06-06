package io.github.androidpoet.passkeys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse

class AndroidPasskeyClientTest {
    private val creationResponseJson = """
        {
          "id": "credential-id",
          "rawId": "credential-id",
          "type": "public-key",
          "authenticatorAttachment": "platform",
          "response": {
            "attestationObject": "attestation",
            "clientDataJSON": "client-data",
            "transports": ["internal"]
          }
        }
    """.trimIndent()

    private val authenticationResponseJson = """
        {
          "id": "credential-id",
          "rawId": "credential-id",
          "type": "public-key",
          "response": {
            "clientDataJSON": "client-data",
            "authenticatorData": "auth-data",
            "signature": "signature",
            "userHandle": "user"
          }
        }
    """.trimIndent()

    @Test
    fun test_create_whenProviderSucceeds_returnsParsedCreationResponse() = runTest {
        val provider = FakeAndroidCredentialProvider(createResponse = creationResponseJson)
        val client = AndroidPasskeyClient(provider)
        val requestJson = """
            {
              "publicKey": {
                "challenge": "aGVsbG8=",
                "rp": { "id": "example.com", "name": "Example" },
                "user": { "id": "dXNlci0x", "name": "user@example.com", "displayName": "User" },
                "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }]
              }
            }
        """.trimIndent()

        val result = client.create(requestJson)

        val success = assertIs<PasskeyResult.Success<PasskeyCreationResponse>>(result)
        assertEquals("credential-id", success.value.id)
        val forwarded = Json.parseToJsonElement(provider.receivedCreateRequestJson).jsonObject
        assertEquals("aGVsbG8", forwarded["challenge"]?.jsonPrimitive?.content)
        assertEquals("dXNlci0x", forwarded["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
    }

    @Test
    fun test_create_whenOptionsSet_forwardsAndroidCredentialFlags() = runTest {
        val provider = FakeAndroidCredentialProvider(createResponse = creationResponseJson)
        val client = AndroidPasskeyClient(provider)
        val requestJson = """
            {
              "challenge": "aGVsbG8",
              "rp": { "id": "example.com", "name": "Example" },
              "user": { "id": "dXNlci0x", "name": "user@example.com" },
              "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }]
            }
        """.trimIndent()

        client.create(
            PasskeyCreationOptions(
                requestJson = requestJson,
                preferImmediatelyAvailableCredentials = true,
                isConditionalCreateRequest = true,
            ),
        )

        assertEquals(true, provider.receivedPreferImmediatelyAvailableCredentials)
        assertEquals(true, provider.receivedIsConditionalCreateRequest)
    }

    @Test
    fun test_authenticate_whenProviderSucceeds_returnsParsedAuthenticationResponse() = runTest {
        val provider = FakeAndroidCredentialProvider(authenticateResponse = authenticationResponseJson)
        val client = AndroidPasskeyClient(provider)
        val requestJson = """
            {
              "publicKey": {
                "challenge": "aGVsbG8=",
                "rpId": "example.com",
                "allowCredentials": [{ "id": "Y3JlZC0x", "type": "public-key" }],
                "userVerification": "preferred"
              }
            }
        """.trimIndent()

        val result = client.authenticate(requestJson)

        val success = assertIs<PasskeyResult.Success<PasskeyAuthenticationResponse>>(result)
        assertEquals("signature", success.value.signature)
        val forwarded = Json.parseToJsonElement(provider.receivedAuthenticateRequestJson).jsonObject
        assertEquals("aGVsbG8", forwarded["challenge"]?.jsonPrimitive?.content)
        assertEquals("example.com", forwarded["rpId"]?.jsonPrimitive?.content)
    }

    @Test
    fun test_create_whenPayloadMalformed_returnsInvalidPayloadFailure() = runTest {
        val client = AndroidPasskeyClient(FakeAndroidCredentialProvider())

        val result = client.create("{")

        val failure = assertIs<PasskeyResult.Failure>(result)
        assertIs<PasskeyException.InvalidPayload>(failure.error)
    }

    @Test
    fun test_authenticate_whenProviderFails_returnsProviderFailure() = runTest {
        val client = AndroidPasskeyClient(
            FakeAndroidCredentialProvider(authenticateError = PasskeyException.NoCredential()),
        )

        val result = client.authenticate(
            """
                {
                  "challenge": "aGVsbG8",
                  "rpId": "example.com",
                  "allowCredentials": []
                }
            """.trimIndent(),
        )

        val failure = assertIs<PasskeyResult.Failure>(result)
        assertIs<PasskeyException.NoCredential>(failure.error)
    }
}

private class FakeAndroidCredentialProvider(
    private val createResponse: String = "",
    private val authenticateResponse: String = "",
    private val createError: Throwable? = null,
    private val authenticateError: Throwable? = null,
) : AndroidCredentialProvider {
    var receivedCreateRequestJson: String = ""
        private set
    var receivedAuthenticateRequestJson: String = ""
        private set
    var receivedPreferImmediatelyAvailableCredentials: Boolean = false
        private set
    var receivedIsConditionalCreateRequest: Boolean = false
        private set

    override suspend fun create(
        requestJson: String,
        preferImmediatelyAvailableCredentials: Boolean,
        isConditionalCreateRequest: Boolean,
    ): String {
        receivedCreateRequestJson = requestJson
        receivedPreferImmediatelyAvailableCredentials = preferImmediatelyAvailableCredentials
        receivedIsConditionalCreateRequest = isConditionalCreateRequest
        createError?.let { throw it }
        return createResponse
    }

    override suspend fun authenticate(requestJson: String): String {
        receivedAuthenticateRequestJson = requestJson
        authenticateError?.let { throw it }
        return authenticateResponse
    }
}
