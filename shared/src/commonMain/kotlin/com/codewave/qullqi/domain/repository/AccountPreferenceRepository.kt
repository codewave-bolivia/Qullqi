package com.codewave.qullqi.domain.repository

import com.codewave.qullqi.domain.model.UserId

data class AccountPreference(
    val biometricsEnabled: Boolean,
    /** "Activar huella" has already been offered (FR-018). */
    val biometricsPrompted: Boolean,
    val lastLoginAt: Long?,
)

interface AccountPreferenceRepository {
    /** Account of the latest login; if none ever logged in, the first by name; null on an empty device. */
    suspend fun mostRecentUser(): UserId?
    suspend fun preference(userId: UserId): AccountPreference
    suspend fun recordLogin(userId: UserId)
    suspend fun setBiometrics(userId: UserId, enabled: Boolean)
    suspend fun markBiometricsPrompted(userId: UserId)
}
