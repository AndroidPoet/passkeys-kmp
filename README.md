<p align="center">
  <img src="art/passkeys-kmp.png" alt="passkeys-kmp — Simple. Secure. Passwordless." width="560"/>
</p>

<h1 align="center">Passkeys KMP</h1>

<p align="center"><b>Simple. Secure. Passwordless.</b></p>

<p align="center">
A Kotlin Multiplatform passkeys (WebAuthn) SDK with <b>one common API</b> and real native
authenticators on Android, iOS, macOS, Windows, Linux, browser (Wasm), and JVM/Compose Desktop.
</p>

## One API, every platform

Every platform speaks the same `PasskeyClient` contract — `create` / `authenticate`
returning a `PasskeyResult` — so your call site never changes:

```kotlin
val passkeys: PasskeyClient = rememberPasskeyClient()   // Compose: resolves the platform client + anchor

when (val result = passkeys.create(registrationOptionsJson)) {
    is PasskeyResult.Success -> sendToBackend(result.value.rawJson) // verify on your server
    is PasskeyResult.Failure -> handle(result.error.code, result.error.message)
}
```

`rememberPasskeyClient()` (from `:passkeys-compose`) is the single entry point for
Compose Multiplatform apps — identical on Android, iOS, desktop, and web.

## Install

```kotlin
implementation("io.github.androidpoet:passkeys:0.1.0")          // core SDK
implementation("io.github.androidpoet:passkeys-compose:0.1.0")  // rememberPasskeyClient() (Compose MP)
```

## Platform support

| Platform | Authenticator | Backed by | Setup |
| --- | --- | --- | --- |
| Android | Fingerprint / face / PIN | Credential Manager (API 28+) | Digital Asset Links (`assetlinks.json`) |
| iOS 16+ | Face ID / Touch ID | AuthenticationServices | Associated Domains (`webcredentials:`) |
| macOS 13+ | Touch ID | AuthenticationServices | Associated Domains (`webcredentials:`) |
| JVM / Compose Desktop | Touch ID (macOS) | bundled Swift + JNI bridge | signed `.app` + entitlement (see below) |
| Browser (Wasm) | Platform / security key | `navigator.credentials` | secure context (HTTPS / `localhost`) |
| Windows 10 1903+ | Windows Hello / security key | OS WebAuthn (`webauthn.dll`) | top-level `HWND` |
| Linux | Roaming USB/NFC security key only | libfido2 | `libfido2` + udev rules |

> Run real-device verification with domain association + backend challenge checks
> before release — see [docs/e2e-real-device.md](docs/e2e-real-device.md).

## Without Compose — construct the client directly

`rememberPasskeyClient()` just wires the right constructor; the only platform
difference is the presentation anchor each OS requires. Same `create` /
`authenticate` API on all of them.

| Platform | Constructor |
| --- | --- |
| Android | `AndroidPasskeyClient(activity)` |
| iOS | `IosPasskeyClient(uiWindow)` |
| macOS (Kotlin/Native) | `MacosPasskeyClient(nsWindow)` |
| JVM desktop | `JvmPasskeyClient { window.windowHandle }` |
| Browser (Wasm) | `WasmJsPasskeyClient()` |
| Windows | `WindowsPasskeyClient(hwnd)` |
| Linux | `LinuxPasskeyClient()` |

`registrationOptionsJson` / `authenticationOptionsJson` accept the standard
WebAuthn JSON (or a `{ "publicKey": … }` wrapper). Responses are returned as
`rawJson` for your server to verify.

<details>
<summary><b>JVM / Compose Desktop — native macOS passkeys</b></summary>

On macOS, `JvmPasskeyClient` drives the real Touch ID ceremony via a bundled
native backend (`libPasskeysNative.dylib`, a Swift + JNI shim over
AuthenticationServices, built from `passkeys/src/jvmMain/native/macos`).

A passkey ceremony **only runs from a signed `.app`** carrying the
`com.apple.developer.associated-domains` (`webcredentials:<rpId>`) entitlement —
and because that entitlement is *restricted*, the signature must embed a
provisioning profile granting it for your App ID. A bare `java -jar` from the
terminal will not launch. On Windows/Linux (or if the native backend can't load)
the client fails loud — use browser handoff instead:

```kotlin
PasskeyBrowserHandoff.open("https://your-rp.example/passkey/sign-in")
```
</details>

<details>
<summary><b>Apple extensions (iOS & macOS)</b></summary>

- `largeBlob` registration/authentication: iOS 17+ / macOS 14+
- `prf` registration/authentication: iOS 18+ / macOS 15+
- Unsupported OS versions fail with `PasskeyException.Unsupported` before any UI
- Extension outputs are preserved in `rawJson.clientExtensionResults`
</details>

<details>
<summary><b>Linux — security keys only</b></summary>

Linux has no platform/biometric authenticator, so `LinuxPasskeyClient` supports
roaming USB/NFC security keys via libfido2 (`LinuxPasskeyClient.capabilities` →
roaming=true, platform=false, hybrid=false). Requires `libfido2-dev` /
`libfido2-devel` and udev rules granting non-root access. Platform and
phone/hybrid passkeys fail with a typed `PasskeyException`.
</details>

## Sample — one codebase, every platform

`:sample:composeApp` is a single Compose Multiplatform app: the whole UI and
client setup live in `commonMain`, and each platform's entry point is just
`App()`. The sample carries no real domain — supply your own:

```sh
# Android (certified device/emulator)
./gradlew :sample:composeApp:installDebug -PpasskeysSampleRpId=your-domain.com

# macOS desktop (run to see the UI; create/authenticate need a signed .app)
./gradlew :sample:composeApp:run -PpasskeysSampleRpId=your-domain.com -PpasskeysSampleBundleId=com.your.app
```

`passkeysSampleRpId` / `passkeysSampleBundleId` configure the relying party and
bundle id. For iOS, set the `webcredentials:` domain in
`sample/composeApp/iosApp/iosApp/iosApp.entitlements` and pass
`PRODUCT_BUNDLE_IDENTIFIER` / `DEVELOPMENT_TEAM` to `xcodebuild`. Publish matching
`assetlinks.json` (Android) and `apple-app-site-association` (Apple) under
`/.well-known/` on your domain. A browser demo lives in `:sample:web`.

## Verification

```sh
./gradlew :passkeys:allTests :passkeys:testDebugUnitTest
./gradlew spotlessCheck detekt apiCheck
./gradlew :passkeys:assemble :passkeys:publishToMavenLocal
```
