@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.androidpoet.passkeys

import fido2.COSE_EDDSA
import fido2.COSE_ES256
import fido2.COSE_RS256
import fido2.FIDO_ERR_PIN_BLOCKED
import fido2.FIDO_ERR_PIN_INVALID
import fido2.FIDO_ERR_PIN_REQUIRED
import fido2.FIDO_OK
import fido2.FIDO_OPT_FALSE
import fido2.FIDO_OPT_OMIT
import fido2.FIDO_OPT_TRUE
import fido2.fido_assert_allow_cred
import fido2.fido_assert_authdata_len
import fido2.fido_assert_authdata_ptr
import fido2.fido_assert_count
import fido2.fido_assert_free
import fido2.fido_assert_id_len
import fido2.fido_assert_id_ptr
import fido2.fido_assert_new
import fido2.fido_assert_set_clientdata
import fido2.fido_assert_set_rp
import fido2.fido_assert_set_uv
import fido2.fido_assert_sig_len
import fido2.fido_assert_sig_ptr
import fido2.fido_assert_t
import fido2.fido_assert_user_id_len
import fido2.fido_assert_user_id_ptr
import fido2.fido_cred_authdata_raw_len
import fido2.fido_cred_authdata_raw_ptr
import fido2.fido_cred_exclude
import fido2.fido_cred_fmt
import fido2.fido_cred_free
import fido2.fido_cred_id_len
import fido2.fido_cred_id_ptr
import fido2.fido_cred_new
import fido2.fido_cred_set_clientdata
import fido2.fido_cred_set_rk
import fido2.fido_cred_set_rp
import fido2.fido_cred_set_type
import fido2.fido_cred_set_user
import fido2.fido_cred_set_uv
import fido2.fido_cred_sig_len
import fido2.fido_cred_sig_ptr
import fido2.fido_cred_t
import fido2.fido_cred_x5c_len
import fido2.fido_cred_x5c_ptr
import fido2.fido_dev_close
import fido2.fido_dev_free
import fido2.fido_dev_get_assert
import fido2.fido_dev_info_free
import fido2.fido_dev_info_manifest
import fido2.fido_dev_info_new
import fido2.fido_dev_info_path
import fido2.fido_dev_info_ptr
import fido2.fido_dev_info_t
import fido2.fido_dev_make_cred
import fido2.fido_dev_new
import fido2.fido_dev_open
import fido2.fido_dev_set_timeout
import fido2.fido_dev_t
import fido2.fido_init
import io.github.androidpoet.passkeys.internal.CborWriter
import io.github.androidpoet.passkeys.internal.NativeAuthenticationOptions
import io.github.androidpoet.passkeys.internal.NativeCreationOptions
import io.github.androidpoet.passkeys.internal.PasskeyPayloadMapper
import io.github.androidpoet.passkeys.internal.decodeBase64Url
import io.github.androidpoet.passkeys.internal.encodeBase64Url
import io.github.androidpoet.passkeys.internal.passkeyResult
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import io.github.androidpoet.passkeys.models.PasskeyCreationOptions
import io.github.androidpoet.passkeys.models.PasskeyCreationResponse
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * [PasskeyClient] for Linux desktop, backed by **libfido2**.
 *
 * Linux has no OS platform authenticator (no Touch-ID / Windows-Hello
 * equivalent and no system passkey provider), so this client drives **roaming
 * authenticators only** — USB/NFC FIDO2 security keys. Requests that require a
 * platform/biometric authenticator fail with [PasskeyException.Unsupported]
 * before touching a device, and a missing key fails with
 * [PasskeyException.NoCredential]. See [capabilities].
 *
 * The server's `pubKeyCredParams` (first of ES256 / EdDSA / RS256 the server
 * accepts), resident-key and user-verification requirements,
 * `excludeCredentials` and `timeout` are forwarded to the key. Keys that need a
 * PIN for the requested operation report [PasskeyException.DomError]; this
 * client has no PIN prompt. The first attached key and, for discoverable
 * logins, its first matching account are used. The blocking libfido2 calls run
 * on [Dispatchers.IO].
 *
 * A native client has no browser to establish the origin: `clientDataJSON.origin`
 * is `https://<rpId>` unless [origin] is given. Your server must list that
 * origin, and it is your app's job to only run options for your own RP.
 *
 * Requires the libfido2 shared library at runtime (`libfido2-dev` /
 * `libfido2`) and, on Linux, udev rules granting non-root access to the key.
 */
