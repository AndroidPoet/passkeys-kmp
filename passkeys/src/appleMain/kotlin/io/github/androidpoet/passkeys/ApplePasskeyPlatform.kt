package io.github.androidpoet.passkeys

/**
 * Per-platform Apple capability gates. The WebAuthn extensions Apple exposes
 * (`largeBlob`, `prf`) and the attachment-reporting API became available at
 * different OS versions on iOS vs macOS, so each Apple target supplies its own
 * runtime checks while [ApplePasskeyClient] stays platform-agnostic.
 */
internal expect object ApplePasskeyPlatform {
    /** Human-readable platform name used in error messages ("iOS" / "macOS"). */
    val displayName: String

    /** Whether the `authenticatorAttachment` property can be read at runtime. */
    fun supportsAttachmentReporting(): Boolean

    /** Whether the `largeBlob` extension is available (iOS 17+ / macOS 14+). */
    fun supportsLargeBlob(): Boolean

    /** Whether the `prf` extension is available (iOS 18+ / macOS 15+). */
    fun supportsPrf(): Boolean
}

/**
 * Compares dotted version strings numerically, padding missing components with
 * zero, so `"16.6".isSemanticVersionAtLeast("16")` is true.
 */
internal fun String.isSemanticVersionAtLeast(minimum: String): Boolean {
    val currentParts = split('.').map { it.toIntOrNull() ?: 0 }
    val minimumParts = minimum.split('.').map { it.toIntOrNull() ?: 0 }
    val size = maxOf(currentParts.size, minimumParts.size)
    for (index in 0 until size) {
        val current = currentParts.getOrElse(index) { 0 }
        val required = minimumParts.getOrElse(index) { 0 }
        if (current != required) return current > required
    }
    return true
}
