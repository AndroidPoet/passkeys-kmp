package io.github.androidpoet.passkeys.server

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.RelyingPartyIdentity
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.UserVerificationRequirement
import java.util.UUID

/**
 * The server half of a passkey ceremony — a thin, explicit wrapper over Yubico's
 * WebAuthn Relying Party operations that speaks the same W3C WebAuthn JSON this
 * project's clients produce and consume.
 *
 * Both ceremonies are two calls: a `begin` that mints the options + a single-use
 * [PasskeyCeremonyRequest.ceremonyId] (the pending request is persisted via
 * [challenges]), and a `finish` that verifies the client's response against the
 * stored request and updates [credentials]. Verification failures surface as
 * [PasskeyVerificationException].
 *
 * @param config the Relying Party identity and permitted origins.
 * @param credentials bring-your-own credential persistence.
 * @param challenges single-use storage for pending ceremony state.
 */
public class PasskeyRelyingParty(
    private val config: PasskeyServerConfig,
    private val credentials: PasskeyCredentialStore,
    private val challenges: PasskeyChallengeStore,
) {
    private val rp: RelyingParty =
        RelyingParty
            .builder()
            .identity(
                RelyingPartyIdentity
                    .builder()
                    .id(config.rpId)
                    .name(config.rpName)
                    .build(),
            ).credentialRepository(YubicoCredentialRepository(credentials))
            .origins(config.origins)
            .allowOriginPort(true)
            .build()

    /**
     * Starts a registration ceremony for [user]. Returns the
     * `PublicKeyCredentialCreationOptions` (as a `navigator.credentials.create`
     * envelope) plus the ceremony id to echo back on [finishRegistration].
     */
    public fun beginRegistration(user: PasskeyUser): PasskeyCeremonyRequest {
        val options =
            rp.startRegistration(
                StartRegistrationOptions
                    .builder()
                    .user(
                        UserIdentity
                            .builder()
                            .name(user.name)
                            .displayName(user.displayName)
                            .id(ByteArray.fromBase64Url(user.handle))
                            .build(),
                    ).authenticatorSelection(
                        AuthenticatorSelectionCriteria
                            .builder()
                            .residentKey(ResidentKeyRequirement.REQUIRED)
                            .userVerification(UserVerificationRequirement.PREFERRED)
                            .build(),
                    ).build(),
            )
        val ceremonyId = newCeremonyId()
        challenges.put(ceremonyId, options.toJson())
        return PasskeyCeremonyRequest(ceremonyId, options.toCredentialsCreateJson())
    }

    /**
     * Verifies the registration [responseJson] (the client's `rawJson`) against
     * the ceremony identified by [ceremonyId], persists the new credential, and
     * returns it.
     *
     * @throws PasskeyVerificationException if the ceremony is unknown/expired,
     *   the response is malformed, or attestation verification fails.
     */
    public fun finishRegistration(ceremonyId: String, responseJson: String): StoredPasskey {
        val requestJson = challenges.take(ceremonyId) ?: throw unknownCeremony(ceremonyId)
        try {
            val request = PublicKeyCredentialCreationOptions.fromJson(requestJson)
            val response = PublicKeyCredential.parseRegistrationResponseJson(responseJson)
            val result =
                rp.finishRegistration(
                    FinishRegistrationOptions
                        .builder()
                        .request(request)
                        .response(response)
                        .build(),
                )
            val stored =
                StoredPasskey(
                    credentialId = result.keyId.id.base64Url,
                    userHandle = request.user.id.base64Url,
                    username = request.user.name,
                    publicKeyCose = result.publicKeyCose.base64Url,
                    signatureCount = result.signatureCount,
                )
            credentials.save(stored)
            return stored
        } catch (e: Exception) {
            throw PasskeyVerificationException("registration verification failed", e)
        }
    }

    /**
     * Starts an authentication ceremony. Pass [username] to scope the request to
     * one account's credentials, or `null` for a usernameless (discoverable
     * credential) login. Returns the `PublicKeyCredentialRequestOptions` (as a
     * `navigator.credentials.get` envelope) plus the ceremony id.
     */
    public fun beginAuthentication(username: String? = null): PasskeyCeremonyRequest {
        val builder = StartAssertionOptions.builder()
        if (username != null) builder.username(username)
        val request = rp.startAssertion(builder.build())
        val ceremonyId = newCeremonyId()
        challenges.put(ceremonyId, request.toJson())
        return PasskeyCeremonyRequest(ceremonyId, request.toCredentialsGetJson())
    }

    /**
     * Verifies the authentication [responseJson] (the client's `rawJson`) against
     * the ceremony identified by [ceremonyId], updates the stored signature
     * counter, and returns who authenticated.
     *
     * @throws PasskeyVerificationException if the ceremony is unknown/expired,
     *   the response is malformed, or the assertion signature is invalid.
     */
    public fun finishAuthentication(ceremonyId: String, responseJson: String): PasskeyAuthenticationOutcome {
        val requestJson = challenges.take(ceremonyId) ?: throw unknownCeremony(ceremonyId)
        val result =
            try {
                val request = AssertionRequest.fromJson(requestJson)
                val response = PublicKeyCredential.parseAssertionResponseJson(responseJson)
                rp.finishAssertion(
                    FinishAssertionOptions
                        .builder()
                        .request(request)
                        .response(response)
                        .build(),
                )
            } catch (e: Exception) {
                // Yubico's finishAssertion throws on any verification failure
                // (bad signature, counter regression, origin/rpId mismatch).
                throw PasskeyVerificationException("authentication verification failed", e)
            }
        val credentialId = result.credential.credentialId.base64Url
        credentials.updateSignatureCount(credentialId, result.signatureCount)
        return PasskeyAuthenticationOutcome(
            username = result.username,
            userHandle = result.credential.userHandle.base64Url,
            credentialId = credentialId,
            signatureCount = result.signatureCount,
        )
    }

    private fun unknownCeremony(ceremonyId: String) =
        PasskeyVerificationException("no pending ceremony for id '$ceremonyId' (unknown, expired, or already used)")

    private fun newCeremonyId(): String = UUID.randomUUID().toString()
}
