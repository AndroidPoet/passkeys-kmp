# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.4.0 - 2026-11-19

Brings the Apple clients to parity with what Credential Manager offers on
Android, and adds the two non-native ways to use passkeys as separate modules.

### Added

- `PasskeyAuthenticationOptions.preferImmediatelyAvailableCredentials`: show the
  sign-in sheet only when a passkey is on the device right now. Honoured on
  Android, iOS and macOS.
- Keyboard-autofill sign-in: `AndroidPasskeyClient.authenticateWithAutofill(view, options)`
  (Android 15+) and `IosPasskeyClient.authenticateWithAutofill(options)`.
- `PasskeySignals` (WebAuthn Signal API) with `AndroidPasskeySignals`. Apple's
  equivalent is Swift-only; the usage guide shows how to implement the interface
  in an iOS app.

- `passkeys-auth-session`: `WebAuthSession` runs a web sign-in page or an
  OAuth / OIDC flow in the system authentication browser and returns the
  redirect URL — an Auth Tab on Android (`AndroidWebAuthSession`),
  `ASWebAuthenticationSession` on iOS (`IosWebAuthSession`). Supports custom
  scheme and verified `https` redirects, and ephemeral sessions.
- `passkeys-webview` (Android): `WebView.enablePasskeys()` lets a first-party
  page in a `WebView` use passkeys through `navigator.credentials`.

### Fixed

- iOS and macOS now honour `PasskeyCreationOptions.preferImmediatelyAvailableCredentials`
  and `isConditionalCreateRequest` (conditional create needs iOS 18 / macOS 15;
  older versions fail with `Unsupported`). Both were previously ignored.
- iOS / macOS 26: "device not set up for passkeys" (`ASAuthorizationError` 1010)
  maps to `Unsupported` instead of `Unexpected`.

### Changed

- `PasskeyAuthenticationOptions` gained a constructor parameter: source
  compatible, but code compiled against 0.3.0 must be recompiled.
- Android Gradle Plugin 8.7.3 → 8.9.1, required by `androidx.browser` 1.9.
  `passkeys-auth-session` needs compileSdk 36 in the consuming app.

## 0.3.0 - 2026-11-07

Security and correctness release following an external audit of 0.2.0. Several
server APIs changed shape; the client surface gained one failure case and lost
two nullable fields.

### Changed — `passkeys-server` (breaking)

- `passkeyRoutes()` now requires a `PasskeyEnrollmentPolicy`. The account a
  `register/begin` enrolls for is resolved **server-side** (from your session or
  sign-up flow) and never from the request body, which previously let any caller
  enroll a passkey on any `handle`/`name`. `newAccountsOnlyEnrollment()` covers
  passkey-first sign-up demos; a refused enrollment answers `403`.
- Origins match exactly, port included. The former implicit `allowOriginPort`
  relaxation is now an explicit, off-by-default `PasskeyServerConfig` option.
- Pending ceremonies carry their kind and an expiry (`challengeTimeout`, default
  5 min). `finish` rejects expired, wrong-kind and replayed ceremonies;
  `InMemoryPasskeyChallengeStore` expires entries and caps its size.
- `PasskeyCredentialStore.save` is insert-only (throws on a duplicate id) and
  `updateSignatureCount` is replaced by `recordAssertion(...)`, a compare-and-set
  on the counter so two concurrent assertions can no longer regress it.
- `PasskeyServerConfig.userVerification` (`REQUIRED` is enforced by the verifier;
  per-ceremony override on `beginAuthentication`). `PasskeyAuthenticationOutcome`
  reports `userVerified`, `backupEligible`, `backedUp`; `StoredPasskey` persists
  BE/BS flags and transports so the verifier's backup-eligibility check works.
- `PasskeyUser.handle` must be canonical unpadded base64url of 1–64 bytes.
  Routes reject non-string optional fields and bodies over 64 KiB (`413`).
- Yubico's library is an `implementation` dependency; it never appeared in the
  public API.

### Changed — `passkeys` (breaking)

- `PasskeyAuthenticationResponse.authenticatorData` and `signature` are
  non-null: an assertion without them is unusable and now fails as
  `InvalidPayload` instead of surfacing as a `Success`.
