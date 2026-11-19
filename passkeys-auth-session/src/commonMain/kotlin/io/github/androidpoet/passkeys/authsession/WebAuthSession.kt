package io.github.androidpoet.passkeys.authsession

import io.github.androidpoet.passkeys.PasskeyResult

/**
 * Runs a browser-based sign-in — your existing web sign-in page or an
 * OAuth / OpenID Connect identity provider — in the platform's dedicated
 * authentication browser, and hands back the redirect it ends on.
 *
 * This is the alternative to a native passkey ceremony (`PasskeyClient`): the
 * passkey prompt happens *inside the web page*, which shares the browser's
 * passkeys, and the app only receives the final redirect URL (typically
 * carrying an authorization code to exchange on your server).
 *
 * - Android: an Auth Tab (`AndroidWebAuthSession`), falling back to a Custom
 *   Tab on browsers without Auth Tab support.
 * - iOS: `ASWebAuthenticationSession` (`IosWebAuthSession`).
 */
public interface WebAuthSession {
    /**
     * Opens [url] and suspends until the page navigates to [redirect].
     *
     * @param url the `https` sign-in or authorization URL to open.
     * @param redirect the redirect that completes the flow.
     * @param ephemeral when `true`, asks for a private session that shares no
     *   cookies or site data with the user's browser. Best effort on Android,
     *   where it depends on the browser.
     * @return a [PasskeyResult.Success] wrapping the full redirect URL, or a
     *   [PasskeyResult.Failure] — `UserCanceled` when the user closes the
     *   browser, `Unsupported` when the device cannot run the flow.
     */
    public suspend fun authenticate(
        url: String,
        redirect: WebAuthRedirect,
        ephemeral: Boolean = false,
    ): PasskeyResult<String>
}

/** The redirect a [WebAuthSession] waits for. */
public sealed interface WebAuthRedirect {
    /**
     * A custom-scheme redirect such as `myapp://callback`. Needs no domain
     * association, but any app can claim the same scheme — prefer [Https].
     *
     * @property scheme the scheme alone, without `://` (e.g. `"myapp"`).
     */
    public data class CustomScheme(
        public val scheme: String,
    ) : WebAuthRedirect

    /**
     * An `https://host/path` redirect, verified against your domain
     * association (Digital Asset Links on Android, an
     * `apple-app-site-association` file on iOS), so no other app can
     * intercept it. Requires iOS 17.4+.
     *
     * @property host the redirect host (e.g. `"example.com"`).
     * @property path the redirect path (e.g. `"/auth/callback"`).
     */
    public data class Https(
        public val host: String,
        public val path: String,
    ) : WebAuthRedirect
}
