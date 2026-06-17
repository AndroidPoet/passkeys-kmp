package io.github.androidpoet.passkeys.composeapp

import androidx.compose.ui.window.ComposeUIViewController
import io.github.androidpoet.passkeys.IosPasskeyClient
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow

/**
 * iOS entry point — consumed by the Xcode `iosApp` project via
 * `ComposeApp.MainViewControllerKt.MainViewController()`. The passkey sheet
 * anchors to the app's key window.
 */
@Suppress("FunctionName", "unused")
fun MainViewController() =
    ComposeUIViewController {
        App(IosPasskeyClient(keyWindow()), platformName = "iOS")
    }

private fun keyWindow(): UIWindow =
    UIApplication.sharedApplication.keyWindow
        ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
        ?: UIWindow()
