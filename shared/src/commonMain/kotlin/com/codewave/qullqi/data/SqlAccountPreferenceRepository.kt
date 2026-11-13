package com.codewave.qullqi.data

import com.codewave.qullqi.db.QullqiDatabase
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.AccountPreference
import com.codewave.qullqi.domain.repository.AccountPreferenceRepository
import com.codewave.qullqi.domain.system.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SqlAccountPreferenceRepository(
    private val db: QullqiDatabase,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : AccountPreferenceRepository {

    private val q = db.accountPreferenceQueries

    override suspend fun mostRecentUser(): UserId? = withContext(io) {
        q.mostRecentUser().executeAsOneOrNull()?.let(::UserId)
    }

    override suspend fun preference(userId: UserId): AccountPreference = withContext(io) {
        q.preferenceFor(userId.value).executeAsOneOrNull()
            ?.let { AccountPreference(it.biometrics_enabled == 1L, it.biometrics_prompted == 1L, it.last_login_at) }
            ?: AccountPreference(biometricsEnabled = false, biometricsPrompted = false, lastLoginAt = null)
    }

    override suspend fun recordLogin(userId: UserId) = write(userId) { q.recordLogin(clock.nowMillis(), it) }

    override suspend fun setBiometrics(userId: UserId, enabled: Boolean) =
        write(userId) { q.setBiometrics(if (enabled) 1L else 0L, it) }

    override suspend fun markBiometricsPrompted(userId: UserId) = write(userId) { q.markPrompted(it) }

    private suspend fun write(userId: UserId, block: (String) -> Unit) = withContext(io) {
        db.transaction {
            q.ensure(userId.value)
            block(userId.value)
        }
    }
}
