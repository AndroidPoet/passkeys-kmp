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
     * backend present it reaches the native side, which rejects this minimal
     * request with a loud [PasskeyException.Unexpected] (a parse failure).
     */
    private fun assertExpectedFailure(error: PasskeyException) {
        if (ApplePasskeyNativeBridge.available) {
            assertIs<PasskeyException.Unexpected>(error)
        } else {
            assertIs<PasskeyException.Unsupported>(error)
        }
    }
}
