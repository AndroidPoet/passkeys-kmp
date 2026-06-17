package io.github.androidpoet.passkeys.composeapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Desktop (JVM) entry point. */
fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Passkeys KMP") {
            App()
        }
    }
