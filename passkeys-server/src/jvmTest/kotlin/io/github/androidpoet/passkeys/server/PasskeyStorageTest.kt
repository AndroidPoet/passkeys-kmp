package io.github.androidpoet.passkeys.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun credentialStore_updateSignatureCount() {
        val store = InMemoryPasskeyCredentialStore()
        store.save(passkey("cred1", count = 1))
        store.updateSignatureCount("cred1", 9)

        assertEquals(9, store.find("cred1", "h1")?.signatureCount)
        // No-op for unknown credentials.
        store.updateSignatureCount("missing", 5)
        assertTrue(store.findByCredentialId("missing").isEmpty())
    }

    @Test
    fun challengeStore_isSingleUse() {
        val store = InMemoryPasskeyChallengeStore()
        store.put("c1", "payload")

        assertEquals("payload", store.take("c1"))
        assertNull(store.take("c1"))
        assertNull(store.take("never-stored"))
    }
}
