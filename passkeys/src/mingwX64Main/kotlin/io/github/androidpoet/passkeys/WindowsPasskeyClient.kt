@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.NativeAuthenticationOptions
import io.github.androidpoet.passkeys.internal.NativeCreationOptions
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.decodeBase64Url
import io.github.androidpoet.passkeys.internal.encodeBase64Url
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import platform.windows.HWND
import webauthn.GetConsoleWindow
import webauthn.GetForegroundWindow
import webauthn.WEBAUTHN_ASSERTION
import webauthn.WEBAUTHN_AUTHENTICATOR_GET_ASSERTION_OPTIONS
import webauthn.WEBAUTHN_AUTHENTICATOR_MAKE_CREDENTIAL_OPTIONS
import webauthn.WEBAUTHN_CLIENT_DATA
import webauthn.WEBAUTHN_COSE_CREDENTIAL_PARAMETER
import webauthn.WEBAUTHN_COSE_CREDENTIAL_PARAMETERS
import webauthn.WEBAUTHN_CREDENTIAL_ATTESTATION
import webauthn.WEBAUTHN_CREDENTIAL_EX
import webauthn.WEBAUTHN_CREDENTIAL_LIST
import webauthn.WEBAUTHN_RP_ENTITY_INFORMATION
import webauthn.WEBAUTHN_USER_ENTITY_INFORMATION
import webauthn.WebAuthNAuthenticatorGetAssertion
import webauthn.WebAuthNAuthenticatorMakeCredential
import webauthn.WebAuthNFreeAssertion
import webauthn.WebAuthNFreeCredentialAttestation
import webauthn.WebAuthNGetErrorName

/**
 * [PasskeyClient] for Windows desktop, backed by the OS WebAuthn API
 * (`webauthn.dll`, shipped with Windows 10 1903+).
 *
 * This drives the real Windows Hello experience: the system shows its passkey
 * sheet and the user authenticates with fingerprint, face, or PIN (platform
 * authenticator), or taps a USB/NFC security key. Nothing is bundled — the
 * binding links the OS-provided `webauthn.dll` import library.
 *
 * The native API needs a parent window. Pass the top-level window handle
 * (`HWND`, as a raw [Long]) so the system sheet is parented correctly; when 0,
 * it falls back to the foreground window and then the console window.
 *
 * The relying party's expected `origin` cannot be derived from the WebAuthn
 * options, so it defaults to `https://<rpId>`; pass [origin] to override.
 */
