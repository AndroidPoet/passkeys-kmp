// ApplePasskeyBridge.swift — native macOS passkey ceremony for the JVM desktop client.
//
// Mirrors the Kotlin/Native `ApplePasskeyClient` (appleMain) but is callable from
// the JVM via JNI. Exposes three plain C functions (see `@_cdecl`) that the
// `jni_bridge.c` shim registers as JNI native methods:
//
//   char* passkeyCreate(const char* requestJson, int64_t windowHandle)
//   char* passkeyAuthenticate(const char* requestJson, int64_t windowHandle)
//   void  passkeyFreeString(char* ptr)
//
// Each ceremony function runs the `ASAuthorizationController` flow synchronously
// (blocks the calling JNI thread on a semaphore while the controller runs on the
// main queue) and returns a heap-allocated C string the caller must free with
// `passkeyFreeString`. The returned string is always JSON: on success the
// standard WebAuthn credential JSON, on failure `{"__error__":{...}}`.

import AppKit
import AuthenticationServices
import Foundation

// MARK: - Base64URL helpers

private func base64UrlDecode(_ string: String) -> Data? {
    var s = string.replacingOccurrences(of: "-", with: "+")
        .replacingOccurrences(of: "_", with: "/")
    let pad = s.count % 4
    if pad != 0 { s += String(repeating: "=", count: 4 - pad) }
    return Data(base64Encoded: s)
}

private func base64UrlEncode(_ data: Data) -> String {
    data.base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
}

// MARK: - JSON helpers

private func jsonString(_ object: Any) -> String {
    guard let data = try? JSONSerialization.data(withJSONObject: object),
          let str = String(data: data, encoding: .utf8) else {
        return "{\"__error__\":{\"code\":\"serialize\",\"message\":\"failed to serialize response\"}}"
    }
    return str
}

private func errorJson(code: String, message: String) -> String {
    jsonString(["__error__": ["code": code, "message": message]])
}

private func cString(_ string: String) -> UnsafeMutablePointer<CChar> {
    strdup(string)!
}

// MARK: - Presentation anchor

private final class AnchorProvider: NSObject, ASAuthorizationControllerPresentationContextProviding {
    let window: NSWindow
    init(window: NSWindow) { self.window = window }
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        window
    }
}

// Resolve an NSWindow from a Compose `windowHandle` (NSWindow pointer as Long).
// Falls back to the app's key/first window, or a transient borderless window so
// the ceremony still has an anchor when called from a headless context.
private func resolveWindow(_ handle: Int64) -> NSWindow {
    if handle != 0, let raw = UnsafeRawPointer(bitPattern: Int(handle)) {
        let obj = Unmanaged<AnyObject>.fromOpaque(raw).takeUnretainedValue()
        if let window = obj as? NSWindow { return window }
    }
    if let key = NSApp?.keyWindow ?? NSApp?.windows.first { return key }
    let window = NSWindow(
        contentRect: NSRect(x: 0, y: 0, width: 1, height: 1),
        styleMask: [.borderless],
        backing: .buffered,
        defer: false
    )
    window.makeKeyAndOrderFront(nil)
    return window
}

// MARK: - Ceremony delegate

private final class CeremonyDelegate: NSObject, ASAuthorizationControllerDelegate {
    private let completion: (Result<ASAuthorization, Error>) -> Void
    init(completion: @escaping (Result<ASAuthorization, Error>) -> Void) {
        self.completion = completion
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        completion(.success(authorization))
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        completion(.failure(error))
    }
}

// Keeps the controller + delegate + anchor alive for the duration of the flow.
private var liveControllers: [ObjectIdentifier: (ASAuthorizationController, CeremonyDelegate, AnchorProvider)] = [:]

private func runController(
    _ build: @escaping () -> ASAuthorizationRequest,
    windowHandle: Int64,
    map: @escaping (ASAuthorization) -> String
) -> String {
    let semaphore = DispatchSemaphore(value: 0)
    var output = errorJson(code: "unknown", message: "ceremony did not complete")

    DispatchQueue.main.async {
        let request = build()
        let controller = ASAuthorizationController(authorizationRequests: [request])
        let delegate = CeremonyDelegate { result in
            switch result {
            case .success(let authorization):
                output = map(authorization)
            case .failure(let error):
                let ns = error as NSError
                output = errorJson(
                    code: "\(ns.domain):\(ns.code)",
                    message: ns.localizedDescription
                )
            }
            liveControllers[ObjectIdentifier(controller)] = nil
            semaphore.signal()
        }
        let anchor = AnchorProvider(window: resolveWindow(windowHandle))
        controller.delegate = delegate
        controller.presentationContextProvider = anchor
        liveControllers[ObjectIdentifier(controller)] = (controller, delegate, anchor)
        controller.performRequests()
    }

    semaphore.wait()
    return output
}

