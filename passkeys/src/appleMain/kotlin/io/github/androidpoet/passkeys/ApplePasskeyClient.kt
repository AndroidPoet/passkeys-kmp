package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.NativePrfValues
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
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Shared AuthenticationServices passkey ceremony driver for every Apple
 * platform. iOS and macOS use the identical `ASAuthorization*` API; the only
 * differences — the presentation anchor type (`UIWindow` vs `NSWindow`) and the
 * source of the running OS version — are injected via [anchor] and the
 * [ApplePasskeyPlatform] expect/actual, so the ceremony logic lives here once.
 */
internal class ApplePasskeyClient(
    private val anchor: () -> ASPresentationAnchor,
    private val authorizationController: PasskeyAuthorizationController = PasskeyAuthorizationController(),
) : PasskeyClient {
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        runCatching {
            val nativeOptions = PasskeyPayloadMapper.nativeCreationOptions(options.requestJson)
            val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(nativeOptions.rp.id)
            val request =
                provider.createCredentialRegistrationRequestWithChallenge(
                    challenge = nativeOptions.challenge.toNSData(),
                    name = nativeOptions.user.name,
                    userID = nativeOptions.user.id.toNSData(),
                )
            nativeOptions.attestation?.let(request::setAttestationPreference)
            nativeOptions.user.displayName?.let { request.displayName = it }
            nativeOptions.authenticatorSelection?.userVerification?.let(request::setUserVerificationPreference)
            nativeOptions.extensions?.largeBlob?.support?.let { support ->
                requireFeature(ApplePasskeyPlatform.supportsLargeBlob(), "largeBlob registration")
                request.largeBlob =
                    ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput(
                        supportRequirement =
                            when (support) {
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
                requireFeature(ApplePasskeyPlatform.supportsPrf(), "PRF registration")
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
            request.allowedCredentials =
                nativeOptions.allowCredentials.map {
                    ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID = it.id.toNSData())
                }
            nativeOptions.userVerification?.let(request::setUserVerificationPreference)
            nativeOptions.extensions?.largeBlob?.let { largeBlob ->
                requireFeature(ApplePasskeyPlatform.supportsLargeBlob(), "largeBlob authentication")
                val operation =
                    if (largeBlob.write != null) {
                        ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
                            .ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationWrite
                    } else {
                        ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
                            .ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationRead
                    }
                request.largeBlob =
                    ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput(operation = operation).also {
                        largeBlob.write?.let { value -> it.dataToWrite = value.toNSData() }
                    }
            }
            nativeOptions.extensions?.prf?.eval?.let {
                requireFeature(ApplePasskeyPlatform.supportsPrf(), "PRF authentication")
                request.prf =
                    ASAuthorizationPublicKeyCredentialPRFAssertionInput(
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
            ): ASPresentationAnchor = anchor()
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

private fun requireFeature(available: Boolean, feature: String) {
    if (!available) {
        throw PasskeyException.Unsupported(
            IllegalStateException("$feature requires a newer ${ApplePasskeyPlatform.displayName} version"),
        )
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal fun String.toNSData(): NSData {
    val bytes = decodeBase64Url()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}

private fun NativePrfValues.toPrfValues(): ASAuthorizationPublicKeyCredentialPRFAssertionInputValues =
    ASAuthorizationPublicKeyCredentialPRFAssertionInputValues(
        saltInput1 = first.toNSData(),
        saltInput2 = second?.toNSData(),
    )
