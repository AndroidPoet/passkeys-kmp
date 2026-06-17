package io.github.androidpoet.passkeys.composeapp

import androidx.compose.runtime.Composable
import io.github.androidpoet.passkeys.PasskeyClient

/**
 * The one common entry point: returns the platform [PasskeyClient] (with the
 * correct presentation anchor) so `commonMain` code never names a platform
 * client. Same call on Android, iOS, and desktop.
 */
@Composable
expect fun rememberPasskeyClient(): PasskeyClient

/** Short platform label for the sample's status line. */
expect fun platformName(): String
