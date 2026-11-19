package io.github.androidpoet.passkeys

import io.github.androidpoet.passkeys.models.PasskeyAuthenticationOptions
import io.github.androidpoet.passkeys.models.PasskeyAuthenticationResponse
import platform.UIKit.UIWindow

/**
 * [PasskeyClient] for iOS, backed by AuthenticationServices.
 *
 * The ceremony itself is shared with every Apple platform in
 * [ApplePasskeyClient]; this type only pins the presentation anchor to the
 * host [UIWindow] (on iOS `ASPresentationAnchor` is a `UIWindow`).
 *
 * Requires iOS 16+ and an Associated Domains entitlement
 * (`webcredentials:your-domain.com`).
 */
public class IosPasskeyClient private constructor(
    private val client: ApplePasskeyClient,
) : PasskeyClient by client {
    /**
     * @param windowProvider resolves, at ceremony time, the window
     *   AuthenticationServices parents its system UI to — so a multi-window or
     *   scene-based app always presents on the current one.
     */
    public constructor(windowProvider: () -> UIWindow) : this(ApplePasskeyClient(windowProvider))

    /** Anchors every ceremony to one fixed [window]. */
    public constructor(window: UIWindow) : this({ window })

    /**
     * Offers the user's passkeys in the keyboard's QuickType bar instead of
     * opening a sheet. They appear when a text field whose content type is
     * `username` (`UITextContentType.username`) has focus.
     *
     * Suspends until the user picks a passkey there; nothing is shown or
     * returned otherwise, so launch it when the sign-in screen opens and
     * cancel the coroutine when it closes, or before starting a modal
     * [authenticate].
     */
    public suspend fun authenticateWithAutofill(
        options: PasskeyAuthenticationOptions,
    ): PasskeyResult<PasskeyAuthenticationResponse> = client.authenticate(options) { it.performAutoFillAssistedRequests() }
}
