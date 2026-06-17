package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class JvmPasskeyClientTest {
    private val client = JvmPasskeyClient()

    @Test
    fun test_create_failsLoudWithUnsupported() =
        runTest {
            val result = client.create(PasskeyCreationOptions("""{"challenge":"abc"}"""))
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertIs<PasskeyException.Unsupported>(failure.error)
        }

    @Test
    fun test_authenticate_failsLoudWithUnsupported() =
        runTest {
            val result = client.authenticate(PasskeyAuthenticationOptions("""{"challenge":"abc"}"""))
            val failure = assertIs<PasskeyResult.Failure>(result)
            assertIs<PasskeyException.Unsupported>(failure.error)
        }
}
