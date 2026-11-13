package com.codewave.qullqi

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.codewave.qullqi.domain.security.BiometricAuthenticator
import com.codewave.qullqi.domain.security.BiometricResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real device biometrics (spec 003 US5, clarified). Success only lets the shared code open the
 * session for a non-locked account; it never reveals or replaces the PIN.
 */
class AndroidBiometricAuthenticator(private val activity: FragmentActivity) : BiometricAuthenticator {

    private val allowed = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    override fun isAvailable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(title: String, subtitle: String): BiometricResult {
        if (!isAvailable()) return BiometricResult.Unavailable
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(BiometricResult.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val r = when (errorCode) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED -> BiometricResult.Cancelled
                            else -> BiometricResult.Failed
                        }
                        if (cont.isActive) cont.resume(r)
                    }
                    // onAuthenticationFailed = one unrecognised finger; the prompt stays open, so no result yet.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Usar PIN")
                .setAllowedAuthenticators(allowed)
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}
