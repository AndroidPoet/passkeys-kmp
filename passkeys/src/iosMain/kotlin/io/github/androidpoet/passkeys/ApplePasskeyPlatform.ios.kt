package io.github.androidpoet.passkeys

import platform.UIKit.UIDevice

private const val ATTACHMENT_MIN_OS_VERSION = "16.6"
private const val LARGE_BLOB_MIN_OS_VERSION = "17.0"
private const val PRF_MIN_OS_VERSION = "18.0"

internal actual object ApplePasskeyPlatform {
    actual val displayName: String = "iOS"

    actual fun supportsAttachmentReporting(): Boolean = osAtLeast(ATTACHMENT_MIN_OS_VERSION)

    actual fun supportsLargeBlob(): Boolean = osAtLeast(LARGE_BLOB_MIN_OS_VERSION)

    actual fun supportsPrf(): Boolean = osAtLeast(PRF_MIN_OS_VERSION)

    private fun osAtLeast(minimum: String): Boolean =
        UIDevice.currentDevice.systemVersion.isSemanticVersionAtLeast(minimum)
}
