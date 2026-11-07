package io.github.androidpoet.passkeys

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationCredentialProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs exactly one `ASAuthorizationController` flow and bridges it to a
 * coroutine.
 *
 * One instance per ceremony: it owns the controller, its delegate and the
 * presentation-context provider for the whole flow (Apple holds both only
 * weakly), completes the continuation at most once, and cancels the system
 * sheet when the calling coroutine is cancelled. Concurrent ceremonies on the
 * same client therefore never share or overwrite each other's callbacks.
 */
internal class PasskeyAuthorizationController(
    private val controller: ASAuthorizationController,
    anchor: () -> ASPresentationAnchor,
) {
    private val presentationProvider =
        object : NSObject(), ASAuthorizationControllerPresentationContextProvidingProtocol {
            override fun presentationAnchorForAuthorizationController(
                controller: ASAuthorizationController,
            ): ASPresentationAnchor = anchor()
        }

    private var delegate: ASAuthorizationControllerDelegateProtocol? = null

    /**
     * Performs the request and returns the credential, or throws the mapped
     * [PasskeyException]. Authorization UI work is dispatched to the main
     * queue, so this is safe to call from any dispatcher.
     */
    suspend fun run(): ASAuthorizationCredentialProtocol =
        suspendCancellableCoroutine { continuation ->
            var completed = false
            val delegate =
                object : NSObject(), ASAuthorizationControllerDelegateProtocol {
                    override fun authorizationController(
                        controller: ASAuthorizationController,
                        didCompleteWithAuthorization: ASAuthorization,
                    ) {
                        if (completed) return
                        completed = true
                        continuation.resume(didCompleteWithAuthorization.credential)
                    }

                    override fun authorizationController(
                        controller: ASAuthorizationController,
                        didCompleteWithError: NSError,
                    ) {
                        if (completed) return
                        completed = true
                        continuation.resumeWithException(didCompleteWithError.toPasskeyException())
                    }
                }
            this.delegate = delegate
            controller.delegate = delegate
            controller.presentationContextProvider = presentationProvider
            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    if (!completed) controller.cancel()
                }
            }
            dispatch_async(dispatch_get_main_queue()) { controller.performRequests() }
        }
}