public class WindowsPasskeyClient(
    private val windowHandle: Long = 0L,
    private val origin: String? = null,
) : PasskeyClient {
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        runCatching {
            val opts = PasskeyPayloadMapper.nativeCreationOptions(options.requestJson)
            val clientData = clientDataJson("webauthn.create", opts.challenge, originFor(opts.rp.id))
            PasskeyPayloadMapper.creationResponse(makeCredential(opts, clientData))
        }.fold(
            onSuccess = { PasskeyResult.Success(it) },
            onFailure = { PasskeyResult.Failure(it.asPasskeyException()) },
        )

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        runCatching {
            val opts = PasskeyPayloadMapper.nativeAuthenticationOptions(options.requestJson)
            val clientData = clientDataJson("webauthn.get", opts.challenge, originFor(opts.rpId))
            PasskeyPayloadMapper.authenticationResponse(getAssertion(opts, clientData))
        }.fold(
            onSuccess = { PasskeyResult.Success(it) },
            onFailure = { PasskeyResult.Failure(it.asPasskeyException()) },
        )

    private fun originFor(rpId: String): String = origin ?: "https://$rpId"

    private fun Throwable.asPasskeyException(): PasskeyException =
        this as? PasskeyException ?: PasskeyPayloadMapper.exception(this)

    @Suppress("ThrowsCount") // each native step guards with a typed PasskeyException
    private fun makeCredential(opts: NativeCreationOptions, clientData: String): String =
        memScoped {
            val clientDataBytes = clientData.encodeToByteArray()

            val rp = alloc<WEBAUTHN_RP_ENTITY_INFORMATION>()
            rp.dwVersion = RP_INFO_VERSION
            rp.pwszId = wide(opts.rp.id)
            rp.pwszName = wide(opts.rp.name ?: opts.rp.id)
            rp.pwszIcon = null

            val userId = opts.user.id.decodeBase64Url()
            val user = alloc<WEBAUTHN_USER_ENTITY_INFORMATION>()
            user.dwVersion = USER_INFO_VERSION
            user.cbId = userId.size.toUInt()
            user.pbId = bytePtr(userId)
            user.pwszName = wide(opts.user.name)
            user.pwszIcon = null
            user.pwszDisplayName = wide(opts.user.displayName ?: opts.user.name)

            val coseParams = coseCredentialParameters()

            val clientDataStruct = alloc<WEBAUTHN_CLIENT_DATA>()
            clientDataStruct.dwVersion = CLIENT_DATA_VERSION
            clientDataStruct.cbClientDataJSON = clientDataBytes.size.toUInt()
            clientDataStruct.pbClientDataJSON = bytePtr(clientDataBytes)
            clientDataStruct.pwszHashAlgId = wide(HASH_ALG_SHA_256)

            val makeOptions = alloc<WEBAUTHN_AUTHENTICATOR_MAKE_CREDENTIAL_OPTIONS>()
            makeOptions.dwVersion = MAKE_CREDENTIAL_OPTIONS_VERSION
            makeOptions.dwTimeoutMilliseconds = TIMEOUT_MS
            makeOptions.CredentialList.cCredentials = 0u
            makeOptions.CredentialList.pCredentials = null
            makeOptions.Extensions.cExtensions = 0u
            makeOptions.Extensions.pExtensions = null
            makeOptions.dwAuthenticatorAttachment = ATTACHMENT_ANY
            makeOptions.bRequireResidentKey = FALSE
            makeOptions.dwUserVerificationRequirement =
                userVerification(opts.authenticatorSelection?.userVerification)
            makeOptions.dwAttestationConveyancePreference = attestationPreference(opts.attestation)
            makeOptions.dwFlags = 0u
            makeOptions.pCancellationId = null
            makeOptions.pExcludeCredentialList = null

            val out = allocPointerTo<WEBAUTHN_CREDENTIAL_ATTESTATION>()
            val hr =
                WebAuthNAuthenticatorMakeCredential(
                    resolveWindow(),
                    rp.ptr,
                    user.ptr,
                    coseParams.ptr,
                    clientDataStruct.ptr,
                    makeOptions.ptr,
                    out.ptr,
                )
            val attestation = out.value ?: throw hrError(hr)
            try {
                if (hr != S_OK) throw hrError(hr)
                val att = attestation.pointed
                val responseJson = att.pbRegistrationResponseJSON.bytesOrEmpty(att.cbRegistrationResponseJSON)
                if (att.dwVersion.toInt() >= CREDENTIAL_ATTESTATION_VERSION_8 && responseJson.isNotEmpty()) {
                    responseJson.decodeToString()
                } else {
                    registrationJson(
                        credentialId = att.pbCredentialId.bytesOrEmpty(att.cbCredentialId),
                        attestationObject = att.pbAttestationObject.bytesOrEmpty(att.cbAttestationObject),
                        clientData = clientDataBytes,
                        transports = transports(att.dwVersion.toInt(), att.dwUsedTransport),
                    )
                }
            } finally {
                WebAuthNFreeCredentialAttestation(attestation)
            }
        }

    @Suppress("ThrowsCount") // each native step guards with a typed PasskeyException
    private fun getAssertion(opts: NativeAuthenticationOptions, clientData: String): String =
        memScoped {
            val clientDataBytes = clientData.encodeToByteArray()

            val clientDataStruct = alloc<WEBAUTHN_CLIENT_DATA>()
            clientDataStruct.dwVersion = CLIENT_DATA_VERSION
            clientDataStruct.cbClientDataJSON = clientDataBytes.size.toUInt()
            clientDataStruct.pbClientDataJSON = bytePtr(clientDataBytes)
            clientDataStruct.pwszHashAlgId = wide(HASH_ALG_SHA_256)

            val getOptions = alloc<WEBAUTHN_AUTHENTICATOR_GET_ASSERTION_OPTIONS>()
            getOptions.dwVersion = GET_ASSERTION_OPTIONS_VERSION
            getOptions.dwTimeoutMilliseconds = TIMEOUT_MS
            getOptions.CredentialList.cCredentials = 0u
            getOptions.CredentialList.pCredentials = null
            getOptions.Extensions.cExtensions = 0u
            getOptions.Extensions.pExtensions = null
            getOptions.dwAuthenticatorAttachment = ATTACHMENT_ANY
            getOptions.dwUserVerificationRequirement = userVerification(opts.userVerification)
            getOptions.dwFlags = 0u
            getOptions.pwszU2fAppId = null
            getOptions.pbU2fAppId = null
            getOptions.pCancellationId = null
            getOptions.pAllowCredentialList = allowCredentialList(opts)

            val out = allocPointerTo<WEBAUTHN_ASSERTION>()
            val hr =
                WebAuthNAuthenticatorGetAssertion(
                    resolveWindow(),
                    opts.rpId,
                    clientDataStruct.ptr,
                    getOptions.ptr,
                    out.ptr,
                )
            val assertionPtr = out.value ?: throw hrError(hr)
            try {
                if (hr != S_OK) throw hrError(hr)
                val assertion = assertionPtr.pointed
                val responseJson = assertion.pbAuthenticationResponseJSON.bytesOrEmpty(assertion.cbAuthenticationResponseJSON)
                if (assertion.dwVersion.toInt() >= ASSERTION_VERSION_6 && responseJson.isNotEmpty()) {
                    responseJson.decodeToString()
                } else {
                    assertionJson(
                        credentialId = assertion.Credential.pbId.bytesOrEmpty(assertion.Credential.cbId),
                        clientData = clientDataBytes,
                        authenticatorData = assertion.pbAuthenticatorData.bytesOrEmpty(assertion.cbAuthenticatorData),
                        signature = assertion.pbSignature.bytesOrEmpty(assertion.cbSignature),
                        userHandle = assertion.pbUserId.bytesOrEmpty(assertion.cbUserId),
                    )
                }
            } finally {
                WebAuthNFreeAssertion(assertionPtr)
            }
        }

    // The system passkey sheet must be parented to a window. Prefer the
    // caller-supplied handle, then the foreground window, then the console.
    private fun resolveWindow(): HWND? =
        if (windowHandle != 0L) windowHandle.toCPointer() else GetForegroundWindow() ?: GetConsoleWindow()

    private fun AutofreeScope.coseCredentialParameters(): WEBAUTHN_COSE_CREDENTIAL_PARAMETERS {
        val params = allocArray<WEBAUTHN_COSE_CREDENTIAL_PARAMETER>(COSE_ALGORITHMS.size)
        COSE_ALGORITHMS.forEachIndexed { index, alg ->
            params[index].dwVersion = COSE_CREDENTIAL_PARAMETER_VERSION
            params[index].pwszCredentialType = wide(CREDENTIAL_TYPE_PUBLIC_KEY)
            params[index].lAlg = alg
        }
        val coseParams = alloc<WEBAUTHN_COSE_CREDENTIAL_PARAMETERS>()
        coseParams.cCredentialParameters = COSE_ALGORITHMS.size.toUInt()
        coseParams.pCredentialParameters = params
        return coseParams
    }

    private fun AutofreeScope.allowCredentialList(
        opts: NativeAuthenticationOptions,
    ): CPointer<WEBAUTHN_CREDENTIAL_LIST>? {
        val descriptors = opts.allowCredentials
        if (descriptors.isEmpty()) return null
        val entries = allocArray<WEBAUTHN_CREDENTIAL_EX>(descriptors.size)
        val pointers = allocArray<CPointerVar<WEBAUTHN_CREDENTIAL_EX>>(descriptors.size)
        descriptors.forEachIndexed { index, descriptor ->
            val id = descriptor.id.decodeBase64Url()
            entries[index].dwVersion = CREDENTIAL_EX_VERSION
            entries[index].cbId = id.size.toUInt()
            entries[index].pbId = bytePtr(id)
            entries[index].pwszCredentialType = wide(CREDENTIAL_TYPE_PUBLIC_KEY)
            entries[index].dwTransports = 0u
            pointers[index] = entries[index].ptr
        }
        val list = alloc<WEBAUTHN_CREDENTIAL_LIST>()
        list.cCredentials = descriptors.size.toUInt()
        list.ppCredentials = pointers
        return list.ptr
    }

    // --- WebAuthn JSON assembly (base64url, server-ready) ---

    private fun clientDataJson(type: String, challengeBase64Url: String, origin: String): String =
        buildJsonObject {
            put("type", type)
            put("challenge", challengeBase64Url)
            put("origin", origin)
            put("crossOrigin", false)
        }.toString()

    private fun registrationJson(
        credentialId: ByteArray,
        attestationObject: ByteArray,
        clientData: ByteArray,
        transports: List<String>,
    ): String =
        buildJsonObject {
            put("id", credentialId.encodeBase64Url())
            put("rawId", credentialId.encodeBase64Url())
            put("type", "public-key")
            putJsonObject("response") {
                put("clientDataJSON", clientData.encodeBase64Url())
                put("attestationObject", attestationObject.encodeBase64Url())
                putJsonArray("transports") { transports.forEach { add(it) } }
            }
        }.toString()

    private fun assertionJson(
        credentialId: ByteArray,
        clientData: ByteArray,
        authenticatorData: ByteArray,
        signature: ByteArray,
        userHandle: ByteArray,
    ): String =
        buildJsonObject {
            put("id", credentialId.encodeBase64Url())
            put("rawId", credentialId.encodeBase64Url())
            put("type", "public-key")
            putJsonObject("response") {
                put("clientDataJSON", clientData.encodeBase64Url())
                put("authenticatorData", authenticatorData.encodeBase64Url())
                put("signature", signature.encodeBase64Url())
                if (userHandle.isNotEmpty()) put("userHandle", userHandle.encodeBase64Url())
            }
        }.toString()

    private fun userVerification(value: String?): UInt =
        when (value) {
            "required" -> UV_REQUIRED
            "discouraged" -> UV_DISCOURAGED
            "preferred" -> UV_PREFERRED
            else -> UV_ANY
        }

    private fun attestationPreference(value: String?): UInt =
        when (value) {
            "none" -> ATTESTATION_NONE
            "indirect" -> ATTESTATION_INDIRECT
            "direct" -> ATTESTATION_DIRECT
            else -> ATTESTATION_ANY
        }

    private fun transports(version: Int, usedTransport: UInt): List<String> {
        if (version < CREDENTIAL_ATTESTATION_VERSION_3 || usedTransport == 0u) return listOf("internal")
        return buildList {
            if (usedTransport and TRANSPORT_USB != 0u) add("usb")
            if (usedTransport and TRANSPORT_NFC != 0u) add("nfc")
            if (usedTransport and TRANSPORT_BLE != 0u) add("ble")
            if (usedTransport and TRANSPORT_INTERNAL != 0u) add("internal")
            if (usedTransport and TRANSPORT_HYBRID != 0u) add("hybrid")
        }.ifEmpty { listOf("internal") }
    }

    private fun hrError(hr: Int): PasskeyException {
        val name = WebAuthNGetErrorName(hr)?.toKStringFromUtf16() ?: "UnknownError"
        val message = "WebAuthn failed: $name (hr=0x${hr.toUInt().toString(HEX_RADIX)})"
        return when (name) {
            "NotAllowedError" -> PasskeyException.UserCanceled(IllegalStateException(message))
            "InvalidStateError", "ConstraintError" -> PasskeyException.DomError(message)
            "NotSupportedError" -> PasskeyException.Unsupported(IllegalStateException(message))
            else -> PasskeyException.Unexpected(IllegalStateException(message))
        }
    }

    private fun CPointer<UByteVar>?.bytesOrEmpty(length: UInt): ByteArray =
        if (this == null || length == 0u) ByteArray(0) else reinterpret<ByteVar>().readBytes(length.toInt())

    private fun AutofreeScope.bytePtr(bytes: ByteArray): CPointer<UByteVar>? {
        if (bytes.isEmpty()) return null
        val buffer = allocArray<UByteVar>(bytes.size)
        bytes.forEachIndexed { index, byte -> buffer[index] = byte.toUByte() }
        return buffer
    }

    private fun AutofreeScope.wide(value: String): CPointer<UShortVar> = value.wcstr.getPointer(this)

    private companion object {
        private const val S_OK = 0
        private const val FALSE = 0
        private const val HEX_RADIX = 16
        private const val TIMEOUT_MS = 60_000u

        private const val RP_INFO_VERSION = 1u
        private const val USER_INFO_VERSION = 1u
        private const val CLIENT_DATA_VERSION = 1u
        private const val COSE_CREDENTIAL_PARAMETER_VERSION = 1u
        private const val CREDENTIAL_EX_VERSION = 1u

        // Conservative option struct versions: VERSION_3 (make) / VERSION_4 (get)
        // are present on every webauthn.dll since Windows 10 1903 and cover all
        // fields this client sets (allow/exclude lists, UV, attachment).
        private const val MAKE_CREDENTIAL_OPTIONS_VERSION = 3u
        private const val GET_ASSERTION_OPTIONS_VERSION = 4u

        // Output struct versions that first carry the ready-made response JSON.
        private const val CREDENTIAL_ATTESTATION_VERSION_3 = 3
        private const val CREDENTIAL_ATTESTATION_VERSION_8 = 8
        private const val ASSERTION_VERSION_6 = 6

        private const val HASH_ALG_SHA_256 = "SHA-256"
        private const val CREDENTIAL_TYPE_PUBLIC_KEY = "public-key"

        // COSE algorithm identifiers: ES256 (-7) and RS256 (-257).
        private val COSE_ALGORITHMS = intArrayOf(-7, -257)

        private const val ATTACHMENT_ANY = 0u

        private const val UV_ANY = 0u
        private const val UV_REQUIRED = 1u
        private const val UV_PREFERRED = 2u
        private const val UV_DISCOURAGED = 3u

        private const val ATTESTATION_ANY = 0u
        private const val ATTESTATION_NONE = 1u
        private const val ATTESTATION_INDIRECT = 2u
        private const val ATTESTATION_DIRECT = 3u

        private const val TRANSPORT_USB = 0x00000001u
        private const val TRANSPORT_NFC = 0x00000002u
        private const val TRANSPORT_BLE = 0x00000004u
        private const val TRANSPORT_INTERNAL = 0x00000010u
        private const val TRANSPORT_HYBRID = 0x00000020u
    }
}
