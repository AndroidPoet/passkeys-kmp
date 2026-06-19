package io.github.androidpoet.passkeys.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PasskeyRoundTripTest {
    private val origin = "http://localhost:8080"
    private val credentials = InMemoryPasskeyCredentialStore()

    private val relyingParty =
        PasskeyRelyingParty(
            config = PasskeyServerConfig(rpId = "localhost", rpName = "test", origins = setOf(origin)),
            credentials = credentials,
            challenges = InMemoryPasskeyChallengeStore(),
        )

    // base64url("user-123")
    private val userHandle = "dXNlci0xMjM"

    @Test
    fun register_then_authenticate_endToEnd() {
        val authenticator = SoftwareAuthenticator(origin)

        // Register: the RP verifies the (none-attestation) registration response.
        val registration = relyingParty.beginRegistration(PasskeyUser(userHandle, "alice", "Alice"))
        val stored = relyingParty.finishRegistration(registration.ceremonyId, authenticator.register(registration.optionsJson))

        assertEquals("alice", stored.username)
        assertEquals(userHandle, stored.userHandle)
        assertEquals(1, credentials.findByUsername("alice").size)

        // Authenticate: the RP verifies the assertion signature against the stored key.
        val authentication = relyingParty.beginAuthentication("alice")
        val outcome =
            relyingParty.finishAuthentication(
                authentication.ceremonyId,
                authenticator.authenticate(authentication.optionsJson, userHandle),
            )

        assertEquals("alice", outcome.username)
        assertEquals(userHandle, outcome.userHandle)
        assertEquals(stored.credentialId, outcome.credentialId)
        // Counter advanced from the stored 0 to the authenticator's reported 1.
        assertEquals(1, outcome.signatureCount)
        assertEquals(1, credentials.find(stored.credentialId, userHandle)?.signatureCount)
    }

    @Test
    fun authentication_withTamperedSignature_isRejected() {
        val authenticator = SoftwareAuthenticator(origin)
        val registration = relyingParty.beginRegistration(PasskeyUser(userHandle, "alice", "Alice"))
        relyingParty.finishRegistration(registration.ceremonyId, authenticator.register(registration.optionsJson))

        val authentication = relyingParty.beginAuthentication("alice")
        val response = authenticator.authenticate(authentication.optionsJson, userHandle)
        // Flip the signature's base64url to corrupt it.
        val tampered = response.replace("\"signature\":\"", "\"signature\":\"AA")

        assertFailsWith<PasskeyVerificationException> {
            relyingParty.finishAuthentication(authentication.ceremonyId, tampered)
        }
    }
}
