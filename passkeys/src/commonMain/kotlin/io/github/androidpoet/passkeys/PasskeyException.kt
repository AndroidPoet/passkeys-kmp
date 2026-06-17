package io.github.androidpoet.passkeys

public sealed class PasskeyException(
    public val code: Int,
    override val message: String,
    override val cause: Throwable?,
) : Exception(message, cause) {
    public class DomError(
        message: String,
        cause: Throwable? = null,
    ) : PasskeyException(1001, message, cause)

    public class UserCanceled(
        cause: Throwable? = null,
    ) : PasskeyException(1002, "User canceled the operation", cause)

    public class Interrupted(
        cause: Throwable? = null,
    ) : PasskeyException(1003, "Credential operation was interrupted", cause)

    public class Unsupported(
        cause: Throwable? = null,
    ) : PasskeyException(1004, "Passkeys are not supported on this device", cause)

    public class NoCredential(
        cause: Throwable? = null,
    ) : PasskeyException(1005, "No passkey credential is available", cause)

    public class InvalidPayload(
        cause: Throwable? = null,
    ) : PasskeyException(1006, "Passkey JSON payload is not valid", cause)

    public class Unexpected(
        cause: Throwable? = null,
    ) : PasskeyException(1007, "An unexpected passkey error occurred", cause)
}
