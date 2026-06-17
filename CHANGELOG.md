# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
