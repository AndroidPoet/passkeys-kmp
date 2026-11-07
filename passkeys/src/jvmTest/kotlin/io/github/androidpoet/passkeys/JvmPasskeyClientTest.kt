package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.ApplePasskeyNativeBridge
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class JvmPasskeyClientTest {
    private val client = JvmPasskeyClient()

    @Test
    fun test_create_failsLoud() =
        runTest {
            val result = client.create(PasskeyCreationOptions("""{"challenge":"abc"}"""))
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertExpectedFailure(failure.error)
        }

    @Test
    fun test_authenticate_failsLoud() =
        runTest {
            val result = client.authenticate(PasskeyAuthenticationOptions("""{"challenge":"abc"}"""))
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertExpectedFailure(failure.error)
        }

    /**
     * Either way the client fails *loud* — it never silently succeeds without an
     * authenticator. The exact type depends on the host: with no in-process
     * backend (Windows/Linux, or macOS when the bundled dylib can't load) the
     * client short-circuits to [PasskeyException.Unsupported]; on macOS with the
     * backend present the common mapper rejects this minimal request as
     * [PasskeyException.InvalidPayload] before any native UI could appear.
     */
    private fun assertExpectedFailure(error: PasskeyException) {
        if (ApplePasskeyNativeBridge.available) {
            assertIs<PasskeyException.InvalidPayload>(error)
        } else {
            assertIs<PasskeyException.Unsupported>(error)
        }
    }

    @Test
    fun test_create_crossPlatformAttachment_isRefusedBeforeNative() =
        runTest {
            val request =
                """{"challenge":"aGVsbG8","rp":{"id":"example.com"},"user":{"id":"dXNlci0x","name":"u"},
                   "authenticatorSelection":{"authenticatorAttachment":"cross-platform"}}"""
            val result = client.create(PasskeyCreationOptions(request))
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertIs<PasskeyException.Unsupported>(failure.error)
        }
}
