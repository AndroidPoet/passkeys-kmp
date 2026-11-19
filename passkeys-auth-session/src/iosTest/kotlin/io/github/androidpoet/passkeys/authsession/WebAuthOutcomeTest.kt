package io.github.androidpoet.passkeys.authsession

import io.github.androidpoet.passkeys.PasskeyException
import io.github.androidpoet.passkeys.PasskeyResult
import platform.Foundation.NSError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val WEB_AUTH_DOMAIN = "com.apple.AuthenticationServices.WebAuthenticationSession"

class WebAuthOutcomeTest {
    private fun failure(error: NSError?): PasskeyException = assertIs<PasskeyResult.Failure>(webAuthOutcome(null, error)).error

    @Test
    fun test_webAuthOutcome_callbackUrl_returnsRedirectUrl() {
        val outcome = webAuthOutcome("myapp://callback?code=abc", null)

        assertEquals(PasskeyResult.Success("myapp://callback?code=abc"), outcome)
    }

    @Test
    fun test_webAuthOutcome_canceledLogin_returnsUserCanceled() {
        val error = NSError.errorWithDomain(domain = WEB_AUTH_DOMAIN, code = 1, userInfo = null)

        assertIs<PasskeyException.UserCanceled>(failure(error))
    }

    @Test
    fun test_webAuthOutcome_otherSessionError_returnsUnexpected() {
        val error = NSError.errorWithDomain(domain = WEB_AUTH_DOMAIN, code = 2, userInfo = null)

        assertIs<PasskeyException.Unexpected>(failure(error))
    }

    @Test
    fun test_webAuthOutcome_sameCodeInAnotherDomain_returnsUnexpected() {
        val error = NSError.errorWithDomain(domain = "NSURLErrorDomain", code = 1, userInfo = null)

        assertIs<PasskeyException.Unexpected>(failure(error))
    }

    @Test
    fun test_webAuthOutcome_neitherUrlNorError_returnsUnexpected() {
        assertIs<PasskeyException.Unexpected>(failure(null))
    }
}
