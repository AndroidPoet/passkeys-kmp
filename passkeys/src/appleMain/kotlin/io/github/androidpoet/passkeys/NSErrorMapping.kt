package io.github.androidpoet.passkeys

import platform.Foundation.NSError

private const val PASSKEY_CANCELED_ERROR_CODE = 1001L
private const val PASSKEY_INVALID_RESPONSE_ERROR_CODE = 1002L
private const val PASSKEY_NOT_HANDLED_ERROR_CODE = 1003L
private const val PASSKEY_FAILED_ERROR_CODE = 1004L
private const val PASSKEY_NOT_INTERACTIVE_ERROR_CODE = 1005L

// ASAuthorizationError.matchedExcludedCredential: the account already has a
// passkey on this authenticator (an excludeCredentials entry matched).
private const val PASSKEY_MATCHED_EXCLUDED_CREDENTIAL_ERROR_CODE = 1006L

// ASAuthorizationError.deviceNotConfiguredForPasskeyCreation (iOS / macOS 26+):
// no passkey provider is set up, e.g. iCloud Keychain is off.
private const val PASSKEY_DEVICE_NOT_CONFIGURED_ERROR_CODE = 1010L
private const val AUTHORIZATION_ERROR_DOMAIN = "com.apple.AuthenticationServices.AuthorizationError"
private const val DOM_MESSAGE =
    "DOM error from WebAuthn. Check your apple-app-site-association file and webcredentials entitlement."

internal fun NSError.toPasskeyException(): PasskeyException {
    val cause = Throwable("$domain:$code ${localizedDescription.ifBlank { "(no description)" }}")
    if (domain != AUTHORIZATION_ERROR_DOMAIN) return PasskeyException.Unexpected(cause)
    return when (code) {
        PASSKEY_CANCELED_ERROR_CODE -> PasskeyException.UserCanceled(cause)
        PASSKEY_INVALID_RESPONSE_ERROR_CODE -> PasskeyException.DomError(DOM_MESSAGE, cause)
        PASSKEY_MATCHED_EXCLUDED_CREDENTIAL_ERROR_CODE -> PasskeyException.CredentialExcluded(cause)
        PASSKEY_DEVICE_NOT_CONFIGURED_ERROR_CODE -> PasskeyException.Unsupported(cause)
        PASSKEY_NOT_HANDLED_ERROR_CODE,
        PASSKEY_NOT_INTERACTIVE_ERROR_CODE,
        PASSKEY_FAILED_ERROR_CODE,
        -> PasskeyException.Unexpected(cause)
        else -> PasskeyException.Unexpected(cause)
    }
}