- New `PasskeyException.CredentialExcluded` (code `1008`) for "an
  `excludeCredentials` entry matched" — previously reported as `Unsupported`
  on Apple (error 1006 is `matchedExcludedCredential`) and as a generic DOM
  error elsewhere.

### Fixed

- Coroutine cancellation is propagated on every platform instead of being
  swallowed into a `Failure`; cancelling the caller now dismisses the system
  sheet on Apple platforms, aborts the browser ceremony (`AbortController`) and
  cancels the Windows Hello dialog.
- Apple: one controller/delegate/presentation provider per ceremony, strongly
  held for its duration and completed at most once — concurrent ceremonies no
  longer cross-wire callbacks. OS-version-gated getters (`attachment`,
  `largeBlob`, `prf`) are only read where they exist, so plain ceremonies no
  longer crash on iOS 16.0–16.5 / macOS 13.0–13.4. Empty binary inputs (e.g. an
  empty largeBlob write) no longer fail. `excludeCredentials` (iOS 17.4+ /
  macOS 13.5+), PRF `evalByCredential` and registration PRF results are
  forwarded; `cross-platform` attachment and non-ES256-only `pubKeyCredParams`
  are rejected up front.
- Windows: version-gated output fields are read only when the returned struct
  has them (previously read before the version check). `pubKeyCredParams`,
  resident-key, attachment, `excludeCredentials` and `timeout` are honoured.
- Linux: `pubKeyCredParams` (ES256/EdDSA/RS256), user verification, resident
  key, `excludeCredentials` and `timeout` are honoured; `platform`-only requests
  are refused; the CBOR unwrap validates the declared length.
- Every native serializer emits `clientExtensionResults` (as `{}` when empty),
  which the W3C JSON shape — and `passkeys-server` — requires.
- JVM desktop: requests are normalized through the common mapper before JNI (a
  `{"publicKey":…}` envelope from `passkeys-server` now works); native errors
  are JSON-parsed and mapped by NSError code; strings cross JNI as UTF-8 byte
  arrays (modified UTF-8 corrupted emoji in display names); the extracted
  native library is cached by content hash and published atomically.
- Compose: the Android client is keyed by its `Activity`; iOS resolves the
  presenting window from the hosting view controller at ceremony time; JVM
  anchors to the hosting Compose window.

## 0.2.0 - 2026-06-19

Adds the server half of passkeys as a new published module.

### Added

