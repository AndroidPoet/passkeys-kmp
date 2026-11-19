package io.github.androidpoet.passkeys.composeapp

import androidx.compose.runtime.Composable
import io.github.androidpoet.passkeys.PasskeyResult

/** Custom scheme the sample server's `/auth.html` page redirects to. */
internal const val AUTH_REDIRECT_SCHEME = "passkeys-sample"

/**
 * Runs the `passkeys-auth-session` demo against `:sample:server`
 * (`./gradlew :sample:server:run`) and returns the redirect it ends on.
 * `null` where the module has no implementation (desktop).
 */
@Composable
expect fun rememberWebSignIn(): (suspend () -> PasskeyResult<String>)?

/** Demos that exist on one platform only (Android: `passkeys-webview`). */
@Suppress("ktlint:standard:function-naming")
@Composable
expect fun PlatformDemos()
