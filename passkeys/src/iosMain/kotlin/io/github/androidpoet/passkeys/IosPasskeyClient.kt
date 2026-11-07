package io.github.androidpoet.passkeys

import platform.UIKit.UIWindow

/**
 * [PasskeyClient] for iOS, backed by AuthenticationServices.
 *
 * The ceremony itself is shared with every Apple platform in
 * [ApplePasskeyClient]; this type only pins the presentation anchor to the
 * host [UIWindow] (on iOS `ASPresentationAnchor` is a `UIWindow`).
 *
 * @param windowProvider resolves, at ceremony time, the window
 *   AuthenticationServices parents its system UI to — so a multi-window or
 *   scene-based app always presents on the current one.
 *   Requires iOS 16+ and an Associated Domains entitlement
 *   (`webcredentials:your-domain.com`).
 */
public class IosPasskeyClient(
    windowProvider: () -> UIWindow,
) : PasskeyClient by ApplePasskeyClient(windowProvider) {
    /** Anchors every ceremony to one fixed [window]. */
    public constructor(window: UIWindow) : this({ window })
}
