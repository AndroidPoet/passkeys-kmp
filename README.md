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
- macOS, JVM, Linux, Wasm: common models, payload normalization, and response helpers. No native passkey UI client yet.

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

Apple extension support:

- `largeBlob` registration/authentication is wired on iOS 17+.
- `prf` registration/authentication is wired on iOS 18+.
- Unsupported OS versions fail with `PasskeyException.Unsupported` before native UI is shown.
- Requested extension outputs are preserved in `clientExtensionResultsJson` and in `rawJson.clientExtensionResults`.

## Verification

```sh
./gradlew :passkeys:allTests :passkeys:testDebugUnitTest
./gradlew :passkeys:lintRelease :passkeys:assemble :passkeys:publishToMavenLocal
```
