# passkeys-server sample

A minimal, runnable WebAuthn Relying Party built on the **`passkeys-server`**
module — the server half that mints ceremony options and verifies the responses
this project's clients produce.

## Run

```bash
./gradlew :sample:server:run
```

Open <http://localhost:8080> in a browser with a platform authenticator (Touch
ID, Windows Hello, Android), type a username, then **Register** and
**Authenticate**. Credentials are stored in memory and vanish on restart.

## Using the library

Add the dependency (JVM / Ktor server):

```kotlin
implementation("io.github.androidpoet:passkeys-server:<version>")
```

Wire a Relying Party and mount the routes:

```kotlin
val relyingParty = PasskeyRelyingParty(
    config = PasskeyServerConfig(
        rpId = "example.com",
        rpName = "Example",
        origins = setOf("https://example.com"),
    ),
    credentials = InMemoryPasskeyCredentialStore(), // swap for your database
    challenges = InMemoryPasskeyChallengeStore(),   // swap for a short-TTL store
)

routing {
    passkeyRoutes(relyingParty) // mounts the four endpoints under /passkeys
}
```

### Endpoints

| Method & path | Body | Returns |
| --- | --- | --- |
| `POST /passkeys/register/begin` | `{"handle","name","displayName"}` | `{"ceremonyId","publicKey":{…}}` |
| `POST /passkeys/register/finish` | `{"ceremonyId","response":{…}}` | registered credential summary |
| `POST /passkeys/login/begin` | `{"username"?}` | `{"ceremonyId","publicKey":{…}}` |
| `POST /passkeys/login/finish` | `{"ceremonyId","response":{…}}` | authentication outcome |

The `publicKey` envelope is the `navigator.credentials` request JSON the
passkeys-kmp clients accept directly as their `requestJson`; send each client's
response `rawJson` back as `response`.

### Bring your own storage

`PasskeyCredentialStore` and `PasskeyChallengeStore` are the only persistence
contracts. The in-memory implementations are for demos and tests — back them
with your real database and a short-TTL challenge store (e.g. Redis) in
production. The library never assumes a storage engine.
