package io.github.androidpoet.passkeys

import java.awt.Desktop
import java.net.URI

/**
 * Browser handoff for JVM desktop, where there is no in-process passkey
 * authenticator (see [JvmPasskeyClient]).
 *
 * The recommended desktop flow is: open your relying party's sign-in/registration
 * page in the system browser, let the OS platform authenticator (Windows Hello,
 * Touch ID, a security key) complete the WebAuthn ceremony there, and have your
 * web page hand the session back to the desktop app (e.g. via a loopback
 * redirect your app listens on).
 */
public object PasskeyBrowserHandoff {
    /**
     * Opens [uri] in the system default browser.
     *
     * @return `true` if the browser was launched, `false` if desktop browsing is
     *   unavailable in this environment (headless, no browser, or unsupported).
     */
    public fun open(uri: String): Boolean {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
        return runCatching { desktop.browse(URI(uri)) }.isSuccess
    }
}
