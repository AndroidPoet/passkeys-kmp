package io.github.androidpoet.passkeys.internal

/**
 * JNI binding to the bundled macOS passkey backend (`libPasskeysNative.dylib`,
 * built from `jvmMain/native/macos`). The native side registers these methods in
 * `JNI_OnLoad`. Each call runs the AuthenticationServices ceremony synchronously
 * and returns JSON — the standard WebAuthn credential JSON on success, or
 * `{"__error__":{"code":..,"message":..}}` on failure.
 *
 * [available] reports whether the dylib loaded (false on non-macOS or when the
 * resource is missing) so the JVM client can fall back instead of crashing.
 */
internal object ApplePasskeyNativeBridge {
    val available: Boolean = NativeLibraryLoader.load("PasskeysNative", ApplePasskeyNativeBridge::class.java)

    @JvmStatic external fun nCreate(requestJson: String, windowHandle: Long): String?

    @JvmStatic external fun nAuthenticate(requestJson: String, windowHandle: Long): String?
}
