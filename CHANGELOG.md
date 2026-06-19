# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
