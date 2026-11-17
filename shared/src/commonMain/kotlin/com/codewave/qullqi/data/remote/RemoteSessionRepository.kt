package com.codewave.qullqi.data.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.codewave.qullqi.data.SqlRegistrationRepository
import com.codewave.qullqi.db.QullqiDatabase
import com.codewave.qullqi.domain.model.CurrentUser
import com.codewave.qullqi.domain.model.StoredUser
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.domain.repository.LoginResult
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.system.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Session against the backend (spec 004, contracts/api-mapping.md). The backend decides PIN,
 * attempts, lock and status; the SQL tables only cache the users seen on this device and the open
 * session, so [currentUser] (M4 contract) and [storedUsers] keep working offline.
 */
class RemoteSessionRepository(
    private val db: QullqiDatabase,
    private val api: QullqiApi,
    private val tokens: TokenStore,
    private val deviceId: () -> String,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : SessionRepository {

    private val q = db.userQueries
    private val expired = MutableSharedFlow<UserId>(extraBufferCapacity = 1)

    init {
        api.renew = ::renew
    }

    override val currentUser: Flow<CurrentUser?> =
        q.currentUser { id, name, phone, email, verification ->
            CurrentUser(UserId(id), name, phone, email, VerificationStatus.valueOf(verification))
        }.asFlow().mapToOneOrNull(io)

    override fun storedUsers(): Flow<List<StoredUser>> =
        q.storedUsers { id, name, phone, attemptsLeft ->
            StoredUser(UserId(id), name, phone, locked = attemptsLeft == 0L)
        }.asFlow().mapToList(io)

    override val sessionExpired: Flow<UserId> = expired.asSharedFlow()

    override suspend fun login(userId: UserId, pin: String): LoginResult {
        val phone = withContext(io) { q.phoneOf(userId.value).executeAsOneOrNull() } ?: return LoginResult.UnknownUser
        return loginWithPhone(phone, pin)
    }

    override suspend fun loginWithPhone(phone: String, pin: String): LoginResult {
        val normalized = SqlRegistrationRepository.normalizePhone(phone) ?: return LoginResult.UnknownUser
        return when (val r = api.login(LoginBody(normalized, pin, deviceId()))) {
            is ApiResult.Ok -> if (establish(r.value) != null) LoginResult.Success else LoginResult.Unavailable
            is ApiResult.Fail -> when (r.status) {
                401 -> {
                    val left = r.details.int("attempts_left", "attemptsLeft") ?: 0
                    cachedId(normalized)?.let { setAttempts(it, left) }
                    if (left == 0) LoginResult.Locked else LoginResult.WrongPin(left)
                }
                423 -> {
                    cachedId(normalized)?.let { setAttempts(it, 0) }
                    LoginResult.Locked
                }
                else -> LoginResult.UnknownUser
            }
            ApiResult.Unavailable -> LoginResult.Unavailable
        }
    }

    /** Fingerprint = app-level gate over the stored renewal credential (FR-008, research R3). */
    override suspend fun loginWithBiometrics(userId: UserId): LoginResult {
        val refresh = tokens.refreshToken(userId) ?: return LoginResult.SessionExpired
        return when (val r = api.refresh(RefreshBody(refresh, deviceId()))) {
            is ApiResult.Ok -> if (establish(r.value) != null) LoginResult.Success else LoginResult.Unavailable
            is ApiResult.Fail -> {
                tokens.clear(userId)
                if (r.status == 423) {
                    setAttempts(userId.value, 0)
                    LoginResult.Locked
                } else LoginResult.SessionExpired
            }
            ApiResult.Unavailable -> LoginResult.Unavailable
        }
    }

    override suspend fun refreshCurrentUser() {
        if (withContext(io) { q.currentSessionUserId().executeAsOneOrNull() } == null) return
        (api.me() as? ApiResult.Ok)?.let { cache(it.value) }
    }

    /** Always clears the device session, reachable or not (FR-016); never emits [sessionExpired]. */
    override suspend fun logout() {
        val id = withContext(io) { q.currentSessionUserId().executeAsOneOrNull() }
        if (id != null) api.logout(LogoutBody(deviceId()))
        id?.let { tokens.clear(UserId(it)) }
        dropAccess()
        withContext(io) {
            db.transaction {
                id?.let(q::markNotNew)
                q.closeSession()
            }
        }
    }

    /**
     * After login, registration or renewal: keep the tokens, load `/me`, cache the user and open the
     * session. Returns the user id, or null if the user could not be loaded.
     */
    internal suspend fun establish(t: TokensDto): UserId? {
        api.access = t.token
        api.clearBearer()
        val me = (api.me() as? ApiResult.Ok)?.value
        if (me == null) {
            dropAccess()
            return null
        }
        val id = UserId(me.userId)
        tokens.saveRefreshToken(id, t.refreshToken)
        cache(me)
        withContext(io) {
            db.transaction {
                q.setAttemptsLeft(3, me.userId)
                q.openSession(me.userId, clock.nowMillis())
            }
        }
        return id
    }

    internal suspend fun cachedId(phone: String): String? = withContext(io) { q.userByPhone(phone).executeAsOneOrNull() }

    internal suspend fun setAttempts(userId: String, left: Int) = withContext(io) { q.setAttemptsLeft(left.toLong(), userId) }

    /** Renewal used by the bearer plugin on 401. Null keeps the 401; a rejection ends the session once (FR-016a). */
    private suspend fun renew(): String? {
        val id = withContext(io) { q.currentSessionUserId().executeAsOneOrNull() }?.let(::UserId) ?: return null
        val refresh = tokens.refreshToken(id) ?: return expire(id)
        return when (val r = api.refresh(RefreshBody(refresh, deviceId()))) {
            is ApiResult.Ok -> {
                tokens.saveRefreshToken(id, r.value.refreshToken)
                api.access = r.value.token
                r.value.token
            }
            is ApiResult.Fail -> expire(id)
            ApiResult.Unavailable -> null
        }
    }

    private suspend fun expire(id: UserId): String? {
        tokens.clear(id)
        api.access = null
        withContext(io) { q.closeSession() }
        expired.tryEmit(id)
        return null
    }

    private suspend fun dropAccess() {
        api.access = null
        api.clearBearer()
    }

    private suspend fun cache(me: MeDto) = withContext(io) {
        val first = me.firstName.trim()
        val paternal = me.lastNamePaternal.trim()
        val status = VerificationStatus.entries.firstOrNull { it.name == me.verificationStatus } ?: VerificationStatus.NOT_VERIFIED
        q.upsertUser(
            id = me.userId,
            // user.full_name must not be blank (CHECK): the backend dev seed has empty names.
            full_name = "$first $paternal".trim().ifEmpty { FALLBACK_NAME },
            first_name = first,
            last_name_paternal = paternal,
            last_name_maternal = me.lastNameMaternal?.trim()?.ifEmpty { null },
            phone = me.phoneNumber,
            email = me.email.trim().lowercase(),
            verification = status.name,
            created_at = clock.nowMillis(),
        )
    }

    private companion object {
        const val FALLBACK_NAME = "Cliente"
    }
}
