package io.github.androidpoet.passkeys

/**
 * A typed failure from a passkey ceremony, carried by [PasskeyResult.Failure].
 *
 * Each subtype maps to a distinct, stable [code] so you can branch on the
 * failure mode (user cancellation, unsupported device, no credential, …)
 * without parsing messages.
 *
 * @property code a stable numeric identifier for the failure category.
 * @property message a human-readable description of the failure.
 * @property cause the underlying platform error, if any.
 */
public sealed class PasskeyException(
    public val code: Int,
    override val message: String,
    override val cause: Throwable?,
) : Exception(message, cause) {
    /** A WebAuthn DOM error reported by the platform authenticator (code `1001`). */
    public class DomError(
        message: String,
        cause: Throwable? = null,
    ) : PasskeyException(1001, message, cause)

    /** The user dismissed or canceled the authenticator prompt (code `1002`). */
    public class UserCanceled(
        cause: Throwable? = null,
    ) : PasskeyException(1002, "User canceled the operation", cause)

    /** The ceremony was interrupted before it could complete (code `1003`). */
    public class Interrupted(
        cause: Throwable? = null,
    ) : PasskeyException(1003, "Credential operation was interrupted", cause)

    /** Passkeys are not supported on this device or OS version (code `1004`). */
    public class Unsupported(
        cause: Throwable? = null,
    ) : PasskeyException(1004, "Passkeys are not supported on this device", cause)

    /** No matching passkey credential was available to satisfy the request (code `1005`). */
    public class NoCredential(
        cause: Throwable? = null,
    ) : PasskeyException(1005, "No passkey credential is available", cause)

    /** The supplied options/response JSON was malformed or invalid (code `1006`). */
    public class InvalidPayload(
        cause: Throwable? = null,
    ) : PasskeyException(1006, "Passkey JSON payload is not valid", cause)

    /** An unexpected error not covered by the other cases (code `1007`). */
    public class Unexpected(
        cause: Throwable? = null,
    ) : PasskeyException(1007, "An unexpected passkey error occurred", cause)
}
