package io.github.androidpoet.passkeys.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.androidpoet.passkeys.IosPasskeyClient
import io.github.androidpoet.passkeys.PasskeyClient
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow

@Composable
public actual fun rememberPasskeyClient(): PasskeyClient = remember { IosPasskeyClient(keyWindow()) }

private fun keyWindow(): UIWindow =
    UIApplication.sharedApplication.keyWindow
        ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
        ?: UIWindow()
