package io.github.androidpoet.passkeys

/**
 * The outcome of a passkey ceremony.
 *
 * A sealed type with exactly two cases — [Success] and [Failure] — so the
 * compiler forces you to handle both. Ceremonies return this instead of
 * throwing, keeping cancellation, timeouts and unsupported platforms on the
 * normal control-flow path.
 *
 * @param T the response type carried on success.
 */
public sealed class PasskeyResult<out T> {
    /**
     * The ceremony completed and produced a [value].
     *
     * @param value the platform response (e.g. a `PasskeyCreationResponse` or
     *   `PasskeyAuthenticationResponse`) whose `rawJson` you send to your server.
     */
    public data class Success<T>(
        public val value: T,
    ) : PasskeyResult<T>()

    /**
     * The ceremony did not complete.
     *
     * @param error the typed [PasskeyException] describing what went wrong.
     */
    public data class Failure(
        public val error: PasskeyException,
    ) : PasskeyResult<Nothing>()
}
