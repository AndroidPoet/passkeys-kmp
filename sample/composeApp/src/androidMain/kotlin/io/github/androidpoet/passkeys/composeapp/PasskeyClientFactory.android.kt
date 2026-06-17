package io.github.androidpoet.passkeys.composeapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.androidpoet.passkeys.AndroidPasskeyClient
import io.github.androidpoet.passkeys.PasskeyClient

@Composable
actual fun rememberPasskeyClient(): PasskeyClient {
    val activity = LocalContext.current.findActivity()
    return remember { AndroidPasskeyClient(activity) }
}

actual fun platformName(): String = Build.MODEL

private fun Context.findActivity(): Activity {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("composeApp must be hosted in an Activity")
}
