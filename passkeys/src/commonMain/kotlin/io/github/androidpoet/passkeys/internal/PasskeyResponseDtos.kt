package io.github.androidpoet.passkeys.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class PasskeyCreationDto(
    val id: String,
    val rawId: String,
    val type: String,
    val authenticatorAttachment: String? = null,
    val clientExtensionResults: JsonObject? = null,
    val response: PasskeyCreationResponseDto,
)

@Serializable
internal data class PasskeyCreationResponseDto(
    val attestationObject: String,
    val clientDataJSON: String,
    val transports: List<String> = emptyList(),
)

@Serializable
internal data class PasskeyAuthenticationDto(
    val id: String,
    val rawId: String,
    val type: String,
    val authenticatorAttachment: String? = null,
    val clientExtensionResults: JsonObject? = null,
    val response: PasskeyAuthenticationResponseDto,
)

@Serializable
internal data class PasskeyAuthenticationResponseDto(
    val clientDataJSON: String,
    val authenticatorData: String? = null,
    val signature: String? = null,
    val userHandle: String? = null,
)
