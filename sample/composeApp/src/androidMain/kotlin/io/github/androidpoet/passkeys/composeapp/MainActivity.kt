package io.github.androidpoet.passkeys.composeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import io.github.androidpoet.passkeys.AndroidPasskeyClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val client = remember { AndroidPasskeyClient(this) }
            App(client, platformName = android.os.Build.MODEL)
        }
    }
}
