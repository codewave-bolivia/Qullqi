package com.codewave.qullqi.domain.repository

import com.codewave.qullqi.domain.model.CurrentUser
import com.codewave.qullqi.domain.model.StoredUser
import com.codewave.qullqi.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/** Contract for M4+ (specs/002-local-data-layer/contracts/current-user.md). */
interface CurrentUserSource {
    /**
     * Emits the logged-in user, or null when there is no session. Emits again on login,
     * logout, account switch and any verification status change.
     */
    val currentUser: Flow<CurrentUser?>
}

interface SessionRepository : CurrentUserSource {
    fun storedUsers(): Flow<List<StoredUser>>
    suspend fun login(userId: UserId, pin: String): LoginResult
    /** Opens the session after a successful device biometric check; never touches PIN attempts. */
    suspend fun loginWithBiometrics(userId: UserId): LoginResult
    /** Login for an account not stored on this device (spec 004, FR-014). */
    suspend fun loginWithPhone(phone: String, pin: String): LoginResult
    /** Reloads the current user from the source of truth; silent when unreachable (FR-017). */
    suspend fun refreshCurrentUser()
    suspend fun logout()
    /** Emits the user id when the session ends because a renewal was rejected; never on [logout] (FR-016a). */
    val sessionExpired: Flow<UserId>
}

sealed interface LoginResult {
    data object Success : LoginResult
    data class WrongPin(val attemptsLeft: Int) : LoginResult
    data object Locked : LoginResult
    data object UnknownUser : LoginResult
    /** Fingerprint login could not renew the session: the PIN is required (FR-008). */
    data object SessionExpired : LoginResult
    /** No connection or server error; nothing was consumed (FR-003). */
    data object Unavailable : LoginResult
}
