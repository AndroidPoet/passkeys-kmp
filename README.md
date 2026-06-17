# Passkeys KMP

Kotlin Multiplatform passkeys SDK with common WebAuthn models and native passkey clients for Android and Apple platforms.

## Modules

- `:passkeys` - common WebAuthn payload/result contracts plus Android and iOS passkey operations.

## Install

```kotlin
implementation("io.github.androidpoet:passkeys:0.1.0")
```

Android passkeys require API 28+ and Digital Asset Links configured for your relying party domain.
iOS passkeys require iOS 16+ and an Associated Domains entitlement with `webcredentials:your-domain.com`.
The Android implementation is pinned to stable AndroidX Credentials `1.6.0`, including conditional passkey creation support.

## Platform Support

- Android: real passkey create/authenticate through AndroidX Credential Manager.
- iOS: real passkey create/authenticate through AuthenticationServices.
- macOS: real passkey create/authenticate through AuthenticationServices (macOS 13+). Shares the iOS ceremony; the system Touch ID sheet is parented to your `NSWindow`.
- Browser (Wasm): real passkey create/authenticate through `navigator.credentials`, using the browser's own WebAuthn JSON serialization (Baseline March 2025).
- JVM desktop: no in-process authenticator exists; `JvmPasskeyClient` fails loud with `PasskeyException.Unsupported`. Use `PasskeyBrowserHandoff` to complete the ceremony in the system browser.
- Linux: common models, payload normalization, and response helpers. No native passkey UI client yet.

Real device verification requires domain association and backend challenge verification. Use [docs/e2e-real-device.md](docs/e2e-real-device.md) before release.

## Android Usage

```kotlin
val passkeys = AndroidPasskeyClient(activity)

when (val result = passkeys.create(registrationOptionsJson)) {
    is PasskeyResult.Success -> {
        val responseJson = result.value.rawJson
        // Send responseJson to your backend for WebAuthn registration verification.
    }
    is PasskeyResult.Failure -> {
        // Inspect result.error.code and result.error.message.
    }
}

when (val result = passkeys.authenticate(authenticationOptionsJson)) {
    is PasskeyResult.Success -> {
        val responseJson = result.value.rawJson
        // Send responseJson to your backend for WebAuthn assertion verification.
    }
    is PasskeyResult.Failure -> {
        // Inspect result.error.code and result.error.message.
    }
}
```

Use `PasskeyCreationOptions(preferImmediatelyAvailableCredentials = true)` only for opportunistic creation attempts where the platform should fail quickly instead of showing UI for remote or unavailable credentials. Use `isConditionalCreateRequest = true` only after a successful password sign-in flow where Credential Manager can create a passkey without the normal bottom sheet.

`registrationOptionsJson` may be either the direct WebAuthn `PublicKeyCredentialCreationOptions` JSON or a `{ "publicKey": ... }` wrapper. `authenticationOptionsJson` may be either the direct `PublicKeyCredentialRequestOptions` JSON or a `{ "publicKey": ... }` wrapper.

## iOS Usage

```kotlin
val passkeys = IosPasskeyClient(window)

when (val result = passkeys.create(registrationOptionsJson)) {
    is PasskeyResult.Success -> {
        val responseJson = result.value.rawJson
        // Send responseJson to your backend for WebAuthn registration verification.
    }
    is PasskeyResult.Failure -> {
        // Inspect result.error.code and result.error.message.
    }
}
```

## macOS Usage

```kotlin
val passkeys = MacosPasskeyClient(window) // an NSWindow to anchor the system sheet

when (val result = passkeys.authenticate(authenticationOptionsJson)) {
    is PasskeyResult.Success -> {
        val responseJson = result.value.rawJson
        // Send responseJson to your backend for WebAuthn assertion verification.
    }
    is PasskeyResult.Failure -> {
        // Inspect result.error.code and result.error.message.
    }
}
```

macOS 13 (Ventura)+ and an Associated Domains entitlement (`webcredentials:your-domain.com`) are required. `create` works the same way as on iOS.

Apple extension support (iOS and macOS share one implementation):

- `largeBlob` registration/authentication is wired on iOS 17+ / macOS 14+.
- `prf` registration/authentication is wired on iOS 18+ / macOS 15+.
- Unsupported OS versions fail with `PasskeyException.Unsupported` before native UI is shown.
- Requested extension outputs are preserved in `clientExtensionResultsJson` and in `rawJson.clientExtensionResults`.

## Browser (Wasm) Usage

```kotlin
val passkeys = WasmJsPasskeyClient()

when (val result = passkeys.authenticate(authenticationOptionsJson)) {
    is PasskeyResult.Success -> {
        val responseJson = result.value.rawJson
        // Send responseJson to your backend for WebAuthn assertion verification.
    }
    is PasskeyResult.Failure -> {
        // Inspect result.error.code and result.error.message.
    }
}
```

Runs in a secure context (HTTPS or `localhost`). Options and responses cross the
JS boundary as JSON via `PublicKeyCredential.parseCreationOptionsFromJSON` /
`parseRequestOptionsFromJSON` and `toJSON()`, so base64url ↔ `ArrayBuffer`
conversion is handled by the browser. Browsers without those methods fail with
`PasskeyException.Unsupported`.

## JVM Desktop

A plain JVM desktop app cannot drive a platform authenticator in-process without
per-OS native bindings, and macOS cannot present its passkey sheet headlessly,
so `JvmPasskeyClient` fails loud:

```kotlin
val passkeys = JvmPasskeyClient()
// create()/authenticate() always return PasskeyResult.Failure(Unsupported)

// Supported pattern — hand off to the system browser, which has a real authenticator:
PasskeyBrowserHandoff.open("https://your-rp.example/passkey/sign-in")
```

Your web page completes the WebAuthn ceremony and returns the session to the
desktop app (e.g. via a loopback redirect your app listens on).

## Verification

```sh
./gradlew :passkeys:allTests :passkeys:testDebugUnitTest
./gradlew :passkeys:lintRelease :passkeys:assemble :passkeys:publishToMavenLocal
```
