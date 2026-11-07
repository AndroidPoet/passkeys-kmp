package io.github.androidpoet.passkeys.internal

import io.github.androidpoet.passkeys.PasskeyException
import io.github.androidpoet.passkeys.PasskeyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PasskeyResultsTest {
    @Test
    fun test_passkeyResult_whenBlockSucceeds_wrapsValue() {
        val result = passkeyResult { "ok" }

        assertEquals(PasskeyResult.Success("ok"), result)
    }

    @Test
    fun test_passkeyResult_whenBlockThrows_mapsToFailure() {
        val result = passkeyResult<String> { throw IllegalArgumentException("bad json") }

        val failure = assertIs<PasskeyResult.Failure>(result)
        assertIs<PasskeyException.InvalidPayload>(failure.error)
    }

    @Test
    fun test_passkeyResult_whenBlockThrowsPasskeyException_keepsIt() {
        val result = passkeyResult<String> { throw PasskeyException.CredentialExcluded() }

        val failure = assertIs<PasskeyResult.Failure>(result)
        assertIs<PasskeyException.CredentialExcluded>(failure.error)
    }

    @Test
    fun test_passkeyResult_whenCancelled_rethrowsInsteadOfFailing() =
        runTest {
            assertFailsWith<CancellationException> {
                passkeyResult<String> { throw CancellationException("caller left the screen") }
            }
        }
}
