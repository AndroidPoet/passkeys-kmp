package io.github.androidpoet.passkeys.models

/**
 * Options for registering (creating) a passkey.
 *
 * @property requestJson the WebAuthn `PublicKeyCredentialCreationOptions` JSON
 *   produced by your server, including a fresh challenge.
 * @property preferImmediatelyAvailableCredentials when `true`, prefer authenticators
 *   that can satisfy the request without extra setup (no fallback UI).
 * @property isConditionalCreateRequest when `true`, perform a conditional
 *   (passive) create where the platform supports it.
 */
public data class PasskeyCreationOptions(
    public val requestJson: String,
    public val preferImmediatelyAvailableCredentials: Boolean = false,
    public val isConditionalCreateRequest: Boolean = false,
)

/**
 * Options for authenticating with an existing passkey.
 *
 * @property requestJson the WebAuthn `PublicKeyCredentialRequestOptions` JSON
 *   produced by your server, including a fresh challenge.
 */
public data class PasskeyAuthenticationOptions(
    public val requestJson: String,
)

/**
 * The attestation response produced by a successful create ceremony.
 *
 * Send [rawJson] to your server to verify and store the new credential. The
 * individual fields are decoded conveniences over that same payload.
 *
 * @property id the base64url credential id.
 * @property rawId the raw credential id (base64url-encoded bytes).
 * @property type the credential type, typically `"public-key"`.
 * @property authenticatorAttachment `"platform"` or `"cross-platform"`, if reported.
 * @property attestationObject the CBOR attestation object (base64url).
 * @property clientDataJson the client data JSON (base64url).
 * @property transports the transports the authenticator advertised (e.g. `internal`, `usb`).
 * @property rawJson the full WebAuthn registration response JSON — the source of truth to verify server-side.
 * @property clientExtensionResultsJson the client extension results JSON, if any extensions were requested.
 */
public data class PasskeyCreationResponse(
    public val id: String,
    public val rawId: String,
    public val type: String,
    public val authenticatorAttachment: String?,
    public val attestationObject: String,
    public val clientDataJson: String,
    public val transports: List<String>,
    public val rawJson: String,
    public val clientExtensionResultsJson: String? = null,
)

/**
 * The assertion response produced by a successful authenticate ceremony.
 *
 * Send [rawJson] to your server to verify the assertion. The individual fields
 * are decoded conveniences over that same payload.
 *
 * @property id the base64url credential id.
 * @property rawId the raw credential id (base64url-encoded bytes).
 * @property type the credential type, typically `"public-key"`.
 * @property authenticatorAttachment `"platform"` or `"cross-platform"`, if reported.
 * @property clientDataJson the client data JSON (base64url).
 * @property authenticatorData the authenticator data (base64url), if present.
 * @property signature the assertion signature (base64url), if present.
 * @property userHandle the user handle returned by the authenticator (base64url), if present.
 * @property rawJson the full WebAuthn authentication response JSON — the source of truth to verify server-side.
 * @property clientExtensionResultsJson the client extension results JSON, if any extensions were requested.
 */
public data class PasskeyAuthenticationResponse(
    public val id: String,
    public val rawId: String,
    public val type: String,
    public val authenticatorAttachment: String?,
    public val clientDataJson: String,
    public val authenticatorData: String?,
    public val signature: String?,
    public val userHandle: String?,
    public val rawJson: String,
    public val clientExtensionResultsJson: String? = null,
)
