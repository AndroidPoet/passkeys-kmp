package io.github.androidpoet.passkeys.sample.server

import io.github.androidpoet.passkeys.server.InMemoryPasskeyChallengeStore
import io.github.androidpoet.passkeys.server.InMemoryPasskeyCredentialStore
import io.github.androidpoet.passkeys.server.PasskeyRelyingParty
import io.github.androidpoet.passkeys.server.PasskeyServerConfig
import io.github.androidpoet.passkeys.server.newAccountsOnlyEnrollment
import io.github.androidpoet.passkeys.server.passkeyRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing

/**
 * Minimal runnable WebAuthn Relying Party demo for `passkeys-server`.
 *
 * Run with `./gradlew :sample:server:run`, then open http://localhost:8080 in a
 * browser with a platform authenticator and register, then authenticate, a
 * passkey. Credentials live in memory only — they vanish on restart.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::passkeyDemo).start(wait = true)
}

private const val DEFAULT_PORT = 8080

private fun Application.passkeyDemo() {
    val credentials = InMemoryPasskeyCredentialStore()
    val relyingParty =
        PasskeyRelyingParty(
            config =
                PasskeyServerConfig(
                    rpId = "localhost",
                    rpName = "passkeys-kmp sample",
                    origins = setOf("http://localhost:$DEFAULT_PORT"),
                ),
            credentials = credentials,
            challenges = InMemoryPasskeyChallengeStore(),
        )

    routing {
        // The server picks the account (and mints its handle); the browser only
        // proposes a name. Existing names are refused — adding a second passkey
        // belongs behind your own signed-in flow.
        passkeyRoutes(relyingParty, enrollment = newAccountsOnlyEnrollment(credentials))
        staticResources("/", "static")
    }
}
