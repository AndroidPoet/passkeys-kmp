package io.github.androidpoet.passkeys.server

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasskeyRoutesTest {
    private fun relyingParty() =
        PasskeyRelyingParty(
            config =
                PasskeyServerConfig(
                    rpId = "localhost",
                    rpName = "test",
                    origins = setOf("http://localhost:8080"),
                ),
            credentials = InMemoryPasskeyCredentialStore(),
            challenges = InMemoryPasskeyChallengeStore(),
        )

    @Test
    fun registerBegin_returnsCeremonyIdAndOptions() =
        testApplication {
            application { routing { passkeyRoutes(relyingParty()) } }

            val response =
                client.post("/passkeys/register/begin") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"handle":"dXNlci0xMjM","name":"alice","displayName":"Alice"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"ceremonyId\""), body)
            assertTrue(body.contains("\"publicKey\""), body)
            assertTrue(body.contains("\"challenge\""), body)
        }

    @Test
    fun loginBegin_usernameless_returnsOptions() =
        testApplication {
            application { routing { passkeyRoutes(relyingParty()) } }

            val response =
                client.post("/passkeys/login/begin") {
                    contentType(ContentType.Application.Json)
                    setBody("""{}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("\"ceremonyId\""))
        }

    @Test
    fun registerFinish_unknownCeremony_returns400() =
        testApplication {
            application { routing { passkeyRoutes(relyingParty()) } }

            val response =
                client.post("/passkeys/register/finish") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"ceremonyId":"nope","response":{"id":"x"}}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("\"error\""))
        }

    @Test
    fun registerBegin_missingField_returns400() =
        testApplication {
            application { routing { passkeyRoutes(relyingParty()) } }

            val response =
                client.post("/passkeys/register/begin") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"alice"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}
