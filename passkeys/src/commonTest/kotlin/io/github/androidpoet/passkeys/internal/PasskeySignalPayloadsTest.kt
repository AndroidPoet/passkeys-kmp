package io.github.androidpoet.passkeys.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class PasskeySignalPayloadsTest {
    @Test
    fun test_unknownCredential_buildsSignalRequestJson() {
        val json = PasskeySignalPayloads.unknownCredential(rpId = "example.com", credentialId = "Y3JlZA")

        assertEquals("""{"rpId":"example.com","credentialId":"Y3JlZA"}""", json)
    }

    @Test
    fun test_allAcceptedCredentials_buildsSignalRequestJson() {
        val json = PasskeySignalPayloads.allAcceptedCredentials("example.com", "dXNlcg", listOf("YQ", "Yg"))

        assertEquals("""{"rpId":"example.com","userId":"dXNlcg","allAcceptedCredentialIds":["YQ","Yg"]}""", json)
    }

    @Test
    fun test_allAcceptedCredentials_emptyList_keepsEmptyArray() {
        val json = PasskeySignalPayloads.allAcceptedCredentials("example.com", "dXNlcg", emptyList())

        assertEquals("""{"rpId":"example.com","userId":"dXNlcg","allAcceptedCredentialIds":[]}""", json)
    }

    @Test
    fun test_currentUserDetails_escapesNames() {
        val json = PasskeySignalPayloads.currentUserDetails("example.com", "dXNlcg", "a@b.c", "A \"B\"")

        assertEquals("""{"rpId":"example.com","userId":"dXNlcg","name":"a@b.c","displayName":"A \"B\""}""", json)
    }
}
