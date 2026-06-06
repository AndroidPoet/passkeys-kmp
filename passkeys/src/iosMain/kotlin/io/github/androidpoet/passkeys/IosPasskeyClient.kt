package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.decodeBase64Url
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInputValues
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFRegistrationInput
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIDevice
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

private const val LARGE_BLOB_MIN_OS_VERSION = "17.0"
private const val PRF_MIN_OS_VERSION = "18.0"

public class IosPasskeyClient : PasskeyClient {
    private val window: UIWindow
    private val authorizationController: PasskeyAuthorizationController

    public constructor(window: UIWindow) : this(window, PasskeyAuthorizationController())

    internal constructor(
        window: UIWindow,
        authorizationController: PasskeyAuthorizationController,
    ) {
        this.window = window
        this.authorizationController = authorizationController
    }

    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        runCatching {
            val nativeOptions = PasskeyPayloadMapper.nativeCreationOptions(options.requestJson)
            val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(nativeOptions.rp.id)
            val request = provider.createCredentialRegistrationRequestWithChallenge(
                challenge = nativeOptions.challenge.toNSData(),
                name = nativeOptions.user.name,
                userID = nativeOptions.user.id.toNSData(),
            )
            nativeOptions.attestation?.let(request::setAttestationPreference)
            nativeOptions.user.displayName?.let { request.displayName = it }
            nativeOptions.authenticatorSelection?.userVerification?.let(request::setUserVerificationPreference)
            nativeOptions.extensions?.largeBlob?.support?.let { support ->
                requireIosVersion(LARGE_BLOB_MIN_OS_VERSION, "largeBlob registration")
                request.largeBlob = ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput(
                    supportRequirement = when (support) {
                        "required" ->
                            ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement
                                .ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirementRequired
                        else ->
                            ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement
                                .ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirementPreferred
                    },
                )
            }
            nativeOptions.extensions?.prf?.let { prf ->
                requireIosVersion(PRF_MIN_OS_VERSION, "PRF registration")
                request.prf = prf.eval?.let {
                    ASAuthorizationPublicKeyCredentialPRFRegistrationInput(inputValues = it.toPrfValues())
                } ?: ASAuthorizationPublicKeyCredentialPRFRegistrationInput.checkForSupport()
            }

            ASAuthorizationController(authorizationRequests = listOf(request)).also {
                it.setPresentationContextProvider(presentationContextProvider())
            }
        }.fold(
            onSuccess = { authorizationController.create(it) },
            onFailure = { PasskeyResult.Failure(PasskeyPayloadMapper.exception(it)) },
        )

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        runCatching {
            val nativeOptions = PasskeyPayloadMapper.nativeAuthenticationOptions(options.requestJson)
            val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(nativeOptions.rpId)
            val request = provider.createCredentialAssertionRequestWithChallenge(nativeOptions.challenge.toNSData())
            request.allowedCredentials = nativeOptions.allowCredentials.map {
                ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID = it.id.toNSData())
            }
            nativeOptions.userVerification?.let(request::setUserVerificationPreference)
            nativeOptions.extensions?.largeBlob?.let { largeBlob ->
                requireIosVersion(LARGE_BLOB_MIN_OS_VERSION, "largeBlob authentication")
                val operation = if (largeBlob.write != null) {
                    ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
                        .ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationWrite
                } else {
                    ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
                        .ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationRead
                }
                request.largeBlob = ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput(operation = operation).also {
                    largeBlob.write?.let { value -> it.dataToWrite = value.toNSData() }
                }
            }
            nativeOptions.extensions?.prf?.eval?.let {
                requireIosVersion(PRF_MIN_OS_VERSION, "PRF authentication")
                request.prf = ASAuthorizationPublicKeyCredentialPRFAssertionInput(
                    inputValues = it.toPrfValues(),
                    perCredentialInputValues = null,
                )
            }

            ASAuthorizationController(authorizationRequests = listOf(request)).also {
                it.setPresentationContextProvider(presentationContextProvider())
            }
        }.fold(
            onSuccess = { authorizationController.authenticate(it) },
            onFailure = { PasskeyResult.Failure(PasskeyPayloadMapper.exception(it)) },
        )

    private fun presentationContextProvider(): ASAuthorizationControllerPresentationContextProvidingProtocol =
        object : NSObject(), ASAuthorizationControllerPresentationContextProvidingProtocol {
            override fun presentationAnchorForAuthorizationController(
                controller: ASAuthorizationController,
            ): ASPresentationAnchor = window
        }

    private suspend fun PasskeyAuthorizationController.create(
        controller: ASAuthorizationController,
    ): PasskeyResult<PasskeyCreationResponse> =
        suspendCancellableCoroutine { continuation ->
            create(controller) { continuation.resume(it) }
        }

    private suspend fun PasskeyAuthorizationController.authenticate(
        controller: ASAuthorizationController,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        suspendCancellableCoroutine { continuation ->
            authenticate(controller) { continuation.resume(it) }
        }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun String.toNSData(): NSData {
    val bytes = decodeBase64Url()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}

private fun io.github.androidpoet.passkeys.internal.NativePrfValues.toPrfValues():
    ASAuthorizationPublicKeyCredentialPRFAssertionInputValues =
    ASAuthorizationPublicKeyCredentialPRFAssertionInputValues(
        saltInput1 = first.toNSData(),
        saltInput2 = second?.toNSData(),
    )

private fun requireIosVersion(minimum: String, feature: String) {
    if (!UIDevice.currentDevice.systemVersion.isSemanticVersionAtLeast(minimum)) {
        throw PasskeyException.Unsupported(
            IllegalStateException("$feature requires iOS $minimum or newer"),
        )
    }
}

internal fun String.isSemanticVersionAtLeast(minimum: String): Boolean {
    val currentParts = split('.').map { it.toIntOrNull() ?: 0 }
    val minimumParts = minimum.split('.').map { it.toIntOrNull() ?: 0 }
    val size = maxOf(currentParts.size, minimumParts.size)
    for (index in 0 until size) {
        val current = currentParts.getOrElse(index) { 0 }
        val required = minimumParts.getOrElse(index) { 0 }
        if (current != required) return current > required
    }
    return true
}
