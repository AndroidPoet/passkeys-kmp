package io.github.androidpoet.passkeys.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.interop.LocalUIViewController
import io.github.androidpoet.passkeys.PasskeyResult
import io.github.androidpoet.passkeys.authsession.IosWebAuthSession
import io.github.androidpoet.passkeys.authsession.WebAuthRedirect

// The simulator shares the Mac's loopback interface.
private const val AUTH_URL = "http://localhost:8080/auth.html"

@Composable
actual fun rememberWebSignIn(): (suspend () -> PasskeyResult<String>)? {
    val hostController = LocalUIViewController.current
    val session =
        remember(hostController) {
            IosWebAuthSession { hostController.view.window ?: error("the sample's view controller has no window") }
        }
    return { session.authenticate(AUTH_URL, WebAuthRedirect.CustomScheme(AUTH_REDIRECT_SCHEME)) }
}

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun PlatformDemos() = Unit
