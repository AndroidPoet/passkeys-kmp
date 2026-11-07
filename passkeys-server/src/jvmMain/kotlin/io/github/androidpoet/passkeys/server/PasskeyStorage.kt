package io.github.androidpoet.passkeys.server

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
 * @property backupEligible the BE flag recorded at registration. Persist it:
 *   the verifier rejects a later assertion whose BE flag changed.
 * @property backedUp the latest BS flag (updated after each assertion).
 * @property transports the transports the authenticator advertised at
 *   registration (e.g. `internal`, `hybrid`, `usb`), echoed into future
 *   `allowCredentials` hints.
 */
public class StoredPasskey(
    public val credentialId: String,
    public val userHandle: String,
    public val username: String,
    public val publicKeyCose: String,
    public val signatureCount: Long,
    public val backupEligible: Boolean = false,
    public val backedUp: Boolean = false,
    public val transports: Set<String> = emptySet(),
)

/**
 * Bring-your-own credential persistence. Back this with your real database; the
 * library never assumes a storage engine. See [InMemoryPasskeyCredentialStore]
 * for a reference (non-production) implementation.
 *
 * Production stores should enforce two uniqueness constraints: one credential
 * row per [StoredPasskey.credentialId], and a one-to-one mapping between
 * [StoredPasskey.username] and [StoredPasskey.userHandle].
 */
public interface PasskeyCredentialStore {
    /**
     * Persists a newly registered credential. Insert-only: must throw
     * [IllegalStateException] (never overwrite) if the credential id exists.
     */
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

    /**
     * Records a verified assertion: sets the signature counter to
     * [signatureCount] and the BS flag to [backedUp], **only if** the stored
     * counter still equals [expectedSignatureCount] (the value verification
     * used). Returns `false` when the counter moved underneath us — a
     * concurrent assertion — so the caller rejects this one rather than
     * regressing the counter. Implement as a compare-and-set / conditional
     * update in your database.
     */
    public fun recordAssertion(
        credentialId: String,
        expectedSignatureCount: Long,
        signatureCount: Long,
        backedUp: Boolean,
    ): Boolean
}

/**
 * Single-use storage for the pending ceremony state keyed by ceremony id. Back
 * this with a short-TTL store (e.g. Redis `SET ... EX` + `GETDEL`) in
 * production; [take] must return each entry at most once, atomically. Expiry is
 * also enforced by the Relying Party from the stored record, so a store that
 * merely forgets entries after [PasskeyServerConfig.challengeTimeout] is enough.
 */
public interface PasskeyChallengeStore {
    /** Stores the serialized pending ceremony [json] under [ceremonyId]. */
    public fun put(ceremonyId: String, json: String)

    /** Removes and returns the record for [ceremonyId], or `null` if absent/consumed. */
    public fun take(ceremonyId: String): String?
}

/**
 * In-memory [PasskeyCredentialStore] for samples and tests. Not for production:
 * credentials are lost on restart and never expire.
 */
public class InMemoryPasskeyCredentialStore : PasskeyCredentialStore {
    private val byCredentialId = ConcurrentHashMap<String, StoredPasskey>()

    override fun save(credential: StoredPasskey) {
        val previous = byCredentialId.putIfAbsent(credential.credentialId, credential)
        check(previous == null) { "credential '${credential.credentialId}' is already registered" }
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

    override fun recordAssertion(
        credentialId: String,
        expectedSignatureCount: Long,
        signatureCount: Long,
        backedUp: Boolean,
    ): Boolean {
        var updated = false
        byCredentialId.computeIfPresent(credentialId) { _, existing ->
            if (existing.signatureCount != expectedSignatureCount) return@computeIfPresent existing
            updated = true
            StoredPasskey(
                credentialId = existing.credentialId,
                userHandle = existing.userHandle,
                username = existing.username,
                publicKeyCose = existing.publicKeyCose,
                signatureCount = signatureCount,
                backupEligible = existing.backupEligible,
                backedUp = backedUp,
                transports = existing.transports,
            )
        }
        return updated
    }
}

/**
 * In-memory [PasskeyChallengeStore] for samples and tests. Entries expire after
 * [ttl] and the store holds at most [maxPending] entries (oldest evicted first)
 * so an unauthenticated `begin` flood cannot grow memory without bound. Not for
 * production: pending ceremonies are lost on restart and not shared across
 * instances.
 */
public class InMemoryPasskeyChallengeStore(
    private val ttl: Duration = DEFAULT_TTL,
    private val maxPending: Int = DEFAULT_MAX_PENDING,
    private val clock: Clock = Clock.systemUTC(),
) : PasskeyChallengeStore {
    private class Entry(
        val json: String,
        val insertedAtMillis: Long,
    )

    private val pending = LinkedHashMap<String, Entry>()
    private val lock = Any()

    init {
        require(ttl.isPositive()) { "ttl must be positive" }
        require(maxPending > 0) { "maxPending must be positive" }
    }

    override fun put(ceremonyId: String, json: String) {
        val now = clock.millis()
        synchronized(lock) {
            pruneExpired(now)
            pending.remove(ceremonyId)
            while (pending.size >= maxPending) {
                pending.remove(pending.keys.first())
            }
            pending[ceremonyId] = Entry(json, now)
        }
    }

    override fun take(ceremonyId: String): String? {
        val now = clock.millis()
        return synchronized(lock) {
            pruneExpired(now)
            pending.remove(ceremonyId)?.json
        }
    }

    private fun pruneExpired(nowMillis: Long) {
        val cutoff = nowMillis - ttl.inWholeMilliseconds
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.insertedAtMillis < cutoff) iterator.remove() else return
        }
    }

    private companion object {
        val DEFAULT_TTL: Duration = 10.minutes
        const val DEFAULT_MAX_PENDING = 10_000
    }
}
