package io.github.androidpoet.passkeys.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.interop.LocalUIViewController
import io.github.androidpoet.passkeys.IosPasskeyClient
import io.github.androidpoet.passkeys.PasskeyClient
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@Composable
public actual fun rememberPasskeyClient(): PasskeyClient {
    val hostController = LocalUIViewController.current
    // The anchor is resolved when a ceremony starts, from the view controller
    // hosting this composition — so scene-based and multi-window apps present
    // on the window that is actually showing the UI, not one captured at first
    // composition.
    return remember(hostController) { IosPasskeyClient { hostController.presentationWindow() } }
}

private fun UIViewController.presentationWindow(): UIWindow =
    view.window
        ?: UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() }
        ?: UIApplication.sharedApplication.keyWindow
        ?: error("rememberPasskeyClient(): no UIWindow is attached to the hosting view controller")
