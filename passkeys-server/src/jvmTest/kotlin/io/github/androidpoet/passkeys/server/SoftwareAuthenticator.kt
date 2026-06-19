package io.github.androidpoet.passkeys.server

import com.upokecenter.cbor.CBORObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * A minimal in-process WebAuthn authenticator for tests: it generates an EC
 * P-256 credential and produces standards-shaped registration ("none"
 * attestation) and assertion responses, so a real [PasskeyRelyingParty] verifies
 * them end-to-end without a browser or device. Single credential per instance.
 */
internal class SoftwareAuthenticator(
    private val origin: String,
) {
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val json = Json { ignoreUnknownKeys = true }

    private val keyPair: KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val credentialId = ByteArray(CRED_ID_LEN).also { SecureRandom().nextBytes(it) }

    /** Builds the registration response JSON for the given creation options. */
    fun register(creationOptionsJson: String): String {
        val publicKey = publicKeyOptions(creationOptionsJson)
        val challenge = publicKey["challenge"]!!.jsonPrimitive.content
        val rpId = publicKey["rp"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val clientDataJson = clientData("webauthn.create", challenge)
        val authData = authenticatorData(rpId, FLAGS_REGISTRATION, signCount = 0, includeCredential = true)
        val attestationObject =
            CBORObject
                .NewMap()
                .Add("fmt", "none")
                .Add("attStmt", CBORObject.NewMap())
                .Add("authData", authData)
                .EncodeToBytes()

        return buildJson(
            "clientDataJSON" to b64(clientDataJson.toByteArray()),
            "attestationObject" to b64(attestationObject),
        )
    }

    /** Builds the assertion response JSON for the given request options. */
    fun authenticate(requestOptionsJson: String, userHandle: String): String {
        val publicKey = publicKeyOptions(requestOptionsJson)
        val challenge = publicKey["challenge"]!!.jsonPrimitive.content
        val rpId = publicKey["rpId"]!!.jsonPrimitive.content

        val clientDataJson = clientData("webauthn.get", challenge)
        val authData = authenticatorData(rpId, FLAGS_ASSERTION, signCount = 1, includeCredential = false)
        val signed = authData + sha256(clientDataJson.toByteArray())
        val signature =
            Signature
                .getInstance("SHA256withECDSA")
                .apply {
                    initSign(keyPair.private)
                    update(signed)
                }.sign()

        return buildJson(
            "clientDataJSON" to b64(clientDataJson.toByteArray()),
            "authenticatorData" to b64(authData),
            "signature" to b64(signature),
            "userHandle" to userHandle,
        )
    }

    private fun publicKeyOptions(optionsJson: String) =
        json.parseToJsonElement(optionsJson).jsonObject["publicKey"]!!.jsonObject

    private fun clientData(type: String, challenge: String) =
        """{"type":"$type","challenge":"$challenge","origin":"$origin","crossOrigin":false}"""

    private fun buildJson(vararg responseFields: Pair<String, String>): String {
        val id = b64(credentialId)
        val response = responseFields.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
        return """{"type":"public-key","id":"$id","rawId":"$id",""" +
            """"response":{$response},"clientExtensionResults":{}}"""
    }

    private fun authenticatorData(rpId: String, flags: Int, signCount: Int, includeCredential: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(sha256(rpId.toByteArray()))
        out.write(flags)
        out.write(byteArrayOf((signCount ushr 24).toByte(), (signCount ushr 16).toByte(), (signCount ushr 8).toByte(), signCount.toByte()))
        if (includeCredential) {
            out.write(ByteArray(AAGUID_LEN)) // zero AAGUID
            out.write((credentialId.size ushr 8) and 0xFF)
            out.write(credentialId.size and 0xFF)
            out.write(credentialId)
            out.write(coseKey())
        }
        return out.toByteArray()
    }

    private fun coseKey(): ByteArray {
        val publicKey = keyPair.public as ECPublicKey
        return CBORObject
            .NewMap()
            .Add(COSE_KTY, COSE_KTY_EC2)
            .Add(COSE_ALG, COSE_ALG_ES256)
            .Add(COSE_CRV, COSE_CRV_P256)
            .Add(COSE_X, fixedLength(publicKey.w.affineX, COORDINATE_LEN))
            .Add(COSE_Y, fixedLength(publicKey.w.affineY, COORDINATE_LEN))
            .EncodeToBytes()
    }

    private fun fixedLength(value: BigInteger, length: Int): ByteArray {
        val raw = value.toByteArray() // may carry a sign byte or be short
        val result = ByteArray(length)
        val copyLen = minOf(raw.size, length)
        System.arraycopy(raw, raw.size - copyLen, result, length - copyLen, copyLen)
        return result
    }

    private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    private fun b64(bytes: ByteArray): String = urlEncoder.encodeToString(bytes)

    private companion object {
        const val CRED_ID_LEN = 32
        const val AAGUID_LEN = 16
        const val COORDINATE_LEN = 32
        const val FLAGS_REGISTRATION = 0x45 // UP | UV | AT
        const val FLAGS_ASSERTION = 0x05 // UP | UV

        // COSE_Key (RFC 8152) labels for an EC2 ES256 P-256 public key.
        const val COSE_KTY = 1
        const val COSE_ALG = 3
        const val COSE_CRV = -1
        const val COSE_X = -2
        const val COSE_Y = -3
        const val COSE_KTY_EC2 = 2
        const val COSE_ALG_ES256 = -7
        const val COSE_CRV_P256 = 1
    }
}
