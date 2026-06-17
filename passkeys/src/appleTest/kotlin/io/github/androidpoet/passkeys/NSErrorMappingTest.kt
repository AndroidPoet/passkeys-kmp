package io.github.androidpoet.passkeys

import platform.Foundation.NSError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NSErrorMappingTest {
    private fun errorWithCode(code: Long): NSError =
        NSError.errorWithDomain(domain = "com.apple.AuthenticationServices.AuthorizationError", code = code, userInfo = null)

    @Test
    fun test_canceledCodeMapsToUserCanceled() {
        assertIs<PasskeyException.UserCanceled>(errorWithCode(1001).toPasskeyException())
    }

    @Test
    fun test_invalidResponseCodeMapsToDomError() {
        val mapped = errorWithCode(1002).toPasskeyException()
        assertIs<PasskeyException.DomError>(mapped)
        // DomError carries the actionable association/entitlement hint.
        assertEquals(1001, mapped.code)
    }

    @Test
    fun test_unsupportedCodeMapsToUnsupported() {
        assertIs<PasskeyException.Unsupported>(errorWithCode(1006).toPasskeyException())
    }

    @Test
    fun test_notHandledFailedAndInteractiveMapToUnexpected() {
        assertIs<PasskeyException.Unexpected>(errorWithCode(1003).toPasskeyException())
        assertIs<PasskeyException.Unexpected>(errorWithCode(1004).toPasskeyException())
        assertIs<PasskeyException.Unexpected>(errorWithCode(1005).toPasskeyException())
    }

    @Test
    fun test_unknownCodeFallsBackToUnexpected() {
        assertIs<PasskeyException.Unexpected>(errorWithCode(9999).toPasskeyException())
    }
}
