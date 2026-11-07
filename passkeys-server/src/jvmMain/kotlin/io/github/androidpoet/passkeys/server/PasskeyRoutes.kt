package io.github.androidpoet.passkeys.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.util.Base64

/** The identity a client asked to register under; see [PasskeyEnrollmentPolicy]. */
public class PasskeyEnrollmentRequest(
    public val name: String,
    public val displayName: String?,
)

/**
 * Decides which account a `register/begin` call may enroll a passkey for.
 *
 * A WebAuthn registration proves possession of an authenticator, not ownership
 * of an account, so the server — never the request body — must choose the
 * [PasskeyUser]. Typical implementations:
 *
 * - **Add a passkey to the signed-in account:** read the session from [call]
 *   and return that account's stable handle + name, ignoring the request.
 * - **Passkey-first sign-up:** create the account for a new [request] name and
 *   return a freshly generated handle; refuse names that already exist (see
 *   [newAccountsOnlyEnrollment]).
 *
 * Return `null` to refuse; the route answers `403`.
 */
public fun interface PasskeyEnrollmentPolicy {
    public suspend fun resolve(call: ApplicationCall, request: PasskeyEnrollmentRequest): PasskeyUser?
}

/**
 * A [PasskeyEnrollmentPolicy] for passkey-first sign-up demos: enrolls unknown
 * names as new accounts with a random 32-byte handle, and refuses names already
 * present in [credentials] — adding a second passkey to an existing account
 * must go through your own authenticated flow.
 */
public fun newAccountsOnlyEnrollment(credentials: PasskeyCredentialStore): PasskeyEnrollmentPolicy {
    val random = SecureRandom()
    return PasskeyEnrollmentPolicy { _, request ->
        if (credentials.userHandleForUsername(request.name) != null) return@PasskeyEnrollmentPolicy null
        val handle = ByteArray(NEW_HANDLE_BYTES).also(random::nextBytes)
        PasskeyUser(
            handle = Base64.getUrlEncoder().withoutPadding().encodeToString(handle),
            name = request.name,
            displayName = request.displayName ?: request.name,
        )
    }
}

private const val NEW_HANDLE_BYTES = 32

/**
 * Mounts the four passkey ceremony endpoints under [basePath], delegating to
 * [relyingParty]:
 *
 * - `POST {basePath}/register/begin` — body `{"name","displayName"?}`; the
 *   account is resolved by [enrollment] (never from the body), returns
 *   `{"ceremonyId","publicKey":{...}}` or `403` when enrollment is refused.
 * - `POST {basePath}/register/finish` — body `{"ceremonyId","response":{...}}`,
 *   returns the registered credential summary.
 * - `POST {basePath}/login/begin` — body `{"username"?}`, returns
 *   `{"ceremonyId","publicKey":{...}}`.
 * - `POST {basePath}/login/finish` — body `{"ceremonyId","response":{...}}`,
 *   returns the authentication outcome.
 *
 * The `publicKey` envelopes are the `navigator.credentials` request JSON this
 * project's clients accept directly. Verification failures and malformed
 * requests respond `400` with a JSON `{"error": ...}` body; bodies over
 * [maxBodyBytes] respond `413`. This helper is intentionally thin: add your own
 * session handling, CSRF protection and rate limiting around it, and establish
 * the signed-in session yourself from a successful `login/finish`.
 */
