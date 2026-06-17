package io.github.androidpoet.passkeys.internal

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Loads the bundled native passkey backend. Two-stage strategy:
 * 1. [System.loadLibrary] — works for packaged apps where the dylib is on
 *    `java.library.path`.
 * 2. Fallback — extract from the classpath (`passkeys/native/<platform>/`) into a
 *    persistent cache and [System.load] it.
 *
 * Returns `false` (rather than throwing) when the platform is unsupported or the
 * resource is absent, so callers can degrade gracefully.
 */
internal object NativeLibraryLoader {
    private const val RESOURCE_PREFIX = "passkeys/native"
    private val loaded = mutableSetOf<String>()

    @Synchronized
    fun load(libraryName: String, caller: Class<*>): Boolean {
        if (libraryName in loaded) return true
        try {
            System.loadLibrary(libraryName)
            loaded += libraryName
            return true
        } catch (_: UnsatisfiedLinkError) {
            // Not on java.library.path; fall back to classpath extraction.
        }
        val file = extractToCache(libraryName, caller) ?: return false
        System.load(file.absolutePath)
        loaded += libraryName
        return true
    }

    private fun extractToCache(libraryName: String, caller: Class<*>): File? {
        val platform = detectPlatform() ?: return null
        val fileName = System.mapLibraryName(libraryName)
        val resourcePath = "$RESOURCE_PREFIX/$platform/$fileName"
        val url = caller.classLoader?.getResource(resourcePath) ?: return null

        val cacheDir = File(File(System.getProperty("user.home"), ".cache"), "$RESOURCE_PREFIX/$platform")
        cacheDir.mkdirs()
        val cached = File(cacheDir, fileName)

        val size = url.openConnection().contentLengthLong
        if (cached.exists() && cached.length() == size) return cached

        val tmp = File(cacheDir, "$fileName.tmp")
        try {
            url.openStream().use { input ->
                Files.copy(input, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(tmp.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
            cached.setExecutable(true)
        } finally {
            tmp.delete()
        }
        return cached
    }

    private fun detectPlatform(): String? {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        val arch = System.getProperty("os.arch")?.lowercase().orEmpty()
        return when {
            os.contains("mac") || os.contains("darwin") ->
                if (arch.contains("aarch64") || arch.contains("arm")) "darwin-aarch64" else "darwin-x86-64"
            else -> null
        }
    }
}
