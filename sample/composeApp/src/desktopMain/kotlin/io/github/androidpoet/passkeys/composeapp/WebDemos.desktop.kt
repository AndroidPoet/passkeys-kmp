package io.github.androidpoet.passkeys.composeapp

import androidx.compose.runtime.Composable
import io.github.androidpoet.passkeys.PasskeyResult

@Composable
actual fun rememberWebSignIn(): (suspend () -> PasskeyResult<String>)? = null

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun PlatformDemos() = Unit