public class LinuxPasskeyClient(
    private val origin: String? = null,
) : PasskeyClient {
    override suspend fun create(options: PasskeyCreationOptions): PasskeyResult<PasskeyCreationResponse> =
        passkeyResult {
            val opts = PasskeyPayloadMapper.nativeCreationOptions(options.requestJson)
            if (opts.authenticatorSelection?.authenticatorAttachment == "platform") {
                throw PasskeyException.Unsupported(
                    IllegalStateException("Linux has no platform authenticator; this request requires one (authenticatorAttachment=platform)"),
                )
            }
            val algorithm = chooseAlgorithm(opts.algorithms)
            val clientData = clientDataJson("webauthn.create", opts.challenge, originFor(opts.rp.id))
            val raw = withContext(Dispatchers.IO) { makeCredential(opts, algorithm, clientData) }
            PasskeyPayloadMapper.creationResponse(raw)
        }

    override suspend fun authenticate(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> =
        passkeyResult {
            val opts = PasskeyPayloadMapper.nativeAuthenticationOptions(options.requestJson)
            val clientData = clientDataJson("webauthn.get", opts.challenge, originFor(opts.rpId))
            val raw = withContext(Dispatchers.IO) { getAssertion(opts, clientData) }
            PasskeyPayloadMapper.authenticationResponse(raw)
        }

    private fun originFor(rpId: String): String = origin ?: "https://$rpId"

    // The first algorithm the server lists that libfido2 can produce; ES256 when
    // the server lists none. Never substitutes an algorithm the server rejects.
    private fun chooseAlgorithm(algorithms: List<Long>): Int {
        if (algorithms.isEmpty()) return COSE_ES256
        return algorithms.firstOrNull { it in SUPPORTED_ALGORITHMS }?.toInt()
            ?: throw PasskeyException.Unsupported(
                IllegalStateException("pubKeyCredParams allows none of ES256 (-7), EdDSA (-8) or RS256 (-257)"),
            )
    }

    @OptIn(ExperimentalForeignApi::class)
    @Suppress("ThrowsCount", "LongMethod") // each libfido2 FFI step guards with a typed PasskeyException
    private fun makeCredential(opts: NativeCreationOptions, algorithm: Int, clientData: String): String {
        fido_init(0)
        val devicePath = firstDevicePath() ?: throw noDeviceError()
        val cred = fido_cred_new() ?: throw allocError("fido_cred_new")
        val dev =
            fido_dev_new() ?: run {
                freeCred(cred)
                throw allocError("fido_dev_new")
            }
        try {
            check(fido_cred_set_type(cred, algorithm), "set_type")
            val clientDataBytes = clientData.encodeToByteArray()
            clientDataBytes.usePinned { pin ->
                check(fido_cred_set_clientdata(cred, pin.addressOf(0).reinterpret(), clientDataBytes.size.convert()), "set_clientdata")
            }
            check(fido_cred_set_rp(cred, opts.rp.id, opts.rp.name), "set_rp")
            val userId = opts.user.id.decodeBase64Url()
            userId.usePinned { pin ->
                val idPtr: CPointer<UByteVar>? = if (userId.isEmpty()) null else pin.addressOf(0).reinterpret()
                check(
                    fido_cred_set_user(
                        cred,
                        idPtr,
                        userId.size.convert(),
                        opts.user.name,
                        opts.user.displayName,
                        null,
                    ),
                    "set_user",
                )
            }
            check(fido_cred_set_rk(cred, if (opts.requiresDiscoverableCredential) FIDO_OPT_TRUE else FIDO_OPT_OMIT), "set_rk")
            check(fido_cred_set_uv(cred, fidoOption(opts.authenticatorSelection?.userVerification)), "set_uv")
            opts.excludeCredentials.forEach { descriptor ->
                val id = descriptor.id.decodeBase64Url()
                if (id.isNotEmpty()) {
                    id.usePinned { pin ->
                        check(fido_cred_exclude(cred, pin.addressOf(0).reinterpret(), id.size.convert()), "exclude")
                    }
                }
            }
            if (fido_dev_open(dev, devicePath) != FIDO_OK) throw openError(devicePath)
            val rc =
                try {
                    applyTimeout(dev, opts.timeout)
                    fido_dev_make_cred(dev, cred, null)
                } finally {
                    fido_dev_close(dev)
                }
            if (rc != FIDO_OK) throw fidoError(rc, "make_cred")

            val credId = fido_cred_id_ptr(cred).readBytesOrEmpty(fido_cred_id_len(cred))
            val authData = fido_cred_authdata_raw_ptr(cred).readBytesOrEmpty(fido_cred_authdata_raw_len(cred))
            val sig = fido_cred_sig_ptr(cred).readBytesOrEmpty(fido_cred_sig_len(cred))
            val x5c = fido_cred_x5c_ptr(cred).readBytesOrEmpty(fido_cred_x5c_len(cred))
            val fmt = fido_cred_fmt(cred)?.toKString() ?: "none"
            return registrationJson(credId, attestationObject(fmt, algorithm, authData, sig, x5c), clientDataBytes)
        } finally {
            freeDev(dev)
            freeCred(cred)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Suppress("ThrowsCount") // each libfido2 FFI step guards with a typed PasskeyException
    private fun getAssertion(opts: NativeAuthenticationOptions, clientData: String): String {
        fido_init(0)
        val devicePath = firstDevicePath() ?: throw noDeviceError()
        val assert = fido_assert_new() ?: throw allocError("fido_assert_new")
        val dev =
            fido_dev_new() ?: run {
                freeAssert(assert)
                throw allocError("fido_dev_new")
            }
        try {
            val clientDataBytes = clientData.encodeToByteArray()
            clientDataBytes.usePinned { pin ->
                check(fido_assert_set_clientdata(assert, pin.addressOf(0).reinterpret(), clientDataBytes.size.convert()), "set_clientdata")
            }
            check(fido_assert_set_rp(assert, opts.rpId), "set_rp")
            check(fido_assert_set_uv(assert, fidoOption(opts.userVerification)), "set_uv")
            opts.allowCredentials.forEach { descriptor ->
                val id = descriptor.id.decodeBase64Url()
                if (id.isNotEmpty()) {
                    id.usePinned { pin ->
                        check(fido_assert_allow_cred(assert, pin.addressOf(0).reinterpret(), id.size.convert()), "allow_cred")
                    }
                }
            }
            if (fido_dev_open(dev, devicePath) != FIDO_OK) throw openError(devicePath)
            val rc =
                try {
                    applyTimeout(dev, opts.timeout)
                    fido_dev_get_assert(dev, assert, null)
                } finally {
                    fido_dev_close(dev)
                }
            if (rc != FIDO_OK) throw fidoError(rc, "get_assert")
            if (fido_assert_count(assert).toInt() == 0) throw noDeviceError()

            val idx: ULong = 0uL
            val authData =
                unwrapCborByteString(
                    fido_assert_authdata_ptr(assert, idx).readBytesOrEmpty(fido_assert_authdata_len(assert, idx)),
                )
            val sig = fido_assert_sig_ptr(assert, idx).readBytesOrEmpty(fido_assert_sig_len(assert, idx))
            val userId = fido_assert_user_id_ptr(assert, idx).readBytesOrEmpty(fido_assert_user_id_len(assert, idx))
            val credId = fido_assert_id_ptr(assert, idx).readBytesOrEmpty(fido_assert_id_len(assert, idx))
            return assertionJson(credId, clientDataBytes, authData, sig, userId)
        } finally {
            freeDev(dev)
            freeAssert(assert)
        }
    }

    private fun applyTimeout(dev: CPointer<fido_dev_t>, timeout: Long?) {
        val millis = timeout?.takeIf { it > 0 }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: return
        // Best effort: an authenticator that cannot honour it keeps its default.
        fido_dev_set_timeout(dev, millis)
    }

    private fun fidoOption(userVerification: String?): UInt =
        when (userVerification) {
            "required" -> FIDO_OPT_TRUE
            "discouraged" -> FIDO_OPT_FALSE
            else -> FIDO_OPT_OMIT
        }

    // --- WebAuthn JSON assembly (base64url, server-ready) ---

    private fun clientDataJson(type: String, challengeBase64Url: String, origin: String): String =
        buildJsonObject {
            put("type", type)
            put("challenge", challengeBase64Url)
            put("origin", origin)
            put("crossOrigin", false)
        }.toString()

    private fun registrationJson(credentialId: ByteArray, attestationObject: ByteArray, clientData: ByteArray): String =
        buildJsonObject {
            put("id", credentialId.encodeBase64Url())
            put("rawId", credentialId.encodeBase64Url())
            put("type", "public-key")
            put("authenticatorAttachment", "cross-platform")
            putJsonObject("clientExtensionResults") {}
            putJsonObject("response") {
                put("clientDataJSON", clientData.encodeBase64Url())
                put("attestationObject", attestationObject.encodeBase64Url())
                putJsonArray("transports") { add("usb") }
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
            put("authenticatorAttachment", "cross-platform")
            putJsonObject("clientExtensionResults") {}
            putJsonObject("response") {
                put("clientDataJSON", clientData.encodeBase64Url())
                put("authenticatorData", authenticatorData.encodeBase64Url())
                put("signature", signature.encodeBase64Url())
                if (userHandle.isNotEmpty()) put("userHandle", userHandle.encodeBase64Url())
            }
        }.toString()

    // Assembles the CBOR attestationObject {fmt, attStmt, authData} from the
    // pieces libfido2 returns. "packed" with a leaf cert -> {alg,sig,x5c};
    // self-attestation -> {alg,sig}; "none" or no signature -> empty attStmt.
    // `alg` is the credential algorithm actually requested from the key.
    private fun attestationObject(fmt: String, algorithm: Int, authData: ByteArray, signature: ByteArray, x5c: ByteArray): ByteArray {
        val writer = CborWriter().mapHeader(3)
        writer.textString("fmt").textString(fmt)
        writer.textString("attStmt")
        when {
            fmt == "none" || signature.isEmpty() -> writer.mapHeader(0)
            x5c.isNotEmpty() ->
                writer
                    .mapHeader(3)
                    .textString("alg")
                    .integer(algorithm.toLong())
                    .textString("sig")
                    .byteString(signature)
                    .textString("x5c")
                    .arrayHeader(1)
                    .byteString(x5c)
            else ->
                writer
                    .mapHeader(2)
                    .textString("alg")
                    .integer(algorithm.toLong())
                    .textString("sig")
                    .byteString(signature)
        }
        writer.textString("authData").byteString(authData)
        return writer.toByteArray()
    }

    // libfido2 returns assertion authenticator data CBOR-wrapped (a byte
    // string); WebAuthn JSON wants the raw bytes. Strip the header only when it
    // is a well-formed byte string whose declared length matches the payload;
    // anything else is passed through untouched rather than guessed at.
    private fun unwrapCborByteString(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty() || (bytes[0].toInt() and MAJOR_TYPE_MASK) != BYTE_STRING_MAJOR) return bytes
        val info = bytes[0].toInt() and ADDITIONAL_INFO_MASK
        val (headerLength, declaredLength) =
            when {
                info < CBOR_IMMEDIATE -> 1 to info.toLong()
                info == CBOR_ONE_BYTE && bytes.size >= 2 -> 2 to bytes.unsigned(1)
                info == CBOR_TWO_BYTES && bytes.size >= 3 -> 3 to (bytes.unsigned(1) shl 8 or bytes.unsigned(2))
                info == CBOR_FOUR_BYTES && bytes.size >= 5 ->
                    5 to (bytes.unsigned(1) shl 24 or (bytes.unsigned(2) shl 16) or (bytes.unsigned(3) shl 8) or bytes.unsigned(4))
                else -> return bytes
            }
        if (bytes.size - headerLength != declaredLength.toInt()) return bytes
        return bytes.copyOfRange(headerLength, bytes.size)
    }

    private fun ByteArray.unsigned(index: Int): Long = this[index].toLong() and 0xFF

    // --- libfido2 device + memory helpers ---

    @OptIn(ExperimentalForeignApi::class)
    private fun firstDevicePath(): String? =
        memScoped {
            val devlist = fido_dev_info_new(MAX_DEVICES.convert()) ?: return@memScoped null
            try {
                val found = alloc<ULongVar>()
                if (fido_dev_info_manifest(devlist, MAX_DEVICES.convert(), found.ptr) != FIDO_OK) return@memScoped null
                if (found.value == 0uL) return@memScoped null
                fido_dev_info_path(fido_dev_info_ptr(devlist, 0uL))?.toKString()
            } finally {
                val holder = allocPointerTo<fido_dev_info_t>()
                holder.value = devlist
                fido_dev_info_free(holder.ptr, MAX_DEVICES.convert())
            }
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun CPointer<UByteVar>?.readBytesOrEmpty(length: ULong): ByteArray =
        if (this == null || length == 0uL) ByteArray(0) else reinterpret<ByteVar>().readBytes(length.toInt())

    @OptIn(ExperimentalForeignApi::class)
    private fun freeCred(cred: CPointer<fido_cred_t>) =
        memScoped {
            val holder = allocPointerTo<fido_cred_t>()
            holder.value = cred
            fido_cred_free(holder.ptr)
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun freeAssert(assert: CPointer<fido_assert_t>) =
        memScoped {
            val holder = allocPointerTo<fido_assert_t>()
            holder.value = assert
            fido_assert_free(holder.ptr)
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun freeDev(dev: CPointer<fido_dev_t>) =
        memScoped {
            val holder = allocPointerTo<fido_dev_t>()
            holder.value = dev
            fido_dev_free(holder.ptr)
        }

    private fun check(rc: Int, op: String) {
        if (rc != FIDO_OK) throw fidoError(rc, op)
    }

    private fun fidoError(rc: Int, op: String): PasskeyException {
        val message = "libfido2 $op failed (code 0x${rc.toString(HEX_RADIX)})"
        return when (rc) {
            FIDO_ERR_PIN_REQUIRED, FIDO_ERR_PIN_INVALID, FIDO_ERR_PIN_BLOCKED ->
                PasskeyException.DomError("$message: the security key requires a PIN, which this client cannot prompt for")
            else -> PasskeyException.Unexpected(IllegalStateException(message))
        }
    }

    private fun noDeviceError(): PasskeyException =
        PasskeyException.NoCredential(IllegalStateException("No FIDO2 security key detected. Attach a key and retry."))

    private fun allocError(what: String): PasskeyException =
        PasskeyException.Unexpected(IllegalStateException("$what returned null"))

    private fun openError(path: String): PasskeyException =
        PasskeyException.Unexpected(IllegalStateException("Cannot open FIDO device at $path"))

    public companion object {
        /** What passkeys are actually possible on Linux: roaming keys only. */
        public val capabilities: LinuxPasskeyCapabilities =
            LinuxPasskeyCapabilities(
                roamingAuthenticators = true,
                platformAuthenticator = false,
                hybridTransport = false,
            )

        private val SUPPORTED_ALGORITHMS = setOf(COSE_ES256.toLong(), COSE_EDDSA.toLong(), COSE_RS256.toLong())

        private const val MAX_DEVICES = 8
        private const val HEX_RADIX = 16
        private const val MAJOR_TYPE_MASK = 0xE0
        private const val ADDITIONAL_INFO_MASK = 0x1F
        private const val BYTE_STRING_MAJOR = 0x40
        private const val CBOR_IMMEDIATE = 24
        private const val CBOR_ONE_BYTE = 24
        private const val CBOR_TWO_BYTES = 25
        private const val CBOR_FOUR_BYTES = 26
    }
}

/**
 * What [LinuxPasskeyClient] can and cannot do — Linux exposes only roaming
 * (USB/NFC) security keys, never a platform/biometric or hybrid authenticator.
 */
public data class LinuxPasskeyCapabilities(
    public val roamingAuthenticators: Boolean,
    public val platformAuthenticator: Boolean,
    public val hybridTransport: Boolean,
)
