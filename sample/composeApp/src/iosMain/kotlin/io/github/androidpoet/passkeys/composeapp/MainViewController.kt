package io.github.androidpoet.passkeys.composeapp

import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point — consumed by the Xcode `iosApp` project via
 * `ComposeApp.MainViewControllerKt.MainViewController()`.
 */
@Suppress("FunctionName", "unused")
fun MainViewController() = ComposeUIViewController { App() }