// MARK: - Registration

@_cdecl("passkeyCreate")
public func passkeyCreate(_ requestJson: UnsafePointer<CChar>, _ windowHandle: Int64) -> UnsafeMutablePointer<CChar> {
    let json = String(cString: requestJson)
    guard let data = json.data(using: .utf8),
          let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
        return cString(errorJson(code: "parse", message: "invalid registration options JSON"))
    }
    guard let rp = root["rp"] as? [String: Any], let rpId = rp["id"] as? String else {
        return cString(errorJson(code: "parse", message: "missing rp.id"))
    }
    guard let challengeStr = root["challenge"] as? String, let challenge = base64UrlDecode(challengeStr) else {
        return cString(errorJson(code: "parse", message: "missing or invalid challenge"))
    }
    guard let user = root["user"] as? [String: Any],
          let userName = user["name"] as? String,
          let userIdStr = user["id"] as? String,
          let userId = base64UrlDecode(userIdStr) else {
        return cString(errorJson(code: "parse", message: "missing or invalid user"))
    }
    let attestation = root["attestation"] as? String
    let userVerification = (root["authenticatorSelection"] as? [String: Any])?["userVerification"] as? String

    let result = runController({
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        let request = provider.createCredentialRegistrationRequest(
            challenge: challenge,
            name: userName,
            userID: userId
        )
        if let attestation { request.attestationPreference = ASAuthorizationPublicKeyCredentialAttestationKind(rawValue: attestation) }
        if let userVerification { request.userVerificationPreference = ASAuthorizationPublicKeyCredentialUserVerificationPreference(rawValue: userVerification) }
        return request
    }, windowHandle: windowHandle) { authorization in
        guard let credential = authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration else {
            return errorJson(code: "credential", message: "unexpected registration credential type")
        }
        let rawId = credential.credentialID
        var response: [String: Any] = [
            "clientDataJSON": base64UrlEncode(credential.rawClientDataJSON)
        ]
        if let attObj = credential.rawAttestationObject {
            response["attestationObject"] = base64UrlEncode(attObj)
        }
        let payload: [String: Any] = [
            "id": base64UrlEncode(rawId),
            "rawId": base64UrlEncode(rawId),
            "type": "public-key",
            "authenticatorAttachment": "platform",
            "response": response,
            "clientExtensionResults": [String: Any]()
        ]
        return jsonString(payload)
    }
    return cString(result)
}

// MARK: - Assertion

@_cdecl("passkeyAuthenticate")
public func passkeyAuthenticate(_ requestJson: UnsafePointer<CChar>, _ windowHandle: Int64) -> UnsafeMutablePointer<CChar> {
    let json = String(cString: requestJson)
    guard let data = json.data(using: .utf8),
          let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
        return cString(errorJson(code: "parse", message: "invalid authentication options JSON"))
    }
    guard let rpId = root["rpId"] as? String else {
        return cString(errorJson(code: "parse", message: "missing rpId"))
    }
    guard let challengeStr = root["challenge"] as? String, let challenge = base64UrlDecode(challengeStr) else {
        return cString(errorJson(code: "parse", message: "missing or invalid challenge"))
    }
    let userVerification = root["userVerification"] as? String
    let allowCredentials = (root["allowCredentials"] as? [[String: Any]]) ?? []

    let result = runController({
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        let request = provider.createCredentialAssertionRequest(challenge: challenge)
        request.allowedCredentials = allowCredentials.compactMap { entry in
            guard let idStr = entry["id"] as? String, let id = base64UrlDecode(idStr) else { return nil }
            return ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: id)
        }
        if let userVerification { request.userVerificationPreference = ASAuthorizationPublicKeyCredentialUserVerificationPreference(rawValue: userVerification) }
        return request
    }, windowHandle: windowHandle) { authorization in
        guard let credential = authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion else {
            return errorJson(code: "credential", message: "unexpected assertion credential type")
        }
        let rawId = credential.credentialID
        let response: [String: Any] = [
            "clientDataJSON": base64UrlEncode(credential.rawClientDataJSON),
            "authenticatorData": base64UrlEncode(credential.rawAuthenticatorData),
            "signature": base64UrlEncode(credential.signature),
            "userHandle": base64UrlEncode(credential.userID)
        ]
        let payload: [String: Any] = [
            "id": base64UrlEncode(rawId),
            "rawId": base64UrlEncode(rawId),
            "type": "public-key",
            "authenticatorAttachment": "platform",
            "response": response,
            "clientExtensionResults": [String: Any]()
        ]
        return jsonString(payload)
    }
    return cString(result)
}

@_cdecl("passkeyFreeString")
public func passkeyFreeString(_ ptr: UnsafeMutablePointer<CChar>?) {
    free(ptr)
}
