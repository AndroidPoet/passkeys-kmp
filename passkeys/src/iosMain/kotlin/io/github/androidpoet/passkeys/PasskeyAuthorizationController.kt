package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttachment
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.base64Encoding
import platform.UIKit.UIDevice
import platform.darwin.NSObject

private const val ATTACHMENT_SUPPORT_MIN_OS_VERSION = "16.6"
private const val PASSKEY_TYPE = "public-key"

internal class PasskeyAuthorizationController {
    private lateinit var controller: ASAuthorizationController
    private var createCompletion: ((PasskeyResult<PasskeyCreationResponse>) -> Unit)? = null
    private var authenticateCompletion: ((PasskeyResult<PasskeyAuthenticationResponse>) -> Unit)? = null

    public fun create(
        controller: ASAuthorizationController,
        completion: (PasskeyResult<PasskeyCreationResponse>) -> Unit,
    ) {
        this.controller = controller
        createCompletion = completion
        this.controller.delegate = createDelegate
        this.controller.performRequests()
    }

    public fun authenticate(
        controller: ASAuthorizationController,
        completion: (PasskeyResult<PasskeyAuthenticationResponse>) -> Unit,
    ) {
        this.controller = controller
        authenticateCompletion = completion
        this.controller.delegate = authenticateDelegate
        this.controller.performRequests()
    }

    private val createDelegate = object : NSObject(), ASAuthorizationControllerDelegateProtocol {
        override fun authorizationController(
            controller: ASAuthorizationController,
            didCompleteWithAuthorization: ASAuthorization,
        ) {
            val registration = didCompleteWithAuthorization.credential
                as ASAuthorizationPlatformPublicKeyCredentialRegistration
            val attestationObject = registration.rawAttestationObject

            if (attestationObject == null) {
                createCompletion?.invoke(
                    PasskeyResult.Failure(
                        PasskeyException.Unexpected(NullPointerException("rawAttestationObject is null")),
                    ),
                )
                return
            }

            val response = PasskeyCreationResponse(
                id = registration.credentialID.toBase64Url(),
                rawId = registration.credentialID.toBase64Url(),
                type = PASSKEY_TYPE,
                authenticatorAttachment = registration.attachment.toWireValue(),
                attestationObject = attestationObject.toBase64Url(),
                clientDataJson = registration.rawClientDataJSON.toBase64Url(),
                transports = listOf("internal"),
                clientExtensionResultsJson = creationExtensionResults(registration)?.toString(),
                rawJson = creationRawJson(registration, attestationObject),
            )

            createCompletion?.invoke(PasskeyResult.Success(response))
        }

        override fun authorizationController(
            controller: ASAuthorizationController,
            didCompleteWithError: NSError,
        ) {
            createCompletion?.invoke(PasskeyResult.Failure(didCompleteWithError.toPasskeyException()))
        }
    }

    private val authenticateDelegate = object : NSObject(), ASAuthorizationControllerDelegateProtocol {
        override fun authorizationController(
            controller: ASAuthorizationController,
            didCompleteWithAuthorization: ASAuthorization,
        ) {
            val assertion = didCompleteWithAuthorization.credential
                as ASAuthorizationPlatformPublicKeyCredentialAssertion
            val response = PasskeyAuthenticationResponse(
                id = assertion.credentialID.toBase64Url(),
                rawId = assertion.credentialID.toBase64Url(),
                type = PASSKEY_TYPE,
                authenticatorAttachment = assertion.attachment.toWireValue(),
                clientDataJson = assertion.rawClientDataJSON.toBase64Url(),
                authenticatorData = assertion.rawAuthenticatorData?.toBase64Url(),
                signature = assertion.signature?.toBase64Url(),
                userHandle = assertion.userID?.toBase64Url(),
                clientExtensionResultsJson = authenticationExtensionResults(assertion)?.toString(),
                rawJson = authenticationRawJson(assertion),
            )

            authenticateCompletion?.invoke(PasskeyResult.Success(response))
        }

        override fun authorizationController(
            controller: ASAuthorizationController,
            didCompleteWithError: NSError,
        ) {
            authenticateCompletion?.invoke(PasskeyResult.Failure(didCompleteWithError.toPasskeyException()))
        }
    }

