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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/**
 * The server half of a passkey ceremony — a thin, explicit wrapper over Yubico's
 * WebAuthn Relying Party operations that speaks the same W3C WebAuthn JSON this
 * project's clients produce and consume.
 *
 * Both ceremonies are two calls: a `begin` that mints the options + a single-use
 * [PasskeyCeremonyRequest.ceremonyId] (the pending request is persisted via
 * [challenges] together with its kind and expiry), and a `finish` that verifies
 * the client's response against the stored request and updates [credentials].
 * Verification failures surface as [PasskeyVerificationException].
 *
 * Account ownership is **your** responsibility: pass [beginRegistration] only a
 * [PasskeyUser] resolved from an authenticated session or your sign-up flow.
 *
 * @param config the Relying Party identity, permitted origins and policies.
 * @param credentials bring-your-own credential persistence.
 * @param challenges single-use storage for pending ceremony state.
 * @param clock the time source for ceremony expiry (injectable for tests).
 */
public class PasskeyRelyingParty(
    private val config: PasskeyServerConfig,
    private val credentials: PasskeyCredentialStore,
    private val challenges: PasskeyChallengeStore,
    private val clock: Clock = Clock.systemUTC(),
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
            .allowOriginPort(config.allowOriginPort)
            // Consumer passkeys are not attestation-verified: platform
            // authenticators return "none" attestation and there is no
            // enterprise attestation trust source. Requiring trusted attestation
            // would reject ordinary passkey registrations.
            .allowUntrustedAttestation(true)
            .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val registrationLock = Any()

    /**
     * Starts a registration ceremony for [user]. Returns the
     * `PublicKeyCredentialCreationOptions` (as a `navigator.credentials.create`
     * envelope) plus the ceremony id to echo back on [finishRegistration].
     *
     * @throws IllegalArgumentException if [user] conflicts with an existing
     *   username ↔ user-handle mapping.
     */
    public fun beginRegistration(user: PasskeyUser): PasskeyCeremonyRequest {
        requireConsistentIdentity(user.name, user.handle)
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
                            .userVerification(config.userVerification.toYubico())
                            .build(),
                    ).build(),
            )
        val ceremonyId = storePending(CeremonyKind.REGISTRATION, options.toJson())
        return PasskeyCeremonyRequest(ceremonyId, options.toCredentialsCreateJson())
    }

    /**
     * Verifies the registration [responseJson] (the client's `rawJson`) against
     * the ceremony identified by [ceremonyId], persists the new credential, and
     * returns it.
     *
     * @throws PasskeyVerificationException if the ceremony is unknown/expired,
     *   the response is malformed, attestation verification fails, or the
     *   credential conflicts with an existing one.
     */
    public fun finishRegistration(ceremonyId: String, responseJson: String): StoredPasskey {
        val requestJson = takePending(CeremonyKind.REGISTRATION, ceremonyId)
        val stored =
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
                StoredPasskey(
                    credentialId = result.keyId.id.base64Url,
                    userHandle = request.user.id.base64Url,
                    username = request.user.name,
                    publicKeyCose = result.publicKeyCose.base64Url,
                    signatureCount = result.signatureCount,
                    backupEligible = result.isBackupEligible,
                    backedUp = result.isBackedUp,
                    transports =
                        result.keyId.transports
                            .map { set -> set.map { it.id }.toSet() }
                            .orElse(emptySet()),
                )
            } catch (e: Exception) {
                throw PasskeyVerificationException("registration verification failed", e)
            }
        persist(stored)
        return stored
    }

    private fun persist(stored: StoredPasskey) {
        synchronized(registrationLock) {
            try {
                requireConsistentIdentity(stored.username, stored.userHandle)
                credentials.save(stored)
            } catch (e: IllegalArgumentException) {
                throw PasskeyVerificationException("registration conflicts with an existing account", e)
            } catch (e: IllegalStateException) {
                throw PasskeyVerificationException("credential is already registered", e)
            }
        }
    }

    /**
     * Starts an authentication ceremony. Pass [username] to scope the request to
     * one account's credentials, or `null` for a usernameless (discoverable
     * credential) login. [userVerification] overrides the configured policy for
     * this ceremony only (e.g. `REQUIRED` for a step-up). Returns the
     * `PublicKeyCredentialRequestOptions` (as a `navigator.credentials.get`
     * envelope) plus the ceremony id.
     */
    public fun beginAuthentication(
        username: String? = null,
        userVerification: PasskeyUserVerification = config.userVerification,
    ): PasskeyCeremonyRequest {
        val builder = StartAssertionOptions.builder().userVerification(userVerification.toYubico())
        if (username != null) builder.username(username)
        val request = rp.startAssertion(builder.build())
        val ceremonyId = storePending(CeremonyKind.AUTHENTICATION, request.toJson())
        return PasskeyCeremonyRequest(ceremonyId, request.toCredentialsGetJson())
    }

    /**
     * Verifies the authentication [responseJson] (the client's `rawJson`) against
     * the ceremony identified by [ceremonyId], updates the stored signature
     * counter and backup state, and returns who authenticated.
     *
     * @throws PasskeyVerificationException if the ceremony is unknown/expired,
     *   the response is malformed, the assertion signature is invalid, required
     *   user verification is missing, or the signature counter regressed or
     *   moved concurrently.
     */
    public fun finishAuthentication(ceremonyId: String, responseJson: String): PasskeyAuthenticationOutcome {
        val requestJson = takePending(CeremonyKind.AUTHENTICATION, ceremonyId)
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
                // (bad signature, counter regression, origin/rpId mismatch,
                // missing required UV).
                throw PasskeyVerificationException("authentication verification failed", e)
            }
        val credentialId = result.credential.credentialId.base64Url
        val recorded =
            credentials.recordAssertion(
                credentialId = credentialId,
                expectedSignatureCount = result.credential.signatureCount,
                signatureCount = result.signatureCount,
                backedUp = result.isBackedUp,
            )
        if (!recorded) {
            throw PasskeyVerificationException("signature counter changed concurrently; assertion rejected")
        }
        return PasskeyAuthenticationOutcome(
            username = result.username,
            userHandle = result.credential.userHandle.base64Url,
            credentialId = credentialId,
            signatureCount = result.signatureCount,
            userVerified = result.isUserVerified,
            backupEligible = result.isBackupEligible,
            backedUp = result.isBackedUp,
        )
    }

    private fun requireConsistentIdentity(username: String, handle: String) {
        val boundHandle = credentials.userHandleForUsername(username)
        val boundUsername = credentials.usernameForUserHandle(handle)
        require((boundHandle == null || boundHandle == handle) && (boundUsername == null || boundUsername == username)) {
            "registration identity conflicts with an existing account"
        }
    }

    private fun storePending(kind: CeremonyKind, requestJson: String): String {
        val ceremonyId = newCeremonyId()
        val record =
            PendingCeremony(
                kind = kind.name,
                expiresAtEpochMillis = clock.millis() + config.challengeTimeout.inWholeMilliseconds,
                request = requestJson,
            )
        challenges.put(ceremonyId, json.encodeToString(PendingCeremony.serializer(), record))
        return ceremonyId
    }

    private fun takePending(kind: CeremonyKind, ceremonyId: String): String {
        val record = challenges.take(ceremonyId)?.let(::decodePending)
        val problem =
            when {
                record == null -> "no pending ceremony for id '$ceremonyId' (unknown, expired, or already used)"
                record.kind != kind.name -> "ceremony '$ceremonyId' is not a ${kind.name.lowercase()} ceremony"
                clock.millis() > record.expiresAtEpochMillis -> "ceremony '$ceremonyId' has expired"
                else -> null
            }
        if (problem != null) throw PasskeyVerificationException(problem)
        return record!!.request
    }

    private fun decodePending(raw: String): PendingCeremony =
        try {
            json.decodeFromString(PendingCeremony.serializer(), raw)
        } catch (e: IllegalArgumentException) {
            throw PasskeyVerificationException("pending ceremony record is malformed", e)
        }

    private fun newCeremonyId(): String {
        val bytes = kotlin.ByteArray(CEREMONY_ID_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun PasskeyUserVerification.toYubico(): UserVerificationRequirement =
        when (this) {
            PasskeyUserVerification.REQUIRED -> UserVerificationRequirement.REQUIRED
            PasskeyUserVerification.PREFERRED -> UserVerificationRequirement.PREFERRED
            PasskeyUserVerification.DISCOURAGED -> UserVerificationRequirement.DISCOURAGED
        }

    private enum class CeremonyKind { REGISTRATION, AUTHENTICATION }

    /** What [PasskeyChallengeStore] holds per ceremony id. */
    @Serializable
    private class PendingCeremony(
        val kind: String,
        val expiresAtEpochMillis: Long,
        val request: String,
    )

    private companion object {
        const val CEREMONY_ID_BYTES = 32
    }
}
