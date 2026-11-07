package io.github.androidpoet.passkeys.internal

import io.github.androidpoet.passkeys.PasskeyException
import io.github.androidpoet.passkeys.PasskeyResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a whole ceremony — option normalization, the native call and response
 * parsing — inside one failure boundary so every outcome is a [PasskeyResult].
 *
 * Coroutine cancellation is **not** a ceremony failure: a [CancellationException]
 * is rethrown so structured concurrency keeps working (a caller leaving the
 * screen must not receive a `Failure` it then shows as an error).
 */
internal inline fun <T> passkeyResult(
    mapError: (Throwable) -> PasskeyException = PasskeyPayloadMapper::exception,
    block: () -> T,
): PasskeyResult<T> =
    try {
        PasskeyResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Throwable,
    ) {
        PasskeyResult.Failure(mapError(e))
    }
