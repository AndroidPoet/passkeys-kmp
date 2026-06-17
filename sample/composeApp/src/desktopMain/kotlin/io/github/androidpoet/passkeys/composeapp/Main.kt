package io.github.androidpoet.passkeys.composeapp

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.androidpoet.passkeys.JvmPasskeyClient

/**
 * Desktop (JVM) entry point. The native macOS backend in [JvmPasskeyClient]
 * presents the Touch ID / passkey sheet anchored to this window, so we pass the
 * Compose window handle through.
 */
fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Passkeys KMP") {
            val client = remember { JvmPasskeyClient(windowHandle = { window.windowHandle }) }
            App(client, platformName = "macOS desktop")
        }
    }
