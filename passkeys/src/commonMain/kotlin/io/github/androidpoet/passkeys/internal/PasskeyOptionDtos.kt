package io.github.androidpoet.passkeys.internal

import kotlinx.serialization.Serializable

@Serializable
internal data class NativeCreationOptions(
    val challenge: String,
    val rp: NativeRelyingParty,
    val user: NativeUser,
    val pubKeyCredParams: List<NativePubKeyCredParam> = emptyList(),
    val excludeCredentials: List<NativeCredentialDescriptor> = emptyList(),
    val timeout: Long? = null,
    val attestation: String? = null,
    val authenticatorSelection: NativeAuthenticatorSelection? = null,
    val extensions: NativeCreationExtensions? = null,
) {
    /** The COSE algorithms the server accepts, in preference order; empty means "any". */
    val algorithms: List<Long> get() = pubKeyCredParams.filter { it.type == "public-key" }.map { it.alg }

    /** Whether the server requires a discoverable (resident) credential. */
    val requiresDiscoverableCredential: Boolean
        get() =
            authenticatorSelection?.residentKey == "required" ||
                (authenticatorSelection?.residentKey == null && authenticatorSelection?.requireResidentKey == true)
}

@Serializable
internal data class NativePubKeyCredParam(
    val type: String,
    val alg: Long,
)

@Serializable
internal data class NativeRelyingParty(
    val id: String,
    val name: String? = null,
)

@Serializable
internal data class NativeUser(
    val id: String,
    val name: String,
    val displayName: String? = null,
)

@Serializable
internal data class NativeAuthenticatorSelection(
    val userVerification: String? = null,
    val residentKey: String? = null,
    val requireResidentKey: Boolean? = null,
    val authenticatorAttachment: String? = null,
)

@Serializable
internal data class NativeAuthenticationOptions(
    val challenge: String,
    val rpId: String,
    val allowCredentials: List<NativeCredentialDescriptor> = emptyList(),
    val timeout: Long? = null,
    val userVerification: String? = null,
    val extensions: NativeAuthenticationExtensions? = null,
)

@Serializable
internal data class NativeCredentialDescriptor(
    val id: String,
    val type: String,
    val transports: List<String> = emptyList(),
)

@Serializable
internal data class NativeCreationExtensions(
    val largeBlob: NativeLargeBlobCreationInput? = null,
    val prf: NativePrfCreationInput? = null,
)

@Serializable
internal data class NativeAuthenticationExtensions(
    val largeBlob: NativeLargeBlobAuthenticationInput? = null,
    val prf: NativePrfAuthenticationInput? = null,
)

@Serializable
internal data class NativeLargeBlobCreationInput(
    val support: String? = null,
)

@Serializable
internal data class NativeLargeBlobAuthenticationInput(
    val read: Boolean = false,
    val write: String? = null,
)

@Serializable
internal data class NativePrfCreationInput(
    val eval: NativePrfValues? = null,
    val evalByCredential: Map<String, NativePrfValues> = emptyMap(),
)

@Serializable
internal data class NativePrfAuthenticationInput(
    val eval: NativePrfValues? = null,
    val evalByCredential: Map<String, NativePrfValues> = emptyMap(),
)

@Serializable
internal data class NativePrfValues(
    val first: String,
    val second: String? = null,
)
