package io.github.androidpoet.passkeys

import androidx.credentials.exceptions.publickeycredential.SignalCredentialSecurityException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialStateProviderConfigurationException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialUnknownException
import kotlin.test.Test
import kotlin.test.assertIs

class AndroidPasskeySignalsTest {
    @Test
    fun test_toSignalPasskeyException_providerConfiguration_returnsUnsupported() {
        val mapped = SignalCredentialStateProviderConfigurationException("no provider").toSignalPasskeyException()

        assertIs<PasskeyException.Unsupported>(mapped)
    }

    @Test
    fun test_toSignalPasskeyException_security_returnsDomError() {
        val mapped = SignalCredentialSecurityException("not allowed").toSignalPasskeyException()

        assertIs<PasskeyException.DomError>(mapped)
    }

    @Test
    fun test_toSignalPasskeyException_malformedId_returnsInvalidPayload() {
        val mapped = IllegalArgumentException("credentialId is not base64url").toSignalPasskeyException()

        assertIs<PasskeyException.InvalidPayload>(mapped)
    }

    @Test
    fun test_toSignalPasskeyException_unknown_returnsUnexpected() {
        val mapped = SignalCredentialUnknownException("boom").toSignalPasskeyException()

        assertIs<PasskeyException.Unexpected>(mapped)
    }
}
