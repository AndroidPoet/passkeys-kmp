package io.github.androidpoet.passkeys

import platform.Foundation.NSError

private const val PASSKEY_CANCELED_ERROR_CODE = 1001L
private const val PASSKEY_INVALID_RESPONSE_ERROR_CODE = 1002L
private const val PASSKEY_NOT_HANDLED_ERROR_CODE = 1003L
private const val PASSKEY_FAILED_ERROR_CODE = 1004L
private const val PASSKEY_NOT_INTERACTIVE_ERROR_CODE = 1005L
private const val PASSKEY_UNSUPPORTED_ERROR_CODE = 1006L
private const val DOM_MESSAGE =
    "DOM error from WebAuthn. Check your apple-app-site-association file and webcredentials entitlement."

internal fun NSError.toPasskeyException(): PasskeyException =
    when (code) {
        PASSKEY_CANCELED_ERROR_CODE -> PasskeyException.UserCanceled(Throwable(localizedDescription))
        PASSKEY_INVALID_RESPONSE_ERROR_CODE -> PasskeyException.DomError(DOM_MESSAGE, Throwable(localizedDescription))
        PASSKEY_UNSUPPORTED_ERROR_CODE -> PasskeyException.Unsupported(Throwable(localizedDescription))
        PASSKEY_NOT_HANDLED_ERROR_CODE,
        PASSKEY_NOT_INTERACTIVE_ERROR_CODE,
        PASSKEY_FAILED_ERROR_CODE,
        -> PasskeyException.Unexpected(Throwable(localizedDescription))
        else -> PasskeyException.Unexpected(Throwable(localizedDescription))
    }
