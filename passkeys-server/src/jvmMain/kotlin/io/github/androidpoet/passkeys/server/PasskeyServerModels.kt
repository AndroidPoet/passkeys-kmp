package io.github.androidpoet.passkeys.server

import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How strongly the Relying Party asks for — and enforces — local user
 * verification (biometric / PIN) during a ceremony.
 *
 * `REQUIRED` is enforced server-side: a correctly signed response whose
 * authenticator data lacks the UV flag is rejected. `PREFERRED` and
 * `DISCOURAGED` are hints to the authenticator only.
 */
public enum class PasskeyUserVerification {
    REQUIRED,
    PREFERRED,
    DISCOURAGED,
}

/**
 * Relying Party configuration for the passkey server.
 *
 * @property rpId the Relying Party ID — the registrable domain the passkey is
 *   scoped to (e.g. `example.com`). Must match the `rpId` the client uses.
 * @property rpName a human-readable name for the Relying Party, shown by some
 *   authenticators during the ceremony.
 * @property origins the exact origins permitted to perform ceremonies (e.g.
 *   `https://example.com`, or `http://localhost:8080` in development). The
 *   browser-reported origin in `clientDataJSON` must be one of these, port
 *   included. Native Android apps report `android:apk-key-hash:<sha256>`
 *   origins; list those too when verifying Android registrations.
 * @property userVerification the user-verification policy applied to every
 *   ceremony (override per authentication via
 *   [PasskeyRelyingParty.beginAuthentication]). Defaults to `PREFERRED`.
 * @property challengeTimeout how long a `begin` ceremony stays valid before its
 *   `finish` is rejected as expired. Defaults to five minutes.
 * @property allowOriginPort when `true`, an origin in [origins] without an
 *   explicit port also matches the same scheme + host on any port. Off by
 *   default: different ports are different origins. Enable only for local
 *   development.
 */
public class PasskeyServerConfig(
    public val rpId: String,
    public val rpName: String,
    public val origins: Set<String>,
    public val userVerification: PasskeyUserVerification = PasskeyUserVerification.PREFERRED,
    public val challengeTimeout: Duration = DEFAULT_CHALLENGE_TIMEOUT,
    public val allowOriginPort: Boolean = false,
) {
    init {
        require(rpId.isNotBlank()) { "rpId must not be blank" }
        require(rpName.isNotBlank()) { "rpName must not be blank" }
        require(origins.isNotEmpty()) { "at least one origin is required" }
        require(challengeTimeout.isPositive()) { "challengeTimeout must be positive" }
    }

    private companion object {
        val DEFAULT_CHALLENGE_TIMEOUT: Duration = 5.minutes
    }
}

/**
 * The user a registration ceremony is for.
 *
 * The server — never the client — must decide which account a registration
 * belongs to: a successful WebAuthn ceremony proves possession of an
 * authenticator, not ownership of an account. Resolve [handle] and [name] from
 * your own authenticated session or sign-up flow.
 *
 * @property handle the WebAuthn user handle as a canonical (unpadded) base64url
 *   string of 1–64 bytes — an opaque, stable, non-personal identifier for the
 *   account (never an email or username). Reuse the same handle across the
 *   user's credentials.
 * @property name the account name (e.g. the login/username or email), shown in
 *   the authenticator's account picker.
 * @property displayName a friendly display name for the account.
 */
public class PasskeyUser(
    public val handle: String,
    public val name: String,
    public val displayName: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        val decoded =
            try {
                Base64.getUrlDecoder().decode(handle)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("handle must be base64url", e)
            }
        require(decoded.size in 1..MAX_USER_HANDLE_BYTES) { "handle must decode to 1..64 bytes" }
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == handle) {
            "handle must be canonical unpadded base64url"
        }
    }

    private companion object {
        const val MAX_USER_HANDLE_BYTES = 64
    }
}

/**
 * A pending ceremony: the WebAuthn options to hand to the client, plus the
 * [ceremonyId] that ties the eventual response back to this challenge.
 *
 * @property ceremonyId an opaque, single-use id. Return it to the client and
 *   send it back unchanged on the matching `finish` call. Treat it like a
 *   short-lived bearer token: tie it to the caller's session where you can.
 * @property optionsJson the WebAuthn options JSON for the client — a
 *   `navigator.credentials` request envelope (`{"publicKey": {...}}`) that this
 *   library's clients accept directly as their `requestJson`.
 */
public class PasskeyCeremonyRequest(
    public val ceremonyId: String,
    public val optionsJson: String,
)

/**
 * The result of a verified authentication (assertion) ceremony.
 *
 * @property username the account name the verified credential belongs to.
 * @property userHandle the base64url user handle the credential belongs to.
 * @property credentialId the base64url id of the credential that signed.
 * @property signatureCount the authenticator's new signature counter, already
 *   persisted via the credential store.
 * @property userVerified whether the authenticator reported local user
 *   verification (the UV flag) for this assertion.
 * @property backupEligible whether the credential is eligible for backup (the
 *   BE flag) — a synced, multi-device passkey.
 * @property backedUp whether the credential is currently backed up (the BS flag).
 */
public class PasskeyAuthenticationOutcome(
    public val username: String,
    public val userHandle: String,
    public val credentialId: String,
    public val signatureCount: Long,
    public val userVerified: Boolean,
    public val backupEligible: Boolean,
    public val backedUp: Boolean,
)

/**
 * Thrown when a ceremony cannot be verified: an unknown/expired [ceremonyId], a
 * malformed client response, a failed attestation/assertion check, a
 * signature-counter regression, or a conflicting account mapping. Treat any
 * instance as a rejected ceremony.
 */
public class PasskeyVerificationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