    private fun creationRawJson(
        registration: ASAuthorizationPlatformPublicKeyCredentialRegistration,
        attestationObject: NSData,
    ): String =
        buildJsonObject {
            put("id", JsonPrimitive(registration.credentialID.toBase64Url()))
            put("rawId", JsonPrimitive(registration.credentialID.toBase64Url()))
            put("type", JsonPrimitive(PASSKEY_TYPE))
            registration.attachment.toWireValue()?.let { put("authenticatorAttachment", JsonPrimitive(it)) }
            creationExtensionResults(registration)?.let { put("clientExtensionResults", it) }
            put(
                "response",
                buildJsonObject {
                    put("attestationObject", JsonPrimitive(attestationObject.toBase64Url()))
                    put("clientDataJSON", JsonPrimitive(registration.rawClientDataJSON.toBase64Url()))
                    put("transports", JsonArray(listOf(JsonPrimitive("internal"))))
                },
            )
        }.toString()

    private fun authenticationRawJson(assertion: ASAuthorizationPlatformPublicKeyCredentialAssertion): String =
        buildJsonObject {
            put("id", JsonPrimitive(assertion.credentialID.toBase64Url()))
            put("rawId", JsonPrimitive(assertion.credentialID.toBase64Url()))
            put("type", JsonPrimitive(PASSKEY_TYPE))
            assertion.attachment.toWireValue()?.let { put("authenticatorAttachment", JsonPrimitive(it)) }
            authenticationExtensionResults(assertion)?.let { put("clientExtensionResults", it) }
            put(
                "response",
                buildJsonObject {
                    put("clientDataJSON", JsonPrimitive(assertion.rawClientDataJSON.toBase64Url()))
                    assertion.rawAuthenticatorData?.let { put("authenticatorData", JsonPrimitive(it.toBase64Url())) }
                    assertion.signature?.let { put("signature", JsonPrimitive(it.toBase64Url())) }
                    assertion.userID?.let { put("userHandle", JsonPrimitive(it.toBase64Url())) }
                },
            )
        }.toString()

    private fun creationExtensionResults(
        registration: ASAuthorizationPlatformPublicKeyCredentialRegistration,
    ): JsonObject? {
        val largeBlob = registration.largeBlob
        val prf = registration.prf
        if (largeBlob == null && prf == null) return null

        return buildJsonObject {
            largeBlob?.let {
                put(
                    "largeBlob",
                    buildJsonObject {
                        put("supported", JsonPrimitive(it.isSupported))
                    },
                )
            }
            prf?.let {
                put(
                    "prf",
                    buildJsonObject {
                        put("enabled", JsonPrimitive(it.isSupported))
                    },
                )
            }
        }
    }

    private fun authenticationExtensionResults(
        assertion: ASAuthorizationPlatformPublicKeyCredentialAssertion,
    ): JsonObject? {
        val largeBlob = assertion.largeBlob
        val prf = assertion.prf
        if (largeBlob == null && prf == null) return null

        return buildJsonObject {
            largeBlob?.let {
                put(
                    "largeBlob",
                    buildJsonObject {
                        it.readData?.let { readData -> put("blob", JsonPrimitive(readData.toBase64Url())) }
                        put("written", JsonPrimitive(it.didWrite))
                    },
                )
            }
            prf?.let {
                put(
                    "prf",
                    buildJsonObject {
                        put(
                            "results",
                            buildJsonObject {
                                put("first", JsonPrimitive(it.first.toBase64Url()))
                                it.second?.let { second -> put("second", JsonPrimitive(second.toBase64Url())) }
                            },
                        )
                    },
                )
            }
        }
    }

    private fun ASAuthorizationPublicKeyCredentialAttachment.toWireValue(): String? {
        if (!UIDevice.currentDevice.systemVersion.isSemanticVersionAtLeast(ATTACHMENT_SUPPORT_MIN_OS_VERSION)) {
            return "platform"
        }
        return when (this) {
            ASAuthorizationPublicKeyCredentialAttachment.ASAuthorizationPublicKeyCredentialAttachmentCrossPlatform ->
                "cross-platform"
            else -> "platform"
        }
    }
}

internal fun NSData.toBase64Url(): String =
    base64Encoding().replace("+", "-").replace("/", "_").trimEnd('=')
