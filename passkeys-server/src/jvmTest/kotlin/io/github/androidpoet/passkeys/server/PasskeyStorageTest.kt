package io.github.androidpoet.passkeys.server

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PasskeyStorageTest {
    private fun passkey(id: String, handle: String = "h1", user: String = "alice", count: Long = 0) =
        StoredPasskey(
            credentialId = id,
            userHandle = handle,
            username = user,
            publicKeyCose = "cose-$id",
            signatureCount = count,
        )

    @Test
    fun credentialStore_saveLookupAndResolve() {
        val store = InMemoryPasskeyCredentialStore()
        store.save(passkey("cred1"))

        assertEquals(1, store.findByUsername("alice").size)
        assertTrue(store.findByUsername("bob").isEmpty())
        assertEquals("cred1", store.findByCredentialId("cred1").single().credentialId)
        assertEquals("alice", store.find("cred1", "h1")?.username)
        assertNull(store.find("cred1", "wrong-handle"))
        assertEquals("h1", store.userHandleForUsername("alice"))
        assertEquals("alice", store.usernameForUserHandle("h1"))
        assertNull(store.userHandleForUsername("nobody"))
    }

    @Test
    fun credentialStore_saveIsInsertOnly() {
        val store = InMemoryPasskeyCredentialStore()
        store.save(passkey("cred1"))

        assertFailsWith<IllegalStateException> { store.save(passkey("cred1", user = "mallory")) }
        assertEquals("alice", store.findByCredentialId("cred1").single().username)
    }

    @Test
    fun credentialStore_recordAssertionIsCompareAndSet() {
        val store = InMemoryPasskeyCredentialStore()
        store.save(passkey("cred1", count = 1))

        assertTrue(store.recordAssertion("cred1", expectedSignatureCount = 1, signatureCount = 9, backedUp = true))
        val updated = store.find("cred1", "h1")!!
        assertEquals(9, updated.signatureCount)
        assertTrue(updated.backedUp)

        // Stale expectation (a concurrent assertion already moved the counter) is refused.
        assertFalse(store.recordAssertion("cred1", expectedSignatureCount = 1, signatureCount = 10, backedUp = false))
        assertEquals(9, store.find("cred1", "h1")?.signatureCount)
        // Unknown credentials are refused too.
        assertFalse(store.recordAssertion("missing", expectedSignatureCount = 0, signatureCount = 5, backedUp = false))
    }

    @Test
    fun challengeStore_isSingleUse() {
        val store = InMemoryPasskeyChallengeStore()
        store.put("c1", "payload")

        assertEquals("payload", store.take("c1"))
        assertNull(store.take("c1"))
        assertNull(store.take("never-stored"))
    }

    @Test
    fun challengeStore_expiresEntriesAfterTtl() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val clock =
            object : Clock() {
                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: java.time.ZoneId) = this

                override fun instant() = now
            }
        val store = InMemoryPasskeyChallengeStore(ttl = 2.minutes, clock = clock)
        store.put("c1", "payload")

        now = now.plusSeconds(3 * 60)

        assertNull(store.take("c1"))
    }

    @Test
    fun challengeStore_evictsOldestBeyondMaxPending() {
        val store = InMemoryPasskeyChallengeStore(maxPending = 2)
        store.put("c1", "p1")
        store.put("c2", "p2")
        store.put("c3", "p3")

        assertNull(store.take("c1"))
        assertEquals("p2", store.take("c2"))
        assertEquals("p3", store.take("c3"))
    }
}
