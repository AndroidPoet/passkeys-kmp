package io.github.androidpoet.passkeys.internal

import io.github.androidpoet.passkeys.PasskeyException
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object PasskeyPayloadMapper {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun creationRequestJson(requestJson: String): String =
        mapPayload(requestJson) { root ->
            val publicKey = root["publicKey"]?.jsonObject ?: root
            publicKey.replaceString("challenge") { it.normalizedBase64Url() }
                .replaceObject("user") { user -> user.replaceString("id") { it.normalizedBase64Url() } }
        }

    fun authenticationRequestJson(requestJson: String): String =
        mapPayload(requestJson) { root ->
            val publicKey = root["publicKey"]?.jsonObject ?: root
            publicKey.replaceString("challenge") { it.normalizedBase64Url() }
        }

    fun nativeCreationOptions(requestJson: String): NativeCreationOptions =
        mapJson { json.decodeFromString<NativeCreationOptions>(creationRequestJson(requestJson)) }

    fun nativeAuthenticationOptions(requestJson: String): NativeAuthenticationOptions =
        mapJson { json.decodeFromString<NativeAuthenticationOptions>(authenticationRequestJson(requestJson)) }

    fun creationResponse(responseJson: String): PasskeyCreationResponse =
        mapJson { json.decodeFromString<PasskeyCreationDto>(responseJson).toModel(responseJson) }

    fun authenticationResponse(responseJson: String): PasskeyAuthenticationResponse =
        mapJson { json.decodeFromString<PasskeyAuthenticationDto>(responseJson).toModel(responseJson) }

    fun exception(error: Throwable): PasskeyException =
        when (error) {
            is PasskeyException -> error
            is SerializationException, is IllegalArgumentException, is IndexOutOfBoundsException ->
                PasskeyException.InvalidPayload(error)
            else -> PasskeyException.Unexpected(error)
        }

    private fun mapPayload(requestJson: String, transform: (JsonObject) -> JsonObject): String =
        mapJson {
            val root = json.parseToJsonElement(requestJson).jsonObject
            json.encodeToString(JsonObject.serializer(), transform(root))
        }

    private inline fun <T> mapJson(block: () -> T): T =
        try {
            block()
        } catch (error: Exception) {
            throw exception(error)
        }

    private fun JsonObject.replaceString(key: String, transform: (String) -> String): JsonObject =
        replace(key) { JsonPrimitive(transform(it.jsonPrimitive.content)) }

    private fun JsonObject.replaceObject(key: String, transform: (JsonObject) -> JsonObject): JsonObject =
        replace(key) { transform(it.jsonObject) }

    private fun JsonObject.replace(key: String, transform: (JsonElement) -> JsonElement): JsonObject =
        JsonObject(toMutableMap().also { fields ->
            fields[key]?.let { fields[key] = transform(it) }
        })

    private fun PasskeyCreationDto.toModel(rawJson: String): PasskeyCreationResponse =
        PasskeyCreationResponse(
            id = id,
            rawId = rawId,
            type = type,
            authenticatorAttachment = authenticatorAttachment,
            attestationObject = response.attestationObject,
            clientDataJson = response.clientDataJSON,
            transports = response.transports,
            rawJson = rawJson,
            clientExtensionResultsJson = clientExtensionResults?.toString(),
        )

    private fun PasskeyAuthenticationDto.toModel(rawJson: String): PasskeyAuthenticationResponse =
        PasskeyAuthenticationResponse(
            id = id,
            rawId = rawId,
            type = type,
            authenticatorAttachment = authenticatorAttachment,
            clientDataJson = response.clientDataJSON,
            authenticatorData = response.authenticatorData,
            signature = response.signature,
            userHandle = response.userHandle,
            rawJson = rawJson,
            clientExtensionResultsJson = clientExtensionResults?.toString(),
        )
}
