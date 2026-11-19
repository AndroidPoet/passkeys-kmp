package io.github.androidpoet.passkeys.authsession

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.browser.auth.AuthTabIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.androidpoet.passkeys.PasskeyException
import io.github.androidpoet.passkeys.PasskeyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

private const val VERIFICATION_MESSAGE =
    "The browser could not verify the HTTPS redirect. Check your Digital Asset Links at /.well-known/assetlinks.json."

/**
 * [WebAuthSession] for Android, backed by an Auth Tab (`androidx.browser`).
 *
 * The browser shares the user's passkeys, so a web sign-in page can run its
 * own passkey prompt.
 *
 * Needs a browser with Auth Tab support (Chrome 137+). On any other browser
 * the same intent opens a plain Custom Tab, which cannot hand the redirect
 * back to this call: the session then ends as `UserCanceled` when the user
 * closes the tab. If the activity is destroyed while the browser is open (for
 * example by a configuration change), the session ends as `Interrupted`.
 *
 * An [WebAuthRedirect.Https] redirect requires Digital Asset Links on the
 * redirect host.
 *
 * @param activity the activity the Auth Tab is launched from. It may be
 *   created at any point in the activity's lifecycle.
 */
public class AndroidWebAuthSession(
    private val activity: ComponentActivity,
) : WebAuthSession {
    override suspend fun authenticate(
        url: String,
        redirect: WebAuthRedirect,
        ephemeral: Boolean,
    ): PasskeyResult<String> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val mainHandler = Handler(Looper.getMainLooper())
                var launcher: ActivityResultLauncher<Intent>? = null
                var destroyObserver: LifecycleEventObserver? = null
                // Always posted: the registry is main-thread only (cancellation can
                // arrive on any thread), and it drops a launched key's mapping only
                // once the result callback has returned.
                val release =
                    Runnable {
                        launcher?.unregister()
                        destroyObserver?.let(activity.lifecycle::removeObserver)
                    }
                val finish = { outcome: PasskeyResult<String> ->
                    mainHandler.post(release)
                    if (continuation.isActive) continuation.resume(outcome)
                }
                // Registered per call, straight on the registry, so a session can
                // start after the activity is already STARTED (e.g. from Compose).
                val caller = RegistryCaller(activity.activityResultRegistry, "passkeys-auth-session-${nextKey.getAndIncrement()}")
                launcher =
                    AuthTabIntent.registerActivityResultLauncher(caller) { result ->
                        finish(authTabOutcome(result.resultCode, result.resultUri?.toString()))
                    }
                // A recreated activity gets a new registry this call is not on, so
                // the result could never arrive: end the session instead of hanging.
                destroyObserver =
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            finish(PasskeyResult.Failure(PasskeyException.Interrupted(IllegalStateException("the activity was destroyed"))))
                        }
                    }.also(activity.lifecycle::addObserver)
                continuation.invokeOnCancellation { mainHandler.post(release) }
                try {
                    val intent = AuthTabIntent.Builder().apply { if (ephemeral) setEphemeralBrowsingEnabled(true) }.build()
                    when (redirect) {
                        is WebAuthRedirect.CustomScheme -> intent.launch(launcher, Uri.parse(url), redirect.scheme)
                        is WebAuthRedirect.Https -> intent.launch(launcher, Uri.parse(url), redirect.host, redirect.path)
                    }
                } catch (e: ActivityNotFoundException) {
                    // No browser can open the URL.
                    finish(PasskeyResult.Failure(PasskeyException.Unsupported(e)))
                }
            }
        }

    private companion object {
        val nextKey = AtomicInteger()
    }
}

/** Maps an Auth Tab result to the session outcome. */
internal fun authTabOutcome(
    resultCode: Int,
    redirectUrl: String?,
): PasskeyResult<String> {
    val cause = IllegalStateException("Auth Tab finished with result code $resultCode")
    return when (resultCode) {
        AuthTabIntent.RESULT_OK ->
            redirectUrl?.let { PasskeyResult.Success(it) } ?: PasskeyResult.Failure(PasskeyException.Unexpected(cause))
        AuthTabIntent.RESULT_CANCELED -> PasskeyResult.Failure(PasskeyException.UserCanceled(cause))
        AuthTabIntent.RESULT_VERIFICATION_FAILED,
        AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT,
        -> PasskeyResult.Failure(PasskeyException.DomError(VERIFICATION_MESSAGE, cause))
        else -> PasskeyResult.Failure(PasskeyException.Unexpected(cause))
    }
}

/**
 * Lets `AuthTabIntent.registerActivityResultLauncher` (whose result contract is
 * not public) register directly on an [ActivityResultRegistry], without the
 * register-before-STARTED rule of a lifecycle-bound registration.
 */
private class RegistryCaller(
    private val registry: ActivityResultRegistry,
    private val key: String,
) : ActivityResultCaller {
    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>,
    ): ActivityResultLauncher<I> = registry.register(key, contract, callback)

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        registry: ActivityResultRegistry,
        callback: ActivityResultCallback<O>,
    ): ActivityResultLauncher<I> = registry.register(key, contract, callback)
}
