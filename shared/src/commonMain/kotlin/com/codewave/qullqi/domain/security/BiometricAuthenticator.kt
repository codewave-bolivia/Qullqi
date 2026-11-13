package com.codewave.qullqi.domain.security

enum class BiometricResult { Success, Failed, Cancelled, Unavailable }

/** Device biometric check. Android implements it in androidApp; other platforms use [NoBiometrics]. */
interface BiometricAuthenticator {
    fun isAvailable(): Boolean
    suspend fun authenticate(title: String, subtitle: String): BiometricResult
}

/** Platforms without an implementation: the fingerprint key is never shown. */
object NoBiometrics : BiometricAuthenticator {
    override fun isAvailable() = false
    override suspend fun authenticate(title: String, subtitle: String) = BiometricResult.Unavailable
}
