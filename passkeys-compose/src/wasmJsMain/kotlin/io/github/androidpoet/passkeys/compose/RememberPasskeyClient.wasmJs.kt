package io.github.androidpoet.passkeys.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.androidpoet.passkeys.PasskeyClient
import io.github.androidpoet.passkeys.WasmJsPasskeyClient

@Composable
public actual fun rememberPasskeyClient(): PasskeyClient = remember { WasmJsPasskeyClient() }
