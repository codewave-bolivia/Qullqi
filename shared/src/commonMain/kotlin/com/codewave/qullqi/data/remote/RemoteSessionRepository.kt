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
import kotlinx.coroutines.CancellationException
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
            is ApiResult.Ok -> if (establish(r.value, normalized) != null) LoginResult.Success else LoginResult.Unavailable
            is ApiResult.Fail -> when (r.status) {
                401 -> {
                    val left = r.details.int("attempts_left", "attemptsLeft") ?: 0
                    cachedId(normalized)?.let { setAttempts(it, left) }
                    if (left <= 0) LoginResult.Locked else LoginResult.WrongPin(left)
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
        local {
            db.transaction {
                id?.let(q::markNotNew)
                q.closeSession()
            }
        }
    }

    /**
     * After login, registration or renewal: keep the tokens, load `/me`, cache the user and open the
     * session. Returns the user id, or null if the user could not be loaded or cached.
     * [phoneHint] = the phone used to log in, used if `/me` sends one the cache cannot store.
     */
    internal suspend fun establish(t: TokensDto, phoneHint: String? = null): UserId? {
        api.access = t.token
        api.clearBearer()
        val me = (api.me() as? ApiResult.Ok)?.value
        if (me == null) {
            dropAccess()
            return null
        }
        val id = UserId(me.userId)
        val stored = local {
            db.transaction {
                cacheUser(me, phoneHint)
                q.setAttemptsLeft(3, me.userId)
                q.openSession(me.userId, clock.nowMillis())
            }
        }
        if (stored == null) {
            dropAccess()
            return null
        }
        tokens.saveRefreshToken(id, t.refreshToken)
        return id
    }

    internal suspend fun cachedId(phone: String): String? = withContext(io) { q.userByPhone(phone).executeAsOneOrNull() }

    /**
     * The cache column holds 0–3 (CHECK). The backend is the authority and may allow more attempts
     * (dev: 5), so the value is clamped here; the UI shows the backend's number from the result.
     */
    internal suspend fun setAttempts(userId: String, left: Int) {
        local { q.setAttemptsLeft(left.coerceIn(0, MAX_CACHED_ATTEMPTS).toLong(), userId) }
    }

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
        local { q.closeSession() }
        expired.tryEmit(id)
        return null
    }

    private suspend fun dropAccess() {
        api.access = null
        api.clearBearer()
    }

    private suspend fun cache(me: MeDto) {
        local { db.transaction { cacheUser(me, null) } }
    }

    /**
     * Backend data → cache row, made to satisfy the table's CHECKs: never trust the wire to match
     * local constraints (a violation would crash the app or be skipped silently by INSERT OR IGNORE).
     */
    private fun cacheUser(me: MeDto, phoneHint: String?) {
        val first = me.firstName?.trim()?.ifEmpty { null }
        val paternal = me.lastNamePaternal?.trim()?.ifEmpty { null }
        val status = VerificationStatus.entries.firstOrNull { it.name == me.verificationStatus } ?: VerificationStatus.NOT_VERIFIED
        // user.full_name must not be blank (CHECK): display name → legal names → fallback.
        val shown = me.displayName?.trim()?.ifEmpty { null }
            ?: listOfNotNull(first, paternal).joinToString(" ").ifEmpty { FALLBACK_NAME }
        q.upsertUser(
            id = me.userId,
            full_name = shown,
            first_name = first,
            last_name_paternal = paternal,
            last_name_maternal = me.lastNameMaternal?.trim()?.ifEmpty { null },
            phone = SqlRegistrationRepository.normalizePhone(me.phoneNumber)
                ?: phoneHint?.let(SqlRegistrationRepository::normalizePhone)
                ?: error("phone ${me.phoneNumber.take(4)}… cannot be cached"),
            email = me.email.trim().lowercase(),
            verification = status.name,
            created_at = clock.nowMillis(),
        )
    }

    /**
     * Runs a local database step off the main thread. A failure (constraint, I/O) must not crash the
     * app: it returns null and the caller reports the operation as unavailable.
     */
    private suspend fun <T> local(block: () -> T): T? = withContext(io) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val FALLBACK_NAME = "Cliente"
        const val MAX_CACHED_ATTEMPTS = 3
    }
}
