package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import platform.AuthenticationServices.ASAuthorizationCredentialProtocol
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttachment
import platform.Foundation.NSData
import platform.Foundation.base64Encoding

private const val PASSKEY_TYPE = "public-key"

/**
 * Serializes AuthenticationServices credentials into the W3C WebAuthn JSON
 * shape every verifier (including `passkeys-server`) parses.
 *
 * Every OS-version-gated property (`attachment`, `largeBlob`, `prf`) is read
 * only behind its [ApplePasskeyPlatform] gate — the getters do not exist on
 * older supported OS versions and would crash a plain ceremony there — and
 * `clientExtensionResults` is always emitted (as `{}` when empty) because the
 * JSON shape requires the member.
 */
internal object ApplePasskeyResponses {
    fun registration(credential: ASAuthorizationCredentialProtocol): PasskeyCreationResponse {
        val registration =
            credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration
                ?: throw PasskeyException.Unexpected(IllegalStateException("unexpected registration credential type"))
        val attestationObject =
            registration.rawAttestationObject
                ?: throw PasskeyException.Unexpected(IllegalStateException("rawAttestationObject is null"))
        val attachment = registration.attachmentWireValue()
        val extensionResults = registrationExtensionResults(registration)
        val rawJson =
            buildJsonObject {
                put("id", JsonPrimitive(registration.credentialID.toBase64Url()))
                put("rawId", JsonPrimitive(registration.credentialID.toBase64Url()))
                put("type", JsonPrimitive(PASSKEY_TYPE))
                put("authenticatorAttachment", JsonPrimitive(attachment))
                put("clientExtensionResults", extensionResults)
                put(
                    "response",
                    buildJsonObject {
                        put("attestationObject", JsonPrimitive(attestationObject.toBase64Url()))
                        put("clientDataJSON", JsonPrimitive(registration.rawClientDataJSON.toBase64Url()))
                        put("transports", JsonArray(listOf(JsonPrimitive("internal"))))
                    },
                )
            }.toString()
        return PasskeyCreationResponse(
            id = registration.credentialID.toBase64Url(),
            rawId = registration.credentialID.toBase64Url(),
            type = PASSKEY_TYPE,
            authenticatorAttachment = attachment,
            attestationObject = attestationObject.toBase64Url(),
            clientDataJson = registration.rawClientDataJSON.toBase64Url(),
            transports = listOf("internal"),
            clientExtensionResultsJson = extensionResults.toString(),
            rawJson = rawJson,
        )
    }

    fun assertion(credential: ASAuthorizationCredentialProtocol): PasskeyAuthenticationResponse {
        val assertion =
            credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion
                ?: throw PasskeyException.Unexpected(IllegalStateException("unexpected assertion credential type"))
        val authenticatorData = assertion.rawAuthenticatorData.requireField("rawAuthenticatorData")
        val signature = assertion.signature.requireField("signature")
        val attachment = assertion.attachmentWireValue()
        val extensionResults = assertionExtensionResults(assertion)
        val rawJson =
            buildJsonObject {
                put("id", JsonPrimitive(assertion.credentialID.toBase64Url()))
                put("rawId", JsonPrimitive(assertion.credentialID.toBase64Url()))
                put("type", JsonPrimitive(PASSKEY_TYPE))
                put("authenticatorAttachment", JsonPrimitive(attachment))
                put("clientExtensionResults", extensionResults)
                put(
                    "response",
                    buildJsonObject {
                        put("clientDataJSON", JsonPrimitive(assertion.rawClientDataJSON.toBase64Url()))
                        put("authenticatorData", JsonPrimitive(authenticatorData.toBase64Url()))
                        put("signature", JsonPrimitive(signature.toBase64Url()))
                        assertion.userID?.let { put("userHandle", JsonPrimitive(it.toBase64Url())) }
                    },
                )
            }.toString()
        return PasskeyAuthenticationResponse(
            id = assertion.credentialID.toBase64Url(),
            rawId = assertion.credentialID.toBase64Url(),
            type = PASSKEY_TYPE,
            authenticatorAttachment = attachment,
            clientDataJson = assertion.rawClientDataJSON.toBase64Url(),
            authenticatorData = authenticatorData.toBase64Url(),
            signature = signature.toBase64Url(),
            userHandle = assertion.userID?.toBase64Url(),
            clientExtensionResultsJson = extensionResults.toString(),
            rawJson = rawJson,
        )
    }

    private fun <T : Any> T?.requireField(name: String): T =
        this ?: throw PasskeyException.Unexpected(IllegalStateException("$name is null"))

    private fun registrationExtensionResults(registration: ASAuthorizationPlatformPublicKeyCredentialRegistration): JsonObject =
        buildJsonObject {
            if (ApplePasskeyPlatform.supportsLargeBlob()) {
                registration.largeBlob?.let { put("largeBlob", buildJsonObject { put("supported", JsonPrimitive(it.isSupported)) }) }
            }
            if (ApplePasskeyPlatform.supportsPrf()) {
                registration.prf?.let { prf ->
                    put(
                        "prf",
                        buildJsonObject {
                            put("enabled", JsonPrimitive(prf.isSupported))
                            prf.first?.let { first ->
                                put(
                                    "results",
                                    buildJsonObject {
                                        put("first", JsonPrimitive(first.toBase64Url()))
                                        prf.second?.let { put("second", JsonPrimitive(it.toBase64Url())) }
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }

    private fun assertionExtensionResults(assertion: ASAuthorizationPlatformPublicKeyCredentialAssertion): JsonObject =
        buildJsonObject {
            if (ApplePasskeyPlatform.supportsLargeBlob()) {
                assertion.largeBlob?.let {
                    put(
                        "largeBlob",
                        buildJsonObject {
                            it.readData?.let { readData -> put("blob", JsonPrimitive(readData.toBase64Url())) }
                            put("written", JsonPrimitive(it.didWrite))
                        },
                    )
                }
            }
            if (ApplePasskeyPlatform.supportsPrf()) {
                assertion.prf?.let { prf ->
                    put(
                        "prf",
                        buildJsonObject {
                            put(
                                "results",
                                buildJsonObject {
                                    put("first", JsonPrimitive(prf.first.toBase64Url()))
                                    prf.second?.let { put("second", JsonPrimitive(it.toBase64Url())) }
                                },
                            )
                        },
                    )
                }
            }
        }

    // `attachment` only exists from iOS 16.6 / macOS 13.5; before that every
    // AuthenticationServices passkey is a platform credential.
    private fun ASAuthorizationPlatformPublicKeyCredentialRegistration.attachmentWireValue(): String =
        if (ApplePasskeyPlatform.supportsAttachmentReporting()) attachment.toWireValue() else "platform"

    private fun ASAuthorizationPlatformPublicKeyCredentialAssertion.attachmentWireValue(): String =
        if (ApplePasskeyPlatform.supportsAttachmentReporting()) attachment.toWireValue() else "platform"

    private fun ASAuthorizationPublicKeyCredentialAttachment.toWireValue(): String =
        when (this) {
            ASAuthorizationPublicKeyCredentialAttachment.ASAuthorizationPublicKeyCredentialAttachmentCrossPlatform ->
                "cross-platform"
            else -> "platform"
        }
}

internal fun NSData.toBase64Url(): String =
    base64Encoding().replace("+", "-").replace("/", "_").trimEnd('=')
