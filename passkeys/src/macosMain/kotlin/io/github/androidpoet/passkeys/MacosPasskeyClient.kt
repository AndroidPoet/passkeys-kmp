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
 * @param windowProvider resolves, at ceremony time, the window
 *   AuthenticationServices parents its system UI to — so a multi-window or
 *   scene-based app always presents on the current one.
 *   Requires macOS 13 (Ventura)+ and an Associated Domains entitlement
 *   (`webcredentials:your-domain.com`).
 */
public class MacosPasskeyClient(
    windowProvider: () -> NSWindow,
) : PasskeyClient by ApplePasskeyClient(windowProvider) {
    /** Anchors every ceremony to one fixed [window]. */
    public constructor(window: NSWindow) : this({ window })
}
