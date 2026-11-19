package io.github.androidpoet.passkeys

/**
 * Tells the user's passkey provider what your server knows about its
 * credentials, so the provider can hide or rename passkeys that would
 * otherwise fail at sign-in (the WebAuthn Signal API).
 *
 * Signals are hints: they show no UI, a provider may ignore them, and a
 * failure never needs to be surfaced to the user. All ids are base64url, as
 * in the WebAuthn JSON your server already produces.
 *
 * - Android: `AndroidPasskeySignals` (Credential Manager, effective on Android 15+).
 * - iOS / macOS 26.2+: the system API is Swift-only, so implement this
 *   interface in a few lines of Swift in your app — see the documentation.
 */
public interface PasskeySignals {
    /**
     * Reports that [credentialId] is not known to the server — call it when a
     * sign-in fails because the passkey was deleted server-side.
     */
    public suspend fun unknownCredential(
        rpId: String,
        credentialId: String,
    ): PasskeyResult<Unit>

    /**
     * Reports the complete list of credential ids the server still accepts for
     * the user [userId]; the provider can drop any passkey not in it. Call it
     * after a successful sign-in.
     */
    public suspend fun allAcceptedCredentials(
        rpId: String,
        userId: String,
        acceptedCredentialIds: List<String>,
    ): PasskeyResult<Unit>

    /**
     * Reports the current [name] and [displayName] of the user [userId], so
     * the provider can relabel their passkeys after a rename.
     */
    public suspend fun currentUserDetails(
        rpId: String,
        userId: String,
        name: String,
        displayName: String,
    ): PasskeyResult<Unit>
}