- **`passkeys-server`** — an installable Kotlin/JVM WebAuthn Relying Party that
  mints ceremony options and verifies the responses the clients produce. Wraps
  [java-webauthn-server](https://github.com/Yubico/java-webauthn-server) behind a
  thin, explicit API (`PasskeyRelyingParty` with begin/finish registration and
  authentication); the underlying library is kept internal. Storage is
  bring-your-own via `PasskeyCredentialStore` / `PasskeyChallengeStore` SPIs with
  in-memory defaults, and `passkeyRoutes()` mounts the four ceremony endpoints on
  Ktor. A full register→authenticate round-trip is verified end-to-end against an
  in-process software authenticator. A runnable demo with a browser test page
  lives in `:sample:server`.

`passkeys` and `passkeys-compose` are unchanged in this release.

## 0.1.2 - 2026-06-19

First release of the macOS-native JVM desktop backend and the Linux/Windows
native targets to reach Maven Central. The 0.1.1 tag was never published — its
release build failed because the macOS CI/publish runners could not resolve the
`libfido2` headers the `linuxX64` cinterop needs, and the JVM desktop test
asserted a host-specific failure type.

### Fixed

- **CI / publish** — install `libfido2` (Homebrew) on the macOS runners that run
  `apiCheck` and `publishAndReleaseToMavenCentral`. Both build the `linuxX64`
  klib, whose `libfido2` cinterop needs `fido.h`; without it the build failed
  with `'fido.h' file not found`.
- **Linux native cinterop** — `fido.h` pulls in `<openssl/...>`, which
  transitively includes `<openssl/opensslconf.h>`. On Debian/Ubuntu multiarch
  that generated header lives under `/usr/include/x86_64-linux-gnu/openssl`,
  which the cinterop clang indexer does not search by default, so the `linuxX64`
  cinterop failed with `'openssl/opensslconf.h' file not found`. Add the
  multiarch include path to `libfido2.def` (`compilerOpts.linux_x64`) and install
  `libssl-dev` explicitly on the Linux runner.
- **JVM desktop test** — `JvmPasskeyClientTest` now asserts the per-host failure
  contract. On macOS the bundled native backend is present, so a minimal/invalid
  request fails loud as `PasskeyException.Unexpected`; on hosts without an
  in-process authenticator it short-circuits to `PasskeyException.Unsupported`.
  The test previously assumed `Unsupported` unconditionally.

## 0.1.0 - 2026-06-18

First public release. Published to Maven Central as
`io.github.androidpoet:passkeys:0.1.0` and `io.github.androidpoet:passkeys-compose:0.1.0`.

### Added

- **Windows** — `WindowsPasskeyClient` drives the native Windows Hello passkey
  experience via the OS WebAuthn API (`webauthn.dll`, Windows 10 1903+):
  fingerprint / face / PIN for the platform authenticator, or a tapped USB/NFC
  security key. Nothing is bundled — it links the OS-provided import library.
  The system sheet is parented to a caller-supplied window handle (`HWND` as a
  `Long`), falling back to the foreground/console window. Uses the ready-made
  response JSON on newer Windows builds and otherwise assembles the WebAuthn
  registration/assertion JSON from the native structs.
- **Linux** — `LinuxPasskeyClient` drives roaming USB/NFC FIDO2 security keys via
  a libfido2 cinterop binding (registration assembles the CBOR `attestationObject`
  from the authenticator's pieces; assertion unwraps libfido2's CBOR authenticator
  data). Linux has no OS platform/biometric or hybrid authenticator, so those
  requests fail with a typed `PasskeyException`; `LinuxPasskeyClient.capabilities`
  reports what is supported. Requires the libfido2 shared library and udev rules.
- **JVM desktop** — `JvmPasskeyClient` fails loud with
  `PasskeyException.Unsupported` (there is no in-process authenticator on JVM
  desktop), plus `PasskeyBrowserHandoff` to open the relying party page in the
  system browser as the supported desktop flow.
- **Browser (Wasm)** — real passkey ceremonies via `navigator.credentials`
  (`WasmJsPasskeyClient`). Uses the browser's own WebAuthn JSON serialization
  (`parseCreationOptionsFromJSON` / `parseRequestOptionsFromJSON` / `toJSON`,
  Baseline March 2025) so base64url ↔ `ArrayBuffer` conversion is handled
  natively; DOMException names map to the shared `PasskeyException` hierarchy.
- **macOS** — real passkey ceremonies via AuthenticationServices
  (`MacosPasskeyClient`, macOS 13+). The iOS and macOS clients now share one
  `ApplePasskeyClient` ceremony implementation; the only per-platform
  differences are the presentation anchor (`NSWindow` vs `UIWindow`) and the
  OS-version gates for the `largeBlob`/`prf` extensions (macOS 14/15+).
- Repository foundation: `LICENSE` (MIT), this changelog, `.editorconfig`,
  Spotless (ktlint) formatting, detekt static analysis, Binary Compatibility
  Validator with a committed public-API dump, Kover coverage, Dokka API docs,
  and GitHub Actions CI (quality gates + per-platform compilation).
- Initial Kotlin Multiplatform passkeys SDK with a common `PasskeyClient`
  contract (`create`/`authenticate` returning `PasskeyResult`), the
  `PasskeyException` hierarchy, and WebAuthn payload/response models.
- **Android** — real passkey ceremonies via AndroidX Credential Manager
  (`AndroidPasskeyClient`), including conditional create.
- **iOS** — real passkey ceremonies via AuthenticationServices
  (`IosPasskeyClient`), including `largeBlob` (iOS 17+) and `prf` (iOS 18+)
  extensions.
- Common payload normalization (`publicKey` envelope unwrapping, base64url) and
  response mapping shared across platforms.
