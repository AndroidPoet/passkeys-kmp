package io.github.androidpoet.passkeys.server

/**
 * Relying Party configuration for the passkey server.
 *
 * @property rpId the Relying Party ID — the registrable domain the passkey is
 *   scoped to (e.g. `example.com`). Must match the `rpId` the client uses.
 * @property rpName a human-readable name for the Relying Party, shown by some
 *   authenticators during the ceremony.
 * @property origins the exact origins permitted to perform ceremonies (e.g.
 *   `https://example.com`, or `http://localhost:8080` in development). The
 *   browser-reported origin in `clientDataJSON` must be one of these.
 */
public class PasskeyServerConfig(
    public val rpId: String,
    public val rpName: String,
    public val origins: Set<String>,
) {
    init {
        require(rpId.isNotBlank()) { "rpId must not be blank" }
        require(rpName.isNotBlank()) { "rpName must not be blank" }
        require(origins.isNotEmpty()) { "at least one origin is required" }
    }
}

/**
 * The user a registration ceremony is for.
 *
 * @property handle the WebAuthn user handle as a base64url string — an opaque,
 *   stable, non-personal identifier for the account (never an email or
 *   username). Reuse the same handle across the user's credentials.
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
        require(handle.isNotBlank()) { "handle must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
    }
}

/**
 * A pending ceremony: the WebAuthn options to hand to the client, plus the
 * [ceremonyId] that ties the eventual response back to this challenge.
 *
 * @property ceremonyId an opaque, single-use id. Return it to the client and
 *   send it back unchanged on the matching `finish` call.
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
 */
public class PasskeyAuthenticationOutcome(
    public val username: String,
    public val userHandle: String,
    public val credentialId: String,
    public val signatureCount: Long,
)

/**
 * Thrown when a ceremony cannot be verified: an unknown/expired [ceremonyId], a
 * malformed client response, a failed attestation/assertion check, or a
 * signature-counter regression. Treat any instance as a rejected ceremony.
 */
public class PasskeyVerificationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
