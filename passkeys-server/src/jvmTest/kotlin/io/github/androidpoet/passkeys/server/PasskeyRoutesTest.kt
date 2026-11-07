package io.github.androidpoet.passkeys.server

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasskeyRoutesTest {
    private val credentials = InMemoryPasskeyCredentialStore()

    private fun relyingParty() =
        PasskeyRelyingParty(
            config =
                PasskeyServerConfig(
                    rpId = "localhost",
                    rpName = "test",
                    origins = setOf("http://localhost:8080"),
                ),
            credentials = credentials,
            challenges = InMemoryPasskeyChallengeStore(),
        )

    private fun ApplicationTestBuilder.mountRoutes(enrollment: PasskeyEnrollmentPolicy = newAccountsOnlyEnrollment(credentials)) {
        application { routing { passkeyRoutes(relyingParty(), enrollment) } }
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun registerBegin_returnsCeremonyIdAndOptions() =
        testApplication {
            mountRoutes()

            val response = postJson("/passkeys/register/begin", """{"name":"alice","displayName":"Alice"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"ceremonyId\""), body)
            assertTrue(body.contains("\"publicKey\""), body)
            assertTrue(body.contains("\"challenge\""), body)
        }

    @Test
    fun registerBegin_ignoresClientSuppliedHandle_andUsesThePolicy() =
        testApplication {
            // base64url("server-chosen")
            val serverHandle = "c2VydmVyLWNob3Nlbg"
            mountRoutes(enrollment = PasskeyEnrollmentPolicy { _, request -> PasskeyUser(serverHandle, request.name, "Who Cares") })

            val response = postJson("/passkeys/register/begin", """{"handle":"YXR0YWNrZXI","name":"alice"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"id\":\"$serverHandle\""), body)
            assertFalse(body.contains("YXR0YWNrZXI"), body)
        }

    @Test
    fun registerBegin_whenEnrollmentRefused_returns403() =
        testApplication {
            mountRoutes(enrollment = PasskeyEnrollmentPolicy { _, _ -> null })

            val response = postJson("/passkeys/register/begin", """{"name":"alice"}""")

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("\"error\""))
        }

    @Test
    fun registerBegin_newAccountsOnly_refusesExistingNames() =
        testApplication {
            credentials.save(
                StoredPasskey(credentialId = "cred1", userHandle = "aGFuZGxl", username = "alice", publicKeyCose = "k", signatureCount = 0),
            )
            mountRoutes()

            assertEquals(HttpStatusCode.Forbidden, postJson("/passkeys/register/begin", """{"name":"alice"}""").status)
            assertEquals(HttpStatusCode.OK, postJson("/passkeys/register/begin", """{"name":"bob"}""").status)
        }

    @Test
    fun loginBegin_usernameless_returnsOptions() =
        testApplication {
            mountRoutes()

            val response = postJson("/passkeys/login/begin", """{}""")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("\"ceremonyId\""))
        }

    @Test
    fun loginBegin_nonStringUsername_returns400InsteadOfUsernamelessLogin() =
        testApplication {
            mountRoutes()

            val response = postJson("/passkeys/login/begin", """{"username":42}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun registerFinish_unknownCeremony_returns400() =
        testApplication {
            mountRoutes()

            val response = postJson("/passkeys/register/finish", """{"ceremonyId":"nope","response":{"id":"x"}}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("\"error\""))
        }

    @Test
    fun registerBegin_missingField_returns400() =
        testApplication {
            mountRoutes()

            val response = postJson("/passkeys/register/begin", """{"displayName":"Alice"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun anyRoute_oversizedBody_returns413() =
        testApplication {
            mountRoutes()
            val padding = "x".repeat(70 * 1024)

            val response = postJson("/passkeys/login/finish", """{"ceremonyId":"$padding","response":{}}""")

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }
}
