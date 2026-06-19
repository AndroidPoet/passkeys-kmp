package io.github.androidpoet.passkeys.server

import java.util.concurrent.ConcurrentHashMap

/**
 * A passkey credential persisted by the Relying Party. All binary fields are
 * base64url strings so the store stays transport- and database-agnostic.
 *
 * @property credentialId the base64url credential id (the primary key).
 * @property userHandle the base64url user handle the credential belongs to.
 * @property username the account name the credential belongs to.
 * @property publicKeyCose the COSE-encoded public key, base64url — used to
 *   verify future assertions.
 * @property signatureCount the last seen authenticator signature counter; a
 *   later assertion reporting a lower value signals a cloned authenticator.
 */
public class StoredPasskey(
    public val credentialId: String,
    public val userHandle: String,
    public val username: String,
    public val publicKeyCose: String,
    public val signatureCount: Long,
)

/**
 * Bring-your-own credential persistence. Back this with your real database; the
 * library never assumes a storage engine. See [InMemoryPasskeyCredentialStore]
 * for a reference (non-production) implementation.
 */
public interface PasskeyCredentialStore {
    /** Persists a newly registered credential. */
    public fun save(credential: StoredPasskey)

    /** All credentials registered to [username] (empty if the user is unknown). */
    public fun findByUsername(username: String): List<StoredPasskey>

    /** All credentials with this base64url [credentialId] (across user handles). */
    public fun findByCredentialId(credentialId: String): List<StoredPasskey>

    /** The credential matching both [credentialId] and [userHandle], if any. */
    public fun find(credentialId: String, userHandle: String): StoredPasskey?

    /** The base64url user handle for [username], if the user is known. */
    public fun userHandleForUsername(username: String): String?

    /** The account name for the base64url [userHandle], if known. */
    public fun usernameForUserHandle(userHandle: String): String?

    /** Updates the stored signature counter after a verified assertion. */
    public fun updateSignatureCount(credentialId: String, signatureCount: Long)
}

/**
 * Single-use storage for the pending ceremony state (the serialized WebAuthn
 * request) keyed by ceremony id. Back this with a short-TTL store (e.g. Redis,
 * or a signed cookie) in production; [take] must return each entry at most once.
 */
public interface PasskeyChallengeStore {
    /** Stores the serialized request [json] under [ceremonyId]. */
    public fun put(ceremonyId: String, json: String)

    /** Removes and returns the request for [ceremonyId], or `null` if absent/consumed. */
    public fun take(ceremonyId: String): String?
}

/**
 * In-memory [PasskeyCredentialStore] for samples and tests. Not for production:
 * credentials are lost on restart and never expire.
 */
public class InMemoryPasskeyCredentialStore : PasskeyCredentialStore {
    private val byCredentialId = ConcurrentHashMap<String, StoredPasskey>()

    override fun save(credential: StoredPasskey) {
        byCredentialId[credential.credentialId] = credential
    }

    override fun findByUsername(username: String): List<StoredPasskey> =
        byCredentialId.values.filter { it.username == username }

    override fun findByCredentialId(credentialId: String): List<StoredPasskey> =
        byCredentialId[credentialId]?.let { listOf(it) } ?: emptyList()

    override fun find(credentialId: String, userHandle: String): StoredPasskey? =
        byCredentialId[credentialId]?.takeIf { it.userHandle == userHandle }

    override fun userHandleForUsername(username: String): String? =
        byCredentialId.values.firstOrNull { it.username == username }?.userHandle

    override fun usernameForUserHandle(userHandle: String): String? =
        byCredentialId.values.firstOrNull { it.userHandle == userHandle }?.username

    override fun updateSignatureCount(credentialId: String, signatureCount: Long) {
        byCredentialId.computeIfPresent(credentialId) { _, existing ->
            StoredPasskey(
                credentialId = existing.credentialId,
                userHandle = existing.userHandle,
                username = existing.username,
                publicKeyCose = existing.publicKeyCose,
                signatureCount = signatureCount,
            )
        }
    }
}

/**
 * In-memory [PasskeyChallengeStore] for samples and tests. Not for production:
 * pending ceremonies are lost on restart and never time out.
 */
public class InMemoryPasskeyChallengeStore : PasskeyChallengeStore {
    private val pending = ConcurrentHashMap<String, String>()

    override fun put(ceremonyId: String, json: String) {
        pending[ceremonyId] = json
    }

    override fun take(ceremonyId: String): String? = pending.remove(ceremonyId)
}
