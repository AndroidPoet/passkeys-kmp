package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse

/**
 * The common entry point for running passkey (WebAuthn) ceremonies.
 *
 * Each platform provides its own implementation backed by the device's native
 * authenticator (Face ID, Touch ID, Windows Hello, fingerprint, or a roaming
 * security key). In Compose Multiplatform, obtain the right implementation with
 * `rememberPasskeyClient()`; otherwise construct the platform client directly
 * and supply its presentation anchor.
 *
 * Every call returns a [PasskeyResult] — the client never throws for control
 * flow. The SDK only runs the on-device ceremony: a credential is trustworthy
 * **only after your backend verifies** the returned `rawJson`.
 */
public interface PasskeyClient {
    /**
     * Registers a new passkey.
     *
     * Runs the platform's create ceremony for the given [options] (a WebAuthn
     * `PublicKeyCredentialCreationOptions` produced by your server, including a
     * fresh challenge).
     *
     * @return a [PasskeyResult.Success] wrapping the attestation
     *   [PasskeyCreationResponse] to send to your server, or a
     *   [PasskeyResult.Failure] carrying a typed [PasskeyException].
     */
    public suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse>

    /**
     * Registers a new passkey from a raw options JSON string.
     *
     * Convenience overload equivalent to `create(PasskeyCreationOptions(requestJson))`.
     *
     * @param requestJson the WebAuthn `PublicKeyCredentialCreationOptions` JSON.
     */
    public suspend fun create(requestJson: String): PasskeyResult<PasskeyCreationResponse> =
        create(PasskeyCreationOptions(requestJson))

    /**
     * Authenticates with an existing passkey.
     *
     * Runs the platform's get ceremony for the given [options] (a WebAuthn
     * `PublicKeyCredentialRequestOptions` produced by your server, including a
     * fresh challenge).
     *
     * @return a [PasskeyResult.Success] wrapping the assertion
     *   [PasskeyAuthenticationResponse] to send to your server, or a
     *   [PasskeyResult.Failure] carrying a typed [PasskeyException].
     */
    public suspend fun authenticate(options: PasskeyAuthenticationOptions): PasskeyResult<PasskeyAuthenticationResponse>

    /**
     * Authenticates with an existing passkey from a raw options JSON string.
     *
     * Convenience overload equivalent to `authenticate(PasskeyAuthenticationOptions(requestJson))`.
     *
     * @param requestJson the WebAuthn `PublicKeyCredentialRequestOptions` JSON.
     */
    public suspend fun authenticate(requestJson: String): PasskeyResult<PasskeyAuthenticationResponse> =
        authenticate(PasskeyAuthenticationOptions(requestJson))
}
