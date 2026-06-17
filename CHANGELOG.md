# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Added

- Repository foundation: `LICENSE` (MIT), this changelog, `.editorconfig`,
  Spotless (ktlint) formatting, detekt static analysis, Binary Compatibility
  Validator with a committed public-API dump, Kover coverage, Dokka API docs,
  and GitHub Actions CI (quality gates + per-platform compilation).

## 0.1.0

### Added

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
