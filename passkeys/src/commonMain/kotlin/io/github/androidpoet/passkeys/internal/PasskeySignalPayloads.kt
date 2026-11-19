package io.github.androidpoet.passkeys.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The WebAuthn Signal API request bodies, as the platform APIs expect them. */
internal object PasskeySignalPayloads {
    fun unknownCredential(
        rpId: String,
        credentialId: String,
    ): String =
        buildJsonObject {
            put("rpId", rpId)
            put("credentialId", credentialId)
        }.toString()

    fun allAcceptedCredentials(
        rpId: String,
        userId: String,
        acceptedCredentialIds: List<String>,
    ): String =
        buildJsonObject {
            put("rpId", rpId)
            put("userId", userId)
            put("allAcceptedCredentialIds", JsonArray(acceptedCredentialIds.map(::JsonPrimitive)))
        }.toString()

    fun currentUserDetails(
        rpId: String,
        userId: String,
        name: String,
        displayName: String,
    ): String =
        buildJsonObject {
            put("rpId", rpId)
            put("userId", userId)
            put("name", name)
            put("displayName", displayName)
        }.toString()
}
