package io.github.androidpoet.passkeys.composeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.androidpoet.passkeys.PasskeyResult
import io.github.androidpoet.passkeys.compose.rememberPasskeyClient
import kotlinx.coroutines.launch

/**
 * The entire sample — one composable shared by Android, iOS and desktop, with a
 * single common call site. [rememberPasskeyClient] resolves the platform client
 * (and its presentation anchor) under the hood, so nothing here is platform
 * specific; each platform's entry point just calls `App()`.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun App(rpId: String = RP_ID) {
    val passkeys = rememberPasskeyClient()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var status by remember { mutableStateOf("Ready on ${platformName()} for $rpId.") }
            var busy by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Passkeys KMP", style = MaterialTheme.typography.headlineMedium)
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )

                Button(
                    onClick = {
                        busy = true
                        status = "Calling create…"
                        scope.launch {
                            status =
                                when (val r = passkeys.create(registrationOptions(rpId))) {
                                    is PasskeyResult.Success -> "✅ Passkey created successfully\n\n${r.value.rawJson}"
                                    is PasskeyResult.Failure -> "❌ ${r.error.message} (${r.error.code})"
                                }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create Passkey") }

                Button(
                    onClick = {
                        busy = true
                        status = "Calling authenticate…"
                        scope.launch {
                            status =
                                when (val r = passkeys.authenticate(authenticationOptions(rpId))) {
                                    is PasskeyResult.Success -> "✅ Login successful\n\n${r.value.rawJson}"
                                    is PasskeyResult.Failure -> "❌ ${r.error.message} (${r.error.code})"
                                }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Authenticate") }
            }
        }
    }
}
