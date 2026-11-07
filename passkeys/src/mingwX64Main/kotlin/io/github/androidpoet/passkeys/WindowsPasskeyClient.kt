@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.internal.NativeAuthenticationOptions
import io.github.androidpoet.passkeys.internal.NativeCreationOptions
import io.github.androidpoet.passkeys.internal.NativeCredentialDescriptor
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.decodeBase64Url
import io.github.androidpoet.passkeys.internal.encodeBase64Url
import io.github.androidpoet.passkeys.internal.passkeyResult
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
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import platform.posix.GUID
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
import webauthn.WebAuthNCancelCurrentOperation
import webauthn.WebAuthNFreeAssertion
import webauthn.WebAuthNFreeCredentialAttestation
import webauthn.WebAuthNGetCancellationId
import webauthn.WebAuthNGetErrorName
import kotlin.coroutines.cancellation.CancellationException

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
 * The server's `pubKeyCredParams`, `authenticatorSelection` (attachment,
 * resident key, user verification), `excludeCredentials` and `timeout` are all
 * forwarded. The blocking native call runs on [Dispatchers.IO]; cancelling the
 * calling coroutine cancels the system sheet.
 *
 * A native client has no browser to establish the origin: `clientDataJSON.origin`
 * is `https://<rpId>` unless [origin] is given. Your server must list that
 * origin, and it is your app's job to only run options for your own RP.
 */
