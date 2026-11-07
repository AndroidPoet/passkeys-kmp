package io.github.androidpoet.passkeys.internal

/**
 * JNI binding to the bundled macOS passkey backend (`libPasskeysNative.dylib`,
 * built from `jvmMain/native/macos`). The native side registers these methods in
 * `JNI_OnLoad`. Each call runs the AuthenticationServices ceremony synchronously
 * and returns JSON — the standard WebAuthn credential JSON on success, or
 * `{"__error__":{"code":..,"message":..}}` on failure.
 *
 * Strings cross the boundary as UTF-8 byte arrays rather than `jstring`:
 * JNI's `GetStringUTFChars` / `NewStringUTF` speak *modified* UTF-8, which
 * corrupts supplementary-plane characters (emoji in a display name) and
 * embedded NULs on the way to Swift's real UTF-8.
 *
 * [available] reports whether the dylib loaded (false on non-macOS or when the
 * resource is missing) so the JVM client can fall back instead of crashing.
 */
internal object ApplePasskeyNativeBridge {
    val available: Boolean = NativeLibraryLoader.load("PasskeysNative", ApplePasskeyNativeBridge::class.java)

    fun create(requestJson: String, windowHandle: Long): String =
        nCreate(requestJson.encodeToByteArray(), windowHandle)?.decodeToString()
            ?: error("native passkey backend returned no result")

    fun authenticate(requestJson: String, windowHandle: Long): String =
        nAuthenticate(requestJson.encodeToByteArray(), windowHandle)?.decodeToString()
            ?: error("native passkey backend returned no result")

    @JvmStatic private external fun nCreate(requestJsonUtf8: ByteArray, windowHandle: Long): ByteArray?

    @JvmStatic private external fun nAuthenticate(requestJsonUtf8: ByteArray, windowHandle: Long): ByteArray?
}
