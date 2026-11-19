package io.github.androidpoet.passkeys.composeapp

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.androidpoet.passkeys.PasskeyResult
import io.github.androidpoet.passkeys.authsession.AndroidWebAuthSession
import io.github.androidpoet.passkeys.authsession.WebAuthRedirect
import io.github.androidpoet.passkeys.webview.enablePasskeys

// 10.0.2.2 is the emulator's alias for the host machine running :sample:server.
private const val SERVER = "http://10.0.2.2:8080"

@Composable
actual fun rememberWebSignIn(): (suspend () -> PasskeyResult<String>)? {
    val activity = LocalContext.current as ComponentActivity
    val session = remember(activity) { AndroidWebAuthSession(activity) }
    return { session.authenticate("$SERVER/auth.html", WebAuthRedirect.CustomScheme(AUTH_REDIRECT_SCHEME)) }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("ktlint:standard:function-naming")
@Composable
actual fun PlatformDemos() {
    var open by remember { mutableStateOf(false) }
    var passkeysEnabled by remember { mutableStateOf<Boolean?>(null) }

    Button(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
        Text(if (open) "Close WebView" else "Open sign-in page in a WebView")
    }
    if (open) {
        Text("WebView.enablePasskeys() returned $passkeysEnabled", style = MaterialTheme.typography.bodyMedium)
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    passkeysEnabled = enablePasskeys()
                    loadUrl(SERVER)
                }
            },
            modifier = Modifier.fillMaxWidth().height(460.dp),
        )
    }
}
