package io.github.androidpoet.passkeys.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.androidpoet.passkeys.JvmPasskeyClient
import io.github.androidpoet.passkeys.PasskeyClient

@Composable
actual fun rememberPasskeyClient(): PasskeyClient = remember { JvmPasskeyClient() }

actual fun platformName(): String = System.getProperty("os.name") ?: "Desktop"
