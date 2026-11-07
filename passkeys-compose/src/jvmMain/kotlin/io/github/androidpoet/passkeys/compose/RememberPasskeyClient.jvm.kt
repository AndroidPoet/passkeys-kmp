package io.github.androidpoet.passkeys.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import io.github.androidpoet.passkeys.JvmPasskeyClient
import io.github.androidpoet.passkeys.PasskeyClient
import java.awt.KeyboardFocusManager
import java.awt.Window

@Composable
public actual fun rememberPasskeyClient(): PasskeyClient = remember { JvmPasskeyClient(::activeComposeWindowHandle) }

/**
 * Native handle of the Compose window the ceremony should anchor to, resolved
 * when the ceremony starts: the focused window if it is a [ComposeWindow],
 * otherwise the first showing one. `0` lets the native side fall back to the
 * key window. (`LocalWindow` is internal in Compose Multiplatform 1.7, so the
 * host window cannot be read from the composition directly.)
 */
private fun activeComposeWindowHandle(): Long {
    val focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? ComposeWindow
    val window = focused ?: Window.getWindows().filterIsInstance<ComposeWindow>().firstOrNull { it.isShowing }
    return window?.windowHandle ?: 0L
}
