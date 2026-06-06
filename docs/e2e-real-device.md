# Real-device passkey E2E checklist

Passkey E2E cannot be proven on an emulator-only or simulator-only setup. Run this checklist against the real relying party domain, production-equivalent app identifiers, and a backend that verifies WebAuthn challenges and responses.

## Inputs

- Relying party ID: `example.com`
- Android package: `com.example.app`
- Android signing certificate SHA-256: `AA:BB:...`
- Apple application identifier: `TEAMID.com.example.app`
- Backend registration challenge endpoint: `POST /webauthn/register/options`
- Backend registration verification endpoint: `POST /webauthn/register/verify`
- Backend authentication challenge endpoint: `POST /webauthn/authenticate/options`
- Backend authentication verification endpoint: `POST /webauthn/authenticate/verify`

## Android domain association

Publish Digital Asset Links at:

```text
https://example.com/.well-known/assetlinks.json
```

Minimum statement:

```json
[
  {
    "relation": ["delegate_permission/common.get_login_creds"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.example.app",
      "sha256_cert_fingerprints": ["AA:BB:CC:DD:EE:FF"]
    }
  }
]
```

If the app also uses verified app links, include the app-link relation too:

```json
"delegate_permission/common.handle_all_urls"
```

Verify on a real Android device:

```sh
curl -i https://example.com/.well-known/assetlinks.json
adb shell pm verify-app-links --re-verify com.example.app
adb shell pm get-app-links com.example.app
```

Expected: the relying party host is `verified`, and Credential Manager can create and retrieve a passkey for `example.com`.

## Apple associated domain

Add the entitlement:

```text
webcredentials:example.com
```

Publish the Apple app site association file at one of:

```text
https://example.com/.well-known/apple-app-site-association
https://example.com/apple-app-site-association
```

Minimum file:

```json
{
  "webcredentials": {
    "apps": ["TEAMID.com.example.app"]
  }
}
```

Serve it with `application/json` and no `.json` extension.

Verify on a real iPhone:

```sh
curl -i https://example.com/.well-known/apple-app-site-association
```

Expected: registration and assertion requests do not fail with an associated-domain error.

## Backend verification contract

Registration options must include:

```json
{
  "rp": { "id": "example.com", "name": "Example" },
  "user": { "id": "base64url-user-id", "name": "user@example.com", "displayName": "User" },
  "challenge": "base64url-challenge",
  "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
  "authenticatorSelection": { "userVerification": "preferred" }
}
```

Authentication options must include:

```json
{
  "rpId": "example.com",
  "challenge": "base64url-challenge",
  "allowCredentials": [{ "type": "public-key", "id": "base64url-credential-id" }],
  "userVerification": "preferred"
}
```

The SDK returns WebAuthn-compatible `rawJson`. Send it unchanged to the matching verification endpoint. The backend must verify challenge, origin, RP ID hash, signature, sign count behavior, credential ID ownership, and user verification requirements.

## Extension checks

Android conditional creation:

- Use `PasskeyCreationOptions(isConditionalCreateRequest = true)` only after a successful password sign-in.
- Confirm the app uses AndroidX Credentials `1.6.0` or newer.

Apple largeBlob:

- Registration: include `"extensions": { "largeBlob": { "support": "preferred" } }`.
- Authentication read: include `"extensions": { "largeBlob": { "read": true } }`.
- Authentication write: include `"extensions": { "largeBlob": { "write": "base64url-bytes" } }`.
- Requires iOS 17+.

Apple PRF:

- Registration support check: include `"extensions": { "prf": {} }`.
- Registration/authentication evaluation: include `"extensions": { "prf": { "eval": { "first": "base64url-salt", "second": "base64url-salt" } } }`.
- Requires iOS 18+.

## Pass criteria

- Android creates a passkey, backend verifies registration, Android authenticates with the same credential, backend verifies assertion.
- Android conditional creation succeeds only in the post-password-login flow and fails cleanly elsewhere.
- iOS creates a passkey, backend verifies registration, iOS authenticates with the same credential, backend verifies assertion.
- On supported Apple OS versions, requested `clientExtensionResults` are present in the SDK response and backend receives them unchanged.
- On unsupported Apple OS versions, the SDK returns `PasskeyException.Unsupported` before presenting native UI.
