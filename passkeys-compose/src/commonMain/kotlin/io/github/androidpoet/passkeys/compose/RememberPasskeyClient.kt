package io.github.androidpoet.passkeys.compose

import androidx.compose.runtime.Composable
import io.github.androidpoet.passkeys.PasskeyClient

/**
 * Returns the [PasskeyClient] for the current platform, remembered across
 * recompositions, with its presentation anchor already wired up:
 *
 * - **Android** — `AndroidPasskeyClient` bound to the hosting `Activity`.
 * - **iOS** — `IosPasskeyClient` anchored to the key `UIWindow`.
 * - **JVM desktop** — `JvmPasskeyClient` (macOS native ceremony; key window anchor).
 * - **Browser (Wasm)** — `WasmJsPasskeyClient`.
 *
 * This is the single common entry point: shared `commonMain` code calls it and
 * never names a platform client. The returned client exposes the same
 * `create` / `authenticate` → `PasskeyResult` API everywhere.
 *
 * ```kotlin
 * val passkeys = rememberPasskeyClient()
 * val result = passkeys.create(optionsJson)
 * ```
 */
@Composable
public expect fun rememberPasskeyClient(): PasskeyClient
