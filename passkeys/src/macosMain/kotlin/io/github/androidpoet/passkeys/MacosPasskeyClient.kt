package io.github.androidpoet.passkeys

import platform.AppKit.NSWindow

/**
 * [PasskeyClient] for macOS, backed by AuthenticationServices.
 *
 * macOS uses the identical `ASAuthorization*` API as iOS (shared in
 * [ApplePasskeyClient]); the only difference is the presentation anchor, which
 * on macOS is an [NSWindow] rather than a `UIWindow`. The system shows the
 * Touch ID / passkey sheet parented to that window.
 *
 * @param window the window AuthenticationServices parents its system UI to.
 *   Requires macOS 13 (Ventura)+ and an Associated Domains entitlement
 *   (`webcredentials:your-domain.com`).
 */
public class MacosPasskeyClient(
    window: NSWindow,
) : PasskeyClient by ApplePasskeyClient({ window })
