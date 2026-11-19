package io.github.androidpoet.passkeys.authsession

import androidx.browser.auth.AuthTabIntent
import io.github.androidpoet.passkeys.PasskeyException
import io.github.androidpoet.passkeys.PasskeyResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthTabOutcomeTest {
    private fun failure(resultCode: Int, redirectUrl: String? = null): PasskeyException =
        assertIs<PasskeyResult.Failure>(authTabOutcome(resultCode, redirectUrl)).error

    @Test
    fun test_authTabOutcome_okWithRedirect_returnsRedirectUrl() {
        val outcome = authTabOutcome(AuthTabIntent.RESULT_OK, "myapp://callback?code=abc")

        assertEquals(PasskeyResult.Success("myapp://callback?code=abc"), outcome)
    }

    @Test
    fun test_authTabOutcome_okWithoutRedirect_returnsUnexpected() {
        assertIs<PasskeyException.Unexpected>(failure(AuthTabIntent.RESULT_OK))
    }

    @Test
    fun test_authTabOutcome_canceled_returnsUserCanceled() {
        assertIs<PasskeyException.UserCanceled>(failure(AuthTabIntent.RESULT_CANCELED))
    }

    @Test
    fun test_authTabOutcome_verificationFailedOrTimedOut_returnsDomError() {
        assertIs<PasskeyException.DomError>(failure(AuthTabIntent.RESULT_VERIFICATION_FAILED))
        assertIs<PasskeyException.DomError>(failure(AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT))
    }

    @Test
    fun test_authTabOutcome_unknownCode_returnsUnexpected() {
        assertIs<PasskeyException.Unexpected>(failure(AuthTabIntent.RESULT_UNKNOWN_CODE))
    }
}
