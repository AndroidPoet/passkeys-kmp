package io.github.androidpoet.passkeys.server

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import java.util.Optional

/**
 * Adapts the public, storage-agnostic [PasskeyCredentialStore] to the Yubico
 * `CredentialRepository` the underlying `RelyingParty` requires. Yubico types
 * never escape the library through this boundary; the store speaks base64url
 * strings, this class converts to/from Yubico [ByteArray].
 */
internal class YubicoCredentialRepository(
    private val store: PasskeyCredentialStore,
) : CredentialRepository {
    override fun getCredentialIdsForUsername(username: String): Set<PublicKeyCredentialDescriptor> =
        store
            .findByUsername(username)
            .map { PublicKeyCredentialDescriptor.builder().id(ByteArray.fromBase64Url(it.credentialId)).build() }
            .toSet()

    override fun getUserHandleForUsername(username: String): Optional<ByteArray> =
        Optional.ofNullable(store.userHandleForUsername(username)).map(ByteArray::fromBase64Url)

    override fun getUsernameForUserHandle(userHandle: ByteArray): Optional<String> =
        Optional.ofNullable(store.usernameForUserHandle(userHandle.base64Url))

    override fun lookup(credentialId: ByteArray, userHandle: ByteArray): Optional<RegisteredCredential> =
        Optional
            .ofNullable(store.find(credentialId.base64Url, userHandle.base64Url))
            .map { it.toRegisteredCredential() }

    override fun lookupAll(credentialId: ByteArray): Set<RegisteredCredential> =
        store
            .findByCredentialId(credentialId.base64Url)
            .map { it.toRegisteredCredential() }
            .toSet()

    private fun StoredPasskey.toRegisteredCredential(): RegisteredCredential =
        RegisteredCredential
            .builder()
            .credentialId(ByteArray.fromBase64Url(credentialId))
            .userHandle(ByteArray.fromBase64Url(userHandle))
            .publicKeyCose(ByteArray.fromBase64Url(publicKeyCose))
            .signatureCount(signatureCount)
            .build()
}
