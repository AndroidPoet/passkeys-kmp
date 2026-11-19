package io.github.androidpoet.passkeys.composeapp

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.androidpoet.passkeys.PasskeyResult
import io.github.androidpoet.passkeys.authsession.AndroidWebAuthSession
import io.github.androidpoet.passkeys.authsession.WebAuthRedirect
import io.github.androidpoet.passkeys.webview.enablePasskeys

// Reach :sample:server on the host with `adb reverse tcp:8080 tcp:8080`. Localhost also
// counts as a secure context, which WebAuthn requires.
private const val SIGN_IN_URL = "http://localhost:8080/auth.html"

@Composable
actual fun rememberWebSignIn(): (suspend () -> PasskeyResult<String>)? {
    val activity = LocalContext.current as ComponentActivity
    val session = remember(activity) { AndroidWebAuthSession(activity) }
    return { session.authenticate(SIGN_IN_URL, WebAuthRedirect.CustomScheme(AUTH_REDIRECT_SCHEME)) }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("ktlint:standard:function-naming")
@Composable
actual fun PlatformDemos() {
    var open by remember { mutableStateOf(false) }

    Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Open sign-in page in a WebView")
    }
    if (!open) return

    Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var passkeysEnabled by remember { mutableStateOf<Boolean?>(null) }
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your page in a WebView", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (passkeysEnabled) {
                                true -> "Passkeys enabled for this page"
                                false -> "This WebView is too old for passkeys"
                                null -> "Loading…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { open = false }) { Text("Close") }
                }
                HorizontalDivider()
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                            passkeysEnabled = enablePasskeys()
                            loadUrl(SIGN_IN_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
