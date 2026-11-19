package io.github.androidpoet.passkeys.webview

import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Lets the page loaded in this [WebView] create and use passkeys through its
 * own `navigator.credentials` calls, with the native Credential Manager sheet.
 *
 * Passkeys are scoped to your app: the page's relying party domain must list
 * the app in its Digital Asset Links, exactly as for a native ceremony. Meant
 * for first-party pages — it cannot serve a third-party identity provider.
 *
 * iOS needs no counterpart: `WKWebView` supports passkeys for the app's
 * Associated Domains out of the box.
 *
 * @return `false` when the installed WebView is too old to support it, in
 *   which case nothing changes and you should offer another sign-in path.
 */
public fun WebView.enablePasskeys(): Boolean {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) return false
    WebSettingsCompat.setWebAuthenticationSupport(settings, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP)
    return true
}
