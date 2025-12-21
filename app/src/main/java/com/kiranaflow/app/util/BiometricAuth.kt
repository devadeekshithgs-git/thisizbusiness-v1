package com.kiranaflow.app.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object BiometricAuth {
    /**
     * Shows biometric/device-credential prompt. Returns true if authenticated.
     *
     * Requires [context] to be a [FragmentActivity].
     */
    suspend fun authenticate(
        context: Context,
        title: String = "Unlock",
        subtitle: String = "Verify to view sensitive numbers"
    ): Boolean {
        val activity = context as? FragmentActivity
            ?: return false

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val manager = BiometricManager.from(activity)
        val canAuth = manager.canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return false

        val executor = ContextCompat.getMainExecutor(activity)

        return suspendCancellableCoroutine { cont ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }

                override fun onAuthenticationFailed() {
                    // User can retry; don't complete here.
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()

            prompt.authenticate(info)

            cont.invokeOnCancellation {
                runCatching { prompt.cancelAuthentication() }
            }
        }
    }
}


