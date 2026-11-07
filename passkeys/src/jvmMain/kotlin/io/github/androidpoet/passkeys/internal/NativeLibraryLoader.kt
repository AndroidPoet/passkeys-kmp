package io.github.androidpoet.passkeys.internal

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Loads the bundled native passkey backend. Two-stage strategy:
 * 1. [System.loadLibrary] — works for packaged apps where the dylib is on
 *    `java.library.path`.
 * 2. Fallback — extract from the classpath (`passkeys/native/<platform>/`) into a
 *    content-addressed cache directory (`~/.cache/passkeys/native/<platform>/<sha256>/`)
 *    and [System.load] it. Keying the cache by the resource's SHA-256 means a
 *    new library version, or a corrupt cached copy, is never mistaken for the
 *    current one (a same-length stale file would have been reused before), and
 *    the file is published with an atomic rename so concurrent JVMs never load
 *    a half-written binary.
 *
 * Returns `false` (rather than throwing) when the platform is unsupported, the
 * resource is absent, or the library cannot be linked, so callers degrade to
 * [io.github.androidpoet.passkeys.PasskeyException.Unsupported].
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
        val file =
            try {
                extractToCache(libraryName, caller) ?: return false
            } catch (_: IOException) {
                return false
            }
        try {
            System.load(file.absolutePath)
        } catch (_: UnsatisfiedLinkError) {
            return false
        }
        loaded += libraryName
        return true
    }

    private fun extractToCache(libraryName: String, caller: Class<*>): File? {
        val platform = detectPlatform() ?: return null
        val fileName = System.mapLibraryName(libraryName)
        val resourcePath = "$RESOURCE_PREFIX/$platform/$fileName"
        val url = caller.classLoader?.getResource(resourcePath) ?: return null
        val bytes = url.openStream().use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        val cacheDir = File(File(System.getProperty("user.home"), ".cache"), "$RESOURCE_PREFIX/$platform/$digest")
        val cached = File(cacheDir, fileName)
        if (cached.isFile && cached.length() == bytes.size.toLong()) return cached

        Files.createDirectories(cacheDir.toPath())
        val tmp = Files.createTempFile(cacheDir.toPath(), "$fileName.", ".tmp").toFile()
        try {
            tmp.writeBytes(bytes)
            tmp.setExecutable(true)
            try {
                Files.move(tmp.toPath(), cached.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                // Another JVM published the same content-addressed file first.
            }
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
