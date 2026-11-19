package io.github.androidpoet.passkeys.authsession

import io.github.androidpoet.passkeys.PasskeyException
import io.github.androidpoet.passkeys.PasskeyResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.Foundation.NSError
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private const val WEB_AUTH_ERROR_DOMAIN = "com.apple.AuthenticationServices.WebAuthenticationSession"
private const val WEB_AUTH_CANCELED_LOGIN_ERROR_CODE = 1L
private const val HTTPS_CALLBACK_MIN_MAJOR = 17L
private const val HTTPS_CALLBACK_MIN_MINOR = 4L

/**
 * [WebAuthSession] for iOS, backed by `ASWebAuthenticationSession`.
 *
 * The session shares Safari's passkeys (and, unless `ephemeral`, its cookies),
 * so a web sign-in page can run its own passkey prompt.
 *
 * An [WebAuthRedirect.Https] redirect requires iOS 17.4+ and an Associated
 * Domains entitlement for the redirect host; on older versions it fails with
 * `Unsupported`.
 *
 * @param windowProvider resolves, at session time, the window the browser
 *   sheet is presented over.
 */
public class IosWebAuthSession(
    private val windowProvider: () -> UIWindow,
) : WebAuthSession {
    /** Anchors every session to one fixed [window]. */
    public constructor(window: UIWindow) : this({ window })

    override suspend fun authenticate(
        url: String,
        redirect: WebAuthRedirect,
        ephemeral: Boolean,
    ): PasskeyResult<String> {
        val nsUrl =
            NSURL.URLWithString(url)
                ?: return PasskeyResult.Failure(PasskeyException.InvalidPayload(IllegalArgumentException("not a URL: $url")))
        if (redirect is WebAuthRedirect.Https && !supportsHttpsCallback()) {
            return PasskeyResult.Failure(
                PasskeyException.Unsupported(IllegalStateException("an https redirect requires iOS 17.4 or newer")),
            )
        }
        return suspendCancellableCoroutine { continuation ->
            // The session holds its context provider only weakly, but retains its
            // completion handler: binding that handler to the flow keeps the flow
            // (and so the provider) alive until the session completes.
            val flow = WebAuthFlow(windowProvider, continuation)
            val session =
                when (redirect) {
                    is WebAuthRedirect.CustomScheme ->
                        ASWebAuthenticationSession(uRL = nsUrl, callbackURLScheme = redirect.scheme, completionHandler = flow::complete)
                    is WebAuthRedirect.Https ->
                        ASWebAuthenticationSession(
                            uRL = nsUrl,
                            callback = ASWebAuthenticationSessionCallback.callbackWithHTTPSHost(redirect.host, path = redirect.path),
                            completionHandler = flow::complete,
                        )
                }
            session.presentationContextProvider = flow
            session.prefersEphemeralWebBrowserSession = ephemeral
            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) { session.cancel() }
            }
            dispatch_async(dispatch_get_main_queue()) {
                if (!session.start() && continuation.isActive) {
                    continuation.resume(
                        PasskeyResult.Failure(PasskeyException.Unexpected(IllegalStateException("the web authentication session did not start"))),
                    )
                }
            }
        }
    }
}

/** One session's presentation anchor and completion, bridged to its coroutine. */
private class WebAuthFlow(
    private val windowProvider: () -> UIWindow,
    private val continuation: CancellableContinuation<PasskeyResult<String>>,
) : NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        windowProvider()

    fun complete(
        callbackUrl: NSURL?,
        error: NSError?,
    ) {
        if (continuation.isActive) continuation.resume(webAuthOutcome(callbackUrl?.absoluteString, error))
    }
}

/** Maps an `ASWebAuthenticationSession` completion to the session outcome. */
internal fun webAuthOutcome(
    callbackUrl: String?,
    error: NSError?,
): PasskeyResult<String> {
    if (callbackUrl != null) return PasskeyResult.Success(callbackUrl)
    val cause = Throwable(error?.let { "${it.domain}:${it.code} ${it.localizedDescription}" } ?: "no callback URL and no error")
    val canceled = error?.domain == WEB_AUTH_ERROR_DOMAIN && error.code == WEB_AUTH_CANCELED_LOGIN_ERROR_CODE
    return PasskeyResult.Failure(if (canceled) PasskeyException.UserCanceled(cause) else PasskeyException.Unexpected(cause))
}

@OptIn(ExperimentalForeignApi::class)
private fun supportsHttpsCallback(): Boolean =
    NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
        cValue<NSOperatingSystemVersion> {
            majorVersion = HTTPS_CALLBACK_MIN_MAJOR
            minorVersion = HTTPS_CALLBACK_MIN_MINOR
            patchVersion = 0
        },
    )