public class WindowsPasskeyClient(
    private val windowHandle: Long = 0L,
    private val origin: String? = null,
) : PasskeyClient {
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        passkeyResult {
            val opts = PasskeyPayloadMapper.nativeCreationOptions(options.requestJson)
            val clientData = clientDataJson("webauthn.create", opts.challenge, originFor(opts.rp.id))
            val raw = cancellable { cancellationId -> makeCredential(opts, clientData, cancellationId) }
            PasskeyPayloadMapper.creationResponse(raw)
        }

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        passkeyResult {
            val opts = PasskeyPayloadMapper.nativeAuthenticationOptions(options.requestJson)
            val clientData = clientDataJson("webauthn.get", opts.challenge, originFor(opts.rpId))
            val raw = cancellable { cancellationId -> getAssertion(opts, clientData, cancellationId) }
            PasskeyPayloadMapper.authenticationResponse(raw)
        }

    private fun originFor(rpId: String): String = origin ?: "https://$rpId"

    // Runs the blocking native ceremony off the caller's thread and, if the
    // caller is cancelled meanwhile, asks webauthn.dll to abort it so the system
    // sheet closes and the worker returns promptly.
    private suspend fun <T> cancellable(block: (CPointer<GUID>?) -> T): T {
        val cancellationId = nativeHeap.alloc<GUID>()
        val id: CPointer<GUID>? = if (WebAuthNGetCancellationId(cancellationId.ptr) == S_OK) cancellationId.ptr else null
        try {
            return coroutineScope {
                val worker = async(Dispatchers.IO) { block(id) }
                try {
                    worker.await()
                } catch (e: CancellationException) {
                    id?.let { WebAuthNCancelCurrentOperation(it) }
                    throw e
                }
            }
        } finally {
            nativeHeap.free(cancellationId)
        }
    }

    @Suppress("ThrowsCount") // each native step guards with a typed PasskeyException
    private fun makeCredential(opts: NativeCreationOptions, clientData: String, cancellationId: CPointer<GUID>?): String =
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

            val coseParams = coseCredentialParameters(opts.algorithms.ifEmpty { DEFAULT_COSE_ALGORITHMS })

            val clientDataStruct = alloc<WEBAUTHN_CLIENT_DATA>()
            clientDataStruct.dwVersion = CLIENT_DATA_VERSION
            clientDataStruct.cbClientDataJSON = clientDataBytes.size.toUInt()
            clientDataStruct.pbClientDataJSON = bytePtr(clientDataBytes)
            clientDataStruct.pwszHashAlgId = wide(HASH_ALG_SHA_256)

            val makeOptions = alloc<WEBAUTHN_AUTHENTICATOR_MAKE_CREDENTIAL_OPTIONS>()
            makeOptions.dwVersion = MAKE_CREDENTIAL_OPTIONS_VERSION
            makeOptions.dwTimeoutMilliseconds = timeoutMillis(opts.timeout)
            makeOptions.CredentialList.cCredentials = 0u
            makeOptions.CredentialList.pCredentials = null
            makeOptions.Extensions.cExtensions = 0u
            makeOptions.Extensions.pExtensions = null
            makeOptions.dwAuthenticatorAttachment = attachment(opts.authenticatorSelection?.authenticatorAttachment)
            makeOptions.bRequireResidentKey = if (opts.requiresDiscoverableCredential) TRUE else FALSE
            makeOptions.dwUserVerificationRequirement =
                userVerification(opts.authenticatorSelection?.userVerification)
            makeOptions.dwAttestationConveyancePreference = attestationPreference(opts.attestation)
            makeOptions.dwFlags = 0u
            makeOptions.pCancellationId = cancellationId
            makeOptions.pExcludeCredentialList = credentialList(opts.excludeCredentials)

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
            val attestation = out.value ?: throw hrError(hr, create = true)
            try {
                if (hr != S_OK) throw hrError(hr, create = true)
                val att = attestation.pointed
                // Only read fields the returned struct version actually has:
                // the ready-made response JSON exists from VERSION_8 onwards.
                val version = att.dwVersion.toInt()
                val responseJson =
                    if (version >= CREDENTIAL_ATTESTATION_VERSION_8) {
                        att.pbRegistrationResponseJSON.bytesOrEmpty(att.cbRegistrationResponseJSON)
                    } else {
                        ByteArray(0)
                    }
                if (responseJson.isNotEmpty()) {
                    responseJson.decodeToString()
                } else {
                    registrationJson(
                        credentialId = att.pbCredentialId.bytesOrEmpty(att.cbCredentialId),
                        attestationObject = att.pbAttestationObject.bytesOrEmpty(att.cbAttestationObject),
                        clientData = clientDataBytes,
                        transports = transports(version, if (version >= CREDENTIAL_ATTESTATION_VERSION_3) att.dwUsedTransport else 0u),
                    )
                }
            } finally {
                WebAuthNFreeCredentialAttestation(attestation)
            }
        }

    @Suppress("ThrowsCount") // each native step guards with a typed PasskeyException
    private fun getAssertion(opts: NativeAuthenticationOptions, clientData: String, cancellationId: CPointer<GUID>?): String =
        memScoped {
            val clientDataBytes = clientData.encodeToByteArray()

            val clientDataStruct = alloc<WEBAUTHN_CLIENT_DATA>()
            clientDataStruct.dwVersion = CLIENT_DATA_VERSION
            clientDataStruct.cbClientDataJSON = clientDataBytes.size.toUInt()
            clientDataStruct.pbClientDataJSON = bytePtr(clientDataBytes)
            clientDataStruct.pwszHashAlgId = wide(HASH_ALG_SHA_256)

            val getOptions = alloc<WEBAUTHN_AUTHENTICATOR_GET_ASSERTION_OPTIONS>()
            getOptions.dwVersion = GET_ASSERTION_OPTIONS_VERSION
            getOptions.dwTimeoutMilliseconds = timeoutMillis(opts.timeout)
            getOptions.CredentialList.cCredentials = 0u
            getOptions.CredentialList.pCredentials = null
            getOptions.Extensions.cExtensions = 0u
            getOptions.Extensions.pExtensions = null
            getOptions.dwAuthenticatorAttachment = ATTACHMENT_ANY
            getOptions.dwUserVerificationRequirement = userVerification(opts.userVerification)
            getOptions.dwFlags = 0u
            getOptions.pwszU2fAppId = null
            getOptions.pbU2fAppId = null
            getOptions.pCancellationId = cancellationId
            getOptions.pAllowCredentialList = credentialList(opts.allowCredentials)

            val out = allocPointerTo<WEBAUTHN_ASSERTION>()
            val hr =
                WebAuthNAuthenticatorGetAssertion(
                    resolveWindow(),
                    opts.rpId,
                    clientDataStruct.ptr,
                    getOptions.ptr,
                    out.ptr,
                )
            val assertionPtr = out.value ?: throw hrError(hr, create = false)
            try {
                if (hr != S_OK) throw hrError(hr, create = false)
                val assertion = assertionPtr.pointed
                val version = assertion.dwVersion.toInt()
                val responseJson =
                    if (version >= ASSERTION_VERSION_6) {
                        assertion.pbAuthenticationResponseJSON.bytesOrEmpty(assertion.cbAuthenticationResponseJSON)
                    } else {
                        ByteArray(0)
                    }
                if (responseJson.isNotEmpty()) {
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

    private fun AutofreeScope.coseCredentialParameters(algorithms: List<Long>): WEBAUTHN_COSE_CREDENTIAL_PARAMETERS {
        val params = allocArray<WEBAUTHN_COSE_CREDENTIAL_PARAMETER>(algorithms.size)
        algorithms.forEachIndexed { index, alg ->
            params[index].dwVersion = COSE_CREDENTIAL_PARAMETER_VERSION
            params[index].pwszCredentialType = wide(CREDENTIAL_TYPE_PUBLIC_KEY)
            params[index].lAlg = alg.toInt()
        }
        val coseParams = alloc<WEBAUTHN_COSE_CREDENTIAL_PARAMETERS>()
        coseParams.cCredentialParameters = algorithms.size.toUInt()
        coseParams.pCredentialParameters = params
        return coseParams
    }

    private fun AutofreeScope.credentialList(
        descriptors: List<NativeCredentialDescriptor>,
    ): CPointer<WEBAUTHN_CREDENTIAL_LIST>? {
        if (descriptors.isEmpty()) return null
        val entries = allocArray<WEBAUTHN_CREDENTIAL_EX>(descriptors.size)
        val pointers = allocArray<CPointerVar<WEBAUTHN_CREDENTIAL_EX>>(descriptors.size)
        descriptors.forEachIndexed { index, descriptor ->
            val id = descriptor.id.decodeBase64Url()
            entries[index].dwVersion = CREDENTIAL_EX_VERSION
            entries[index].cbId = id.size.toUInt()
            entries[index].pbId = bytePtr(id)
            entries[index].pwszCredentialType = wide(CREDENTIAL_TYPE_PUBLIC_KEY)
            entries[index].dwTransports = transportMask(descriptor.transports)
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
            putJsonObject("clientExtensionResults") {}
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
            putJsonObject("clientExtensionResults") {}
            putJsonObject("response") {
                put("clientDataJSON", clientData.encodeBase64Url())
                put("authenticatorData", authenticatorData.encodeBase64Url())
                put("signature", signature.encodeBase64Url())
                if (userHandle.isNotEmpty()) put("userHandle", userHandle.encodeBase64Url())
            }
        }.toString()

    private fun timeoutMillis(timeout: Long?): UInt =
        timeout?.takeIf { it > 0 }?.coerceAtMost(UInt.MAX_VALUE.toLong())?.toUInt() ?: DEFAULT_TIMEOUT_MS

    private fun userVerification(value: String?): UInt =
        when (value) {
            "required" -> UV_REQUIRED
            "discouraged" -> UV_DISCOURAGED
            "preferred" -> UV_PREFERRED
            else -> UV_ANY
        }

    private fun attachment(value: String?): UInt =
        when (value) {
            "platform" -> ATTACHMENT_PLATFORM
            "cross-platform" -> ATTACHMENT_CROSS_PLATFORM
            else -> ATTACHMENT_ANY
        }

    private fun attestationPreference(value: String?): UInt =
        when (value) {
            "none" -> ATTESTATION_NONE
            "indirect" -> ATTESTATION_INDIRECT
            "direct" -> ATTESTATION_DIRECT
            else -> ATTESTATION_ANY
        }

    private fun transportMask(transports: List<String>): UInt =
        transports.fold(0u) { mask, transport ->
            mask or
                when (transport) {
                    "usb" -> TRANSPORT_USB
                    "nfc" -> TRANSPORT_NFC
                    "ble" -> TRANSPORT_BLE
                    "internal" -> TRANSPORT_INTERNAL
                    "hybrid" -> TRANSPORT_HYBRID
                    else -> 0u
                }
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

    private fun hrError(hr: Int, create: Boolean): PasskeyException {
        val name = WebAuthNGetErrorName(hr)?.toKStringFromUtf16() ?: "UnknownError"
        val message = "WebAuthn failed: $name (hr=0x${hr.toUInt().toString(HEX_RADIX)})"
        val cause = IllegalStateException(message)
        return when (name) {
            "NotAllowedError" -> PasskeyException.UserCanceled(cause)
            // On create, InvalidStateError means an excludeCredentials entry matched.
            "InvalidStateError" -> if (create) PasskeyException.CredentialExcluded(cause) else PasskeyException.DomError(message, cause)
            "ConstraintError" -> PasskeyException.DomError(message, cause)
            "NotSupportedError" -> PasskeyException.Unsupported(cause)
            else -> PasskeyException.Unexpected(cause)
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
        private const val TRUE = 1
        private const val HEX_RADIX = 16
        private const val DEFAULT_TIMEOUT_MS = 60_000u

        private const val RP_INFO_VERSION = 1u
        private const val USER_INFO_VERSION = 1u
        private const val CLIENT_DATA_VERSION = 1u
        private const val COSE_CREDENTIAL_PARAMETER_VERSION = 1u
        private const val CREDENTIAL_EX_VERSION = 1u

        // Conservative option struct versions: VERSION_3 (make) / VERSION_4 (get)
        // are present on every webauthn.dll since Windows 10 1903 and cover all
        // fields this client sets (allow/exclude lists, UV, attachment,
        // cancellation id).
        private const val MAKE_CREDENTIAL_OPTIONS_VERSION = 3u
        private const val GET_ASSERTION_OPTIONS_VERSION = 4u

        // Output struct versions: VERSION_3 adds dwUsedTransport; VERSION_8 /
        // VERSION_6 first carry the ready-made response JSON.
        private const val CREDENTIAL_ATTESTATION_VERSION_3 = 3
        private const val CREDENTIAL_ATTESTATION_VERSION_8 = 8
        private const val ASSERTION_VERSION_6 = 6

        private const val HASH_ALG_SHA_256 = "SHA-256"
        private const val CREDENTIAL_TYPE_PUBLIC_KEY = "public-key"

        // COSE algorithm identifiers offered when the server lists none: ES256 (-7) and RS256 (-257).
        private val DEFAULT_COSE_ALGORITHMS = listOf(-7L, -257L)

        private const val ATTACHMENT_ANY = 0u
        private const val ATTACHMENT_PLATFORM = 1u
        private const val ATTACHMENT_CROSS_PLATFORM = 2u

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
