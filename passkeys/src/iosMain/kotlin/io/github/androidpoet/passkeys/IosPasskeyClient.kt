package io.github.androidpoet.passkeys

import platform.UIKit.UIWindow

/**
 * [PasskeyClient] for iOS, backed by AuthenticationServices.
 *
 * The ceremony itself is shared with every Apple platform in
 * [ApplePasskeyClient]; this type only pins the presentation anchor to the
 * host [UIWindow] (on iOS `ASPresentationAnchor` is a `UIWindow`).
 *
 * @param window the window AuthenticationServices parents its system UI to.
 *   Requires iOS 16+ and an Associated Domains entitlement
 *   (`webcredentials:your-domain.com`).
 */
public class IosPasskeyClient(
    window: UIWindow,
) : PasskeyClient by ApplePasskeyClient({ window })
