package io.github.androidpoet.passkeys.server

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasskeyRelyingPartyTest {
    private val challenges = InMemoryPasskeyChallengeStore()

    private val rp =
        PasskeyRelyingParty(
            config =
                PasskeyServerConfig(
                    rpId = "localhost",
                    rpName = "test",
                    origins = setOf("http://localhost:8080"),
                ),
            credentials = InMemoryPasskeyCredentialStore(),
            challenges = challenges,
        )

    // base64url("user-123")
    private val handle = "dXNlci0xMjM"

    @Test
    fun beginRegistration_mintsOptionsAndPersistsChallenge() {
        val ceremony = rp.beginRegistration(PasskeyUser(handle, "alice", "Alice"))

        assertTrue(ceremony.ceremonyId.isNotBlank())
        assertTrue(ceremony.optionsJson.contains("\"publicKey\""))
        assertTrue(ceremony.optionsJson.contains("\"challenge\""))
        // The pending request was stored under the ceremony id.
        assertNotNull(challenges.take(ceremony.ceremonyId))
    }

    @Test
    fun beginAuthentication_usernameless_mintsRequestOptions() {
        val ceremony = rp.beginAuthentication(null)

        assertTrue(ceremony.ceremonyId.isNotBlank())
        assertTrue(ceremony.optionsJson.contains("\"challenge\""))
        assertNotNull(challenges.take(ceremony.ceremonyId))
    }

    @Test
    fun finishRegistration_unknownCeremony_throwsVerification() {
        assertFailsWith<PasskeyVerificationException> {
            rp.finishRegistration("does-not-exist", """{"id":"x"}""")
        }
    }

    @Test
    fun finishRegistration_malformedResponse_throwsVerification() {
        val ceremony = rp.beginRegistration(PasskeyUser(handle, "alice", "Alice"))
        assertFailsWith<PasskeyVerificationException> {
            rp.finishRegistration(ceremony.ceremonyId, "not-json")
        }
    }

    @Test
    fun finishAuthentication_unknownCeremony_throwsVerification() {
        assertFailsWith<PasskeyVerificationException> {
            rp.finishAuthentication("does-not-exist", """{"id":"x"}""")
        }
    }

    @Test
    fun config_rejectsBlankAndEmpty() {
        assertFailsWith<IllegalArgumentException> {
            PasskeyServerConfig(rpId = "", rpName = "x", origins = setOf("http://localhost"))
        }
        assertFailsWith<IllegalArgumentException> {
            PasskeyServerConfig(rpId = "localhost", rpName = "x", origins = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            PasskeyUser(handle = "", name = "alice", displayName = "Alice")
        }
    }
}
