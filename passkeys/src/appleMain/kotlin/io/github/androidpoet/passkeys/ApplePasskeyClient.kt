package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.NativeAuthenticationOptions
import io.github.androidpoet.passkeys.internal.NativeCreationOptions
import io.github.androidpoet.passkeys.internal.NativePrfValues
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.decodeBase64Url
import io.github.androidpoet.passkeys.internal.passkeyResult
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertionRequest
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyle
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFAssertionInputValues
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialPRFRegistrationInput
import platform.AuthenticationServices.ASAuthorizationRequest
import platform.AuthenticationServices.ASAuthorizationWebBrowserPlatformPublicKeyCredentialRegistrationRequestProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.create

private const val COSE_ES256 = -7L

/**
 * Shared AuthenticationServices passkey ceremony driver for every Apple
 * platform. iOS and macOS use the identical `ASAuthorization*` API; the only
 * differences — the presentation anchor type (`UIWindow` vs `NSWindow`) and the
 * source of the running OS version — are injected via [anchor] and the
 * [ApplePasskeyPlatform] expect/actual, so the ceremony logic lives here once.
 *
 * Only platform passkeys are driven (no security-key provider). Requests whose
 * constraints this client cannot honour — a `cross-platform` attachment, a
 * `pubKeyCredParams` list without ES256, or an extension the running OS lacks —
 * fail up front with [PasskeyException.Unsupported] instead of silently
 * producing a credential the server will reject.
 */
internal class ApplePasskeyClient(
    private val anchor: () -> ASPresentationAnchor,
) : PasskeyClient {
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        passkeyResult {
            val nativeOptions = PasskeyPayloadMapper.nativeCreationOptions(options.requestJson)
            val request = registrationRequest(nativeOptions, conditional = options.isConditionalCreateRequest)
            ApplePasskeyResponses.registration(run(request, requestPerformer(options.preferImmediatelyAvailableCredentials)))
        }

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        authenticate(options, requestPerformer(options.preferImmediatelyAvailableCredentials))

    /** Runs an assertion ceremony started by [perform] (modal sheet, immediate-only, or AutoFill-assisted). */
    internal suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
        perform: (ASAuthorizationController) -> Unit,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        passkeyResult {
            val nativeOptions = PasskeyPayloadMapper.nativeAuthenticationOptions(options.requestJson)
            val request = assertionRequest(nativeOptions)
            ApplePasskeyResponses.assertion(run(request, perform))
        }

    private suspend fun run(
        request: ASAuthorizationRequest,
        perform: (ASAuthorizationController) -> Unit,
    ) = PasskeyAuthorizationController(ASAuthorizationController(authorizationRequests = listOf(request)), anchor, perform).run()

    internal fun registrationRequest(
        nativeOptions: NativeCreationOptions,
        conditional: Boolean = false,
    ): ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest {
        requireSupported(nativeOptions.authenticatorSelection?.authenticatorAttachment != "cross-platform") {
            "only platform passkeys are supported on ${ApplePasskeyPlatform.displayName}; cross-platform (security key) requests are not"
        }
        requireSupported(nativeOptions.algorithms.isEmpty() || COSE_ES256 in nativeOptions.algorithms) {
            "${ApplePasskeyPlatform.displayName} passkeys are ES256 only, but pubKeyCredParams does not allow alg -7"
        }
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
        if (nativeOptions.excludeCredentials.isNotEmpty()) {
            requireFeature(ApplePasskeyPlatform.supportsExcludedCredentials(), "excludeCredentials")
            // `excludedCredentials` is declared on the web-browser request
            // protocol, which the registration request adopts via a category.
            (request as ASAuthorizationWebBrowserPlatformPublicKeyCredentialRegistrationRequestProtocol).excludedCredentials =
                nativeOptions.excludeCredentials.map {
                    ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID = it.id.toNSData())
                }
        }
        if (conditional) {
            // The counterpart of Credential Manager's conditional create: the
            // credential manager adds the passkey silently after a password
            // sign-in, or the request fails without showing any UI.
            requireFeature(ApplePasskeyPlatform.supportsConditionalRegistration(), "conditional registration")
            request.requestStyle =
                ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyle
                    .ASAuthorizationPlatformPublicKeyCredentialRegistrationRequestStyleConditional
        }
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
        return request
    }

    private fun assertionRequest(
        nativeOptions: NativeAuthenticationOptions,
    ): ASAuthorizationPlatformPublicKeyCredentialAssertionRequest {
        val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(nativeOptions.rpId)
        val request = provider.createCredentialAssertionRequestWithChallenge(nativeOptions.challenge.toNSData())
        request.allowedCredentials =
            nativeOptions.allowCredentials.map {
                ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID = it.id.toNSData())
            }
        nativeOptions.userVerification?.let(request::setUserVerificationPreference)
        nativeOptions.extensions?.largeBlob?.let { largeBlob ->
            // `{read: false}` with nothing to write is a no-op, not an implicit read.
            if (largeBlob.write == null && !largeBlob.read) return@let
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
        nativeOptions.extensions?.prf?.let { prf ->
            if (prf.eval == null && prf.evalByCredential.isEmpty()) return@let
            requireFeature(ApplePasskeyPlatform.supportsPrf(), "PRF authentication")
            val perCredential: Map<Any?, *>? =
                prf.evalByCredential
                    .takeIf { it.isNotEmpty() }
                    ?.entries
                    ?.associate<Map.Entry<String, NativePrfValues>, Any?, Any?> { (credentialId, values) ->
                        credentialId.toNSData() to values.toPrfValues()
                    }
            request.prf =
                ASAuthorizationPublicKeyCredentialPRFAssertionInput(
                    inputValues = prf.eval?.toPrfValues(),
                    perCredentialInputValues = perCredential,
                )
        }
        return request
    }
}

private inline fun requireSupported(condition: Boolean, message: () -> String) {
    if (!condition) throw PasskeyException.Unsupported(IllegalStateException(message()))
}

private fun requireFeature(available: Boolean, feature: String) {
    requireSupported(available) { "$feature requires a newer ${ApplePasskeyPlatform.displayName} version" }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal fun String.toNSData(): NSData {
    val bytes = decodeBase64Url()
    // addressOf(0) is invalid for an empty array; an empty value is a legal input
    // (e.g. an empty largeBlob write).
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}

private fun NativePrfValues.toPrfValues(): ASAuthorizationPublicKeyCredentialPRFAssertionInputValues =
    ASAuthorizationPublicKeyCredentialPRFAssertionInputValues(
        saltInput1 = first.toNSData(),
        saltInput2 = second?.toNSData(),
    )