public fun Route.passkeyRoutes(
    relyingParty: PasskeyRelyingParty,
    enrollment: PasskeyEnrollmentPolicy,
    basePath: String = "/passkeys",
    maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
) {
    val json = Json { ignoreUnknownKeys = true }

    route(basePath) {
        post("/register/begin") {
            call.handle(json, maxBodyBytes) { body ->
                val request = PasskeyEnrollmentRequest(name = body.string("name"), displayName = body.optString("displayName"))
                val user = enrollment.resolve(call, request) ?: throw EnrollmentRefused()
                relyingParty.beginRegistration(user).withCeremonyId(json)
            }
        }

        post("/register/finish") {
            call.handle(json, maxBodyBytes) { body ->
                val stored =
                    relyingParty.finishRegistration(
                        ceremonyId = body.string("ceremonyId"),
                        responseJson = body.getValue("response").toString(),
                    )
                buildJsonObject {
                    put("credentialId", stored.credentialId)
                    put("userHandle", stored.userHandle)
                    put("username", stored.username)
                    put("signatureCount", stored.signatureCount)
                    put("backupEligible", stored.backupEligible)
                    put("backedUp", stored.backedUp)
                }
            }
        }

        post("/login/begin") {
            call.handle(json, maxBodyBytes) { body ->
                relyingParty.beginAuthentication(body.optString("username")).withCeremonyId(json)
            }
        }

        post("/login/finish") {
            call.handle(json, maxBodyBytes) { body ->
                val outcome =
                    relyingParty.finishAuthentication(
                        ceremonyId = body.string("ceremonyId"),
                        responseJson = body.getValue("response").toString(),
                    )
                buildJsonObject {
                    put("username", outcome.username)
                    put("userHandle", outcome.userHandle)
                    put("credentialId", outcome.credentialId)
                    put("signatureCount", outcome.signatureCount)
                    put("userVerified", outcome.userVerified)
                    put("backupEligible", outcome.backupEligible)
                    put("backedUp", outcome.backedUp)
                }
            }
        }
    }
}

private const val DEFAULT_MAX_BODY_BYTES = 64L * 1024

private class EnrollmentRefused : RuntimeException("registration is not permitted for this account")

/**
 * Runs [block] with the parsed request body and writes its JSON result, mapping
 * verification failures and malformed/missing input to a `400` JSON error,
 * refused enrollment to `403`, and oversized bodies to `413`.
 */
private suspend fun ApplicationCall.handle(
    json: Json,
    maxBodyBytes: Long,
    block: suspend (RequestBody) -> JsonObject,
) {
    val declared = request.contentLength()
    if (declared != null && declared > maxBodyBytes) {
        return respondError(json, "request body too large", HttpStatusCode.PayloadTooLarge)
    }
    val result =
        try {
            val text = receiveText()
            if (text.length > maxBodyBytes) {
                return respondError(json, "request body too large", HttpStatusCode.PayloadTooLarge)
            }
            block(RequestBody(json.parseToJsonElement(text).jsonObject))
        } catch (e: EnrollmentRefused) {
            return respondError(json, e.message, HttpStatusCode.Forbidden)
        } catch (e: PasskeyVerificationException) {
            return respondError(json, e.message)
        } catch (e: IllegalArgumentException) {
            return respondError(json, e.message)
        } catch (e: SerializationException) {
            return respondError(json, "malformed request body: ${e.message}")
        }
    respondText(json.encodeToString(JsonObject.serializer(), result), ContentType.Application.Json)
}

private suspend fun ApplicationCall.respondError(
    json: Json,
    message: String?,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
) {
    val body = buildJsonObject { put("error", message ?: "bad request") }
    respondText(json.encodeToString(JsonObject.serializer(), body), ContentType.Application.Json, status)
}

/** A thin view over a parsed JSON request object with typed field access. */
private class RequestBody(
    private val obj: JsonObject,
) {
    fun string(key: String): String =
        optString(key) ?: throw IllegalArgumentException("missing required field '$key'")

    /** The string at [key], `null` when absent; a present non-string value is an error, not "absent". */
    fun optString(key: String): String? {
        val element = obj[key] ?: return null
        val primitive = element as? JsonPrimitive
        require(primitive != null && primitive.isString) { "field '$key' must be a string" }
        return primitive.content
    }

    fun getValue(key: String) = obj[key] ?: throw IllegalArgumentException("missing required field '$key'")
}

private fun PasskeyCeremonyRequest.withCeremonyId(json: Json): JsonObject {
    val options = json.parseToJsonElement(optionsJson).jsonObject
    return buildJsonObject {
        put("ceremonyId", ceremonyId)
        options["publicKey"]?.let { put("publicKey", it) }
    }
}
