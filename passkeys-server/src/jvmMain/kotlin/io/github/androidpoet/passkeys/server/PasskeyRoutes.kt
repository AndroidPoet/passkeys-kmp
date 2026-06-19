package io.github.androidpoet.passkeys.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
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

/**
 * Mounts the four passkey ceremony endpoints under [basePath], delegating to
 * [relyingParty]:
 *
 * - `POST {basePath}/register/begin` — body `{"handle","name","displayName"}`,
 *   returns `{"ceremonyId","publicKey":{...}}`.
 * - `POST {basePath}/register/finish` — body `{"ceremonyId","response":{...}}`,
 *   returns the registered credential summary.
 * - `POST {basePath}/login/begin` — body `{"username"?}`, returns
 *   `{"ceremonyId","publicKey":{...}}`.
 * - `POST {basePath}/login/finish` — body `{"ceremonyId","response":{...}}`,
 *   returns the authentication outcome.
 *
 * The `publicKey` envelopes are the `navigator.credentials` request JSON this
 * project's clients accept directly. Verification failures and malformed
 * requests respond `400` with a JSON `{"error": ...}` body. This helper is
 * intentionally thin: add your own authentication, rate limiting, and session
 * handling around it.
 */
public fun Route.passkeyRoutes(
    relyingParty: PasskeyRelyingParty,
    basePath: String = "/passkeys",
) {
    val json = Json { ignoreUnknownKeys = true }

    route(basePath) {
        post("/register/begin") {
            call.handle(json) { body ->
                val user =
                    PasskeyUser(
                        handle = body.string("handle"),
                        name = body.string("name"),
                        displayName = body.optString("displayName") ?: body.string("name"),
                    )
                relyingParty.beginRegistration(user).withCeremonyId(json)
            }
        }

        post("/register/finish") {
            call.handle(json) { body ->
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
                }
            }
        }

        post("/login/begin") {
            call.handle(json) { body ->
                relyingParty.beginAuthentication(body.optString("username")).withCeremonyId(json)
            }
        }

        post("/login/finish") {
            call.handle(json) { body ->
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
                }
            }
        }
    }
}

/**
 * Runs [block] with the parsed request body and writes its JSON result, mapping
 * verification failures and malformed/missing input to a `400` JSON error.
 */
private suspend fun ApplicationCall.handle(json: Json, block: suspend (RequestBody) -> JsonObject) {
    val result =
        try {
            val body = RequestBody(json.parseToJsonElement(receiveText()).jsonObject)
            block(body)
        } catch (e: PasskeyVerificationException) {
            return respondError(json, e.message)
        } catch (e: IllegalArgumentException) {
            return respondError(json, e.message)
        } catch (e: SerializationException) {
            return respondError(json, "malformed request body: ${e.message}")
        }
    respondText(json.encodeToString(JsonObject.serializer(), result), ContentType.Application.Json)
}

private suspend fun ApplicationCall.respondError(json: Json, message: String?) {
    val body = buildJsonObject { put("error", message ?: "bad request") }
    respondText(
        json.encodeToString(JsonObject.serializer(), body),
        ContentType.Application.Json,
        HttpStatusCode.BadRequest,
    )
}

/** A thin view over a parsed JSON request object with typed field access. */
private class RequestBody(
    private val obj: JsonObject,
) {
    fun string(key: String): String =
        optString(key) ?: throw IllegalArgumentException("missing required field '$key'")

    fun optString(key: String): String? =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun getValue(key: String) = obj[key] ?: throw IllegalArgumentException("missing required field '$key'")
}

private fun PasskeyCeremonyRequest.withCeremonyId(json: Json): JsonObject {
    val options = json.parseToJsonElement(optionsJson).jsonObject
    return buildJsonObject {
        put("ceremonyId", ceremonyId)
        options["publicKey"]?.let { put("publicKey", it) }
    }
}
