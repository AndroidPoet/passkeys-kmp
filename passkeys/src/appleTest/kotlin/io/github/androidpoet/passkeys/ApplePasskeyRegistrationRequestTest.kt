package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyle
import platform.AuthenticationServices.ASPresentationAnchor
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CREATION_OPTIONS_JSON =
    """{"challenge":"AAAA","rp":{"id":"example.com","name":"Example"},""" +
        """"user":{"id":"AQID","name":"user@example.com","displayName":"User"},""" +
        """"pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""

class ApplePasskeyRegistrationRequestTest {
    private val client = ApplePasskeyClient(anchor = { ASPresentationAnchor() })
    private val nativeOptions = PasskeyPayloadMapper.nativeCreationOptions(CREATION_OPTIONS_JSON)

    @Test
    fun test_registrationRequest_conditionalCreate_setsConditionalRequestStyle() {
        if (!ApplePasskeyPlatform.supportsConditionalRegistration()) return

        val request = client.registrationRequest(nativeOptions, conditional = true)

        assertEquals(
            ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyle
                .ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyleConditional,
            request.requestStyle,
        )
    }

    @Test
    fun test_registrationRequest_defaultCreate_keepsStandardRequestStyle() {
        if (!ApplePasskeyPlatform.supportsConditionalRegistration()) return

        val request = client.registrationRequest(nativeOptions)

        assertEquals(
            ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyle
                .ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyleStandard,
            request.requestStyle,
        )
    }
}
