package io.github.androidpoet.passkeys

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo

// Apple shipped the matching AuthenticationServices features one major macOS
// release behind iOS: attachment reporting in 13.5, largeBlob in 14, prf in 15.
private const val ATTACHMENT_MIN_MAJOR = 13
private const val ATTACHMENT_MIN_MINOR = 5
private const val LARGE_BLOB_MIN_MAJOR = 14
private const val PRF_MIN_MAJOR = 15

internal actual object ApplePasskeyPlatform {
    actual val displayName: String = "macOS"

    actual fun supportsAttachmentReporting(): Boolean = osAtLeast(ATTACHMENT_MIN_MAJOR, ATTACHMENT_MIN_MINOR)

    actual fun supportsLargeBlob(): Boolean = osAtLeast(LARGE_BLOB_MIN_MAJOR, 0)

    actual fun supportsPrf(): Boolean = osAtLeast(PRF_MIN_MAJOR, 0)

    @OptIn(ExperimentalForeignApi::class)
    private fun osAtLeast(major: Int, minor: Int): Boolean =
        NSProcessInfo.processInfo.operatingSystemVersion.useContents {
            majorVersion > major || (majorVersion == major.toLong() && minorVersion >= minor)
        }
}
