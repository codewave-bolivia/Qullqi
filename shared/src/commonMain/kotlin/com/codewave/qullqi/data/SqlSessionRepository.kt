package com.codewave.qullqi.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.codewave.qullqi.db.QullqiDatabase
import com.codewave.qullqi.domain.model.CurrentUser
import com.codewave.qullqi.domain.model.StoredUser
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.domain.repository.LoginResult
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.security.PinHash
import com.codewave.qullqi.domain.security.PinHasher
import com.codewave.qullqi.domain.system.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlSessionRepository(
    private val db: QullqiDatabase,
    private val hasher: PinHasher,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : SessionRepository {

    private val q = db.userQueries

    override val currentUser: Flow<CurrentUser?> =
        q.currentUser { id, name, phone, email, verification ->
            CurrentUser(UserId(id), name, phone, email, VerificationStatus.valueOf(verification))
        }.asFlow().mapToOneOrNull(io)

    override fun storedUsers(): Flow<List<StoredUser>> =
        q.storedUsers { id, name, phone, attemptsLeft ->
            StoredUser(UserId(id), name, phone, locked = attemptsLeft == 0L)
        }.asFlow().mapToList(io)

    override suspend fun login(userId: UserId, pin: String): LoginResult = withContext(io) {
        db.transactionWithResult {
            val left = q.attemptsLeft(userId.value).executeAsOneOrNull() ?: return@transactionWithResult LoginResult.UnknownUser
            if (left == 0L) return@transactionWithResult LoginResult.Locked
            val cred = q.credentialFor(userId.value).executeAsOne()
            if (!hasher.verify(pin, PinHash(cred.pin_hash, cred.pin_salt, cred.iterations.toInt()))) {
                q.decrementAttempts(userId.value)
                val now = q.attemptsLeft(userId.value).executeAsOne().toInt()
                return@transactionWithResult if (now == 0) LoginResult.Locked else LoginResult.WrongPin(now)
            }
            q.resetAttempts(userId.value)
            q.openSession(userId.value, clock.nowMillis())
            LoginResult.Success
        }
    }

    override suspend fun loginWithBiometrics(userId: UserId): LoginResult = withContext(io) {
        db.transactionWithResult {
            val left = q.attemptsLeft(userId.value).executeAsOneOrNull() ?: return@transactionWithResult LoginResult.UnknownUser
            // A locked account can only be recovered with the PIN flow, never with biometrics.
            if (left == 0L) return@transactionWithResult LoginResult.Locked
            q.openSession(userId.value, clock.nowMillis())
            LoginResult.Success
        }
    }

    override suspend fun logout() = withContext(io) {
        db.transaction {
            // A user stops being "new" once their first session is completed (FR-001).
            q.currentSessionUserId().executeAsOneOrNull()?.let(q::markNotNew)
            q.closeSession()
        }
    }
}
