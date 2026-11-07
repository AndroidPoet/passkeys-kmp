package io.github.androidpoet.passkeys.server

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PasskeyRoundTripTest {
    private val origin = "http://localhost:8080"
    private val credentials = InMemoryPasskeyCredentialStore()
    private var now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId) = this

            override fun instant() = now
        }

    private fun relyingParty(
        userVerification: PasskeyUserVerification = PasskeyUserVerification.PREFERRED,
        allowOriginPort: Boolean = false,
        credentialStore: PasskeyCredentialStore = credentials,
    ) = PasskeyRelyingParty(
        config =
            PasskeyServerConfig(
                rpId = "localhost",
                rpName = "test",
                origins = setOf(origin),
                userVerification = userVerification,
                challengeTimeout = 5.minutes,
                allowOriginPort = allowOriginPort,
            ),
        credentials = credentialStore,
        challenges = InMemoryPasskeyChallengeStore(clock = clock),
        clock = clock,
    )

    // base64url("user-123")
    private val userHandle = "dXNlci0xMjM"
    private val alice = PasskeyUser(userHandle, "alice", "Alice")

    private fun PasskeyRelyingParty.register(authenticator: SoftwareAuthenticator, user: PasskeyUser = alice): StoredPasskey {
        val registration = beginRegistration(user)
        return finishRegistration(registration.ceremonyId, authenticator.register(registration.optionsJson))
    }

    private fun PasskeyRelyingParty.authenticate(
        authenticator: SoftwareAuthenticator,
        username: String? = "alice",
        userVerification: PasskeyUserVerification? = null,
    ): PasskeyAuthenticationOutcome {
        val authentication =
            if (userVerification == null) beginAuthentication(username) else beginAuthentication(username, userVerification)
        return finishAuthentication(authentication.ceremonyId, authenticator.authenticate(authentication.optionsJson, userHandle))
    }

    @Test
    fun register_then_authenticate_endToEnd() {
        val rp = relyingParty()
        val authenticator = SoftwareAuthenticator(origin)

        val stored = rp.register(authenticator)

        assertEquals("alice", stored.username)
        assertEquals(userHandle, stored.userHandle)
        assertEquals(1, credentials.findByUsername("alice").size)

        val outcome = rp.authenticate(authenticator)

        assertEquals("alice", outcome.username)
        assertEquals(userHandle, outcome.userHandle)
        assertEquals(stored.credentialId, outcome.credentialId)
        assertTrue(outcome.userVerified)
        // Counter advanced from the stored 0 to the authenticator's reported 1.
        assertEquals(1, outcome.signatureCount)
        assertEquals(1, credentials.find(stored.credentialId, userHandle)?.signatureCount)
    }

    @Test
    fun register_persistsBackupFlags_andAuthenticationReportsThem() {
        val rp = relyingParty()
        val authenticator = SoftwareAuthenticator(origin, backupEligible = true, backedUp = true)

        val stored = rp.register(authenticator)
        val outcome = rp.authenticate(authenticator)

        assertTrue(stored.backupEligible)
        assertTrue(stored.backedUp)
        assertTrue(outcome.backupEligible)
        assertTrue(outcome.backedUp)
    }

    @Test
    fun authentication_withTamperedSignature_isRejected() {
        val rp = relyingParty()
        val authenticator = SoftwareAuthenticator(origin)
        rp.register(authenticator)

        val authentication = rp.beginAuthentication("alice")
        val response = authenticator.authenticate(authentication.optionsJson, userHandle)
        // Flip the signature's base64url to corrupt it.
        val tampered = response.replace("\"signature\":\"", "\"signature\":\"AA")

        assertFailsWith<PasskeyVerificationException> {
            rp.finishAuthentication(authentication.ceremonyId, tampered)
        }
    }

    @Test
    fun ceremony_fromOriginOnAnotherPort_isRejectedUnlessPortsAreRelaxed() {
        val otherPort = SoftwareAuthenticator("http://localhost:9999")

        assertFailsWith<PasskeyVerificationException> { relyingParty().register(otherPort) }

        val relaxed = relyingParty(allowOriginPort = true, credentialStore = InMemoryPasskeyCredentialStore())
        val registered = relaxed.register(otherPort)
        assertEquals("alice", registered.username)
    }

    @Test
    fun authentication_requiredUserVerification_rejectsResponsesWithoutUvFlag() {
        val rp = relyingParty(userVerification = PasskeyUserVerification.REQUIRED)
        val noUv = SoftwareAuthenticator(origin, userVerified = false)
        // Registration under REQUIRED also needs UV.
        assertFailsWith<PasskeyVerificationException> { rp.register(noUv) }

        val withUv = SoftwareAuthenticator(origin)
        rp.register(withUv)
        assertTrue(rp.authenticate(withUv).userVerified)
    }

    @Test
    fun authentication_preferredUserVerification_acceptsAndReportsMissingUv() {
        val rp = relyingParty()
        val noUv = SoftwareAuthenticator(origin, userVerified = false)
        rp.register(noUv)

        val outcome = rp.authenticate(noUv)

        assertFalse(outcome.userVerified)
        // A per-ceremony step-up can still demand it.
        assertFailsWith<PasskeyVerificationException> {
            rp.authenticate(noUv, userVerification = PasskeyUserVerification.REQUIRED)
        }
    }

    @Test
    fun authentication_withRegressedSignatureCounter_isRejected() {
        val rp = relyingParty()
        val authenticator = SoftwareAuthenticator(origin)
        rp.register(authenticator)

        authenticator.signCount = 5
        assertEquals(5, rp.authenticate(authenticator).signatureCount)

        authenticator.signCount = 3
        assertFailsWith<PasskeyVerificationException> { rp.authenticate(authenticator) }
        assertEquals(5, credentials.findByUsername("alice").single().signatureCount)
    }

    @Test
    fun authentication_whenCounterMovedConcurrently_isRejected() {
        // A store whose conditional update always loses the race.
        val racingStore =
            object : PasskeyCredentialStore by credentials {
                override fun recordAssertion(
                    credentialId: String,
                    expectedSignatureCount: Long,
                    signatureCount: Long,
                    backedUp: Boolean,
                ): Boolean = false
            }
        val rp = relyingParty(credentialStore = racingStore)
        val authenticator = SoftwareAuthenticator(origin)
        rp.register(authenticator)

        val failure = assertFailsWith<PasskeyVerificationException> { rp.authenticate(authenticator) }
        assertTrue(failure.message!!.contains("concurrently"), failure.message)
    }

    @Test
    fun finish_afterChallengeTimeout_isRejected() {
        val rp = relyingParty()
        val authenticator = SoftwareAuthenticator(origin)
        val registration = rp.beginRegistration(alice)
        val response = authenticator.register(registration.optionsJson)

        now = now.plusSeconds(6 * 60)

        val failure = assertFailsWith<PasskeyVerificationException> { rp.finishRegistration(registration.ceremonyId, response) }
        assertTrue(failure.message!!.contains("expired"), failure.message)
    }

    @Test
    fun finish_withCeremonyOfTheOtherKind_isRejected() {
        val rp = relyingParty()
        val registration = rp.beginRegistration(alice)

        val failure = assertFailsWith<PasskeyVerificationException> { rp.finishAuthentication(registration.ceremonyId, "{}") }
        assertTrue(failure.message!!.contains("not a authentication ceremony"), failure.message)
        // The misused ceremony is consumed either way.
        assertFailsWith<PasskeyVerificationException> { rp.finishRegistration(registration.ceremonyId, "{}") }
    }

    @Test
    fun registration_ofSameCredentialTwice_isRejected() {
        val rp = relyingParty()
        val authenticator = SoftwareAuthenticator(origin)
        rp.register(authenticator)

        assertFailsWith<PasskeyVerificationException> { rp.register(authenticator) }
        assertEquals(1, credentials.findByUsername("alice").size)
    }

    @Test
    fun registration_bindingUsernameToSecondHandle_isRejected() {
        val rp = relyingParty()
        rp.register(SoftwareAuthenticator(origin))

        // base64url("user-456")
        val otherHandle = "dXNlci00NTY"
        assertFailsWith<IllegalArgumentException> { rp.beginRegistration(PasskeyUser(otherHandle, "alice", "Alice")) }
        assertFailsWith<IllegalArgumentException> { rp.beginRegistration(PasskeyUser(userHandle, "mallory", "Mallory")) }
        // Same identity may enroll another authenticator.
        rp.register(SoftwareAuthenticator(origin))
        assertEquals(2, credentials.findByUsername("alice").size)
    }

    @Test
    fun response_withoutClientExtensionResults_isRejectedByTheParser() {
        // Documents why every client serializer must emit `clientExtensionResults`,
        // even when empty: the W3C JSON shape requires the member.
        val rp = relyingParty()
        val authenticator = SoftwareAuthenticator(origin, includeExtensionResults = false)

        assertFailsWith<PasskeyVerificationException> { rp.register(authenticator) }
    }
}
