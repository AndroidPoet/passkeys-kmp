package io.github.androidpoet.passkeys.models

public data class PasskeyCreationOptions(
    public val requestJson: String,
    public val preferImmediatelyAvailableCredentials: Boolean = false,
    public val isConditionalCreateRequest: Boolean = false,
)

public data class PasskeyAuthenticationOptions(public val requestJson: String)

public data class PasskeyCreationResponse(
    public val id: String,
    public val rawId: String,
    public val type: String,
    public val authenticatorAttachment: String?,
    public val attestationObject: String,
    public val clientDataJson: String,
    public val transports: List<String>,
    public val rawJson: String,
    public val clientExtensionResultsJson: String? = null,
)

public data class PasskeyAuthenticationResponse(
    public val id: String,
    public val rawId: String,
    public val type: String,
    public val authenticatorAttachment: String?,
    public val clientDataJson: String,
    public val authenticatorData: String?,
    public val signature: String?,
    public val userHandle: String?,
    public val rawJson: String,
    public val clientExtensionResultsJson: String? = null,
)
