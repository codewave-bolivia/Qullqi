package com.codewave.qullqi.data.remote

import com.codewave.qullqi.data.remote.MockBackend.Companion.error
import com.codewave.qullqi.data.remote.MockBackend.Companion.me
import com.codewave.qullqi.data.remote.MockBackend.Companion.tokens
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.domain.repository.LoginResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** contracts/api-mapping.md rows: login, biometrics, renewal, logout, current user (spec 004 US1, US3, US4, US6). */
class RemoteSessionRepositoryTest {
    private val u1 = UserId("u-1")

    private fun loggedIn(): RemoteEnv = RemoteEnv().apply {
        backend.on("auth/login", 200, tokens(1))
        backend.on("me", 200, me())
        runBlocking { assertEquals(LoginResult.Success, session.loginWithPhone("70000001", "1234")) }
    }

    @Test fun loginCachesBackendUserAndKeepsSecretsOutOfDatabase() = runBlocking {
        val env = loggedIn()
        val user = env.session.currentUser.first()!!
        assertEquals("Lucía Flores", user.displayName)
        assertEquals("lucia@example.com", user.email)
        assertEquals(VerificationStatus.VERIFIED, user.verification)
        assertEquals("refresh-1", env.tokens.refreshToken(u1))
        val login = env.backend.requests.first { it.path == "auth/login" }
        assertTrue(login.body.contains("\"phoneNumber\":\"+59170000001\"") && login.body.contains("\"deviceId\":\"id-1\""))
        assertNull(login.auth) // public endpoint: no session token
        assertEquals("Bearer access-1", env.backend.requests.first { it.path == "me" }.auth)
        // FR-005: no PIN or token anywhere in the database.
        val dump = env.t.db.userQueries.storedUsers().executeAsList().toString() + env.t.count("SELECT count(*) FROM credential")
        assertFalse(dump.contains("1234") || dump.contains("refresh-1") || dump.contains("access-1"))
        assertEquals(0, env.t.count("SELECT count(*) FROM credential"))
    }

    @Test fun wrongPinLockedUnknownAndOffline() = runBlocking {
        val env = loggedIn()
        env.backend.on("auth/login", 401, error("invalid_pin", """{"attempts_left":2}"""))
        assertEquals(LoginResult.WrongPin(2), env.session.login(u1, "0000"))
        assertEquals(2, env.t.count("SELECT pin_attempts_left FROM user WHERE id = 'u-1'"))
        env.backend.on("auth/login", 423, error("pin_locked"))
        assertEquals(LoginResult.Locked, env.session.login(u1, "0000"))
        assertTrue(env.session.storedUsers().first().single().locked)
        env.backend.on("auth/login", 422, error("validation_failed"))
        assertEquals(LoginResult.UnknownUser, env.session.loginWithPhone("70000009", "1234"))
        env.backend.offline = true
        assertEquals(LoginResult.Unavailable, env.session.login(u1, "1234"))
        assertEquals(0, env.t.count("SELECT pin_attempts_left FROM user WHERE id = 'u-1'")) // unchanged
    }

    @Test fun expiredAccessRenewsOnceForConcurrentRequests() = runBlocking {
        val env = loggedIn()
        env.backend.sequence("me", 401 to error("unauthorized"), 200 to me(status = "UNDER_REVIEW")) // access expired
        env.backend.on("auth/refresh", 200, tokens(2))
        listOf(async { env.session.refreshCurrentUser() }, async { env.session.refreshCurrentUser() }).awaitAll()
        assertEquals(1, env.backend.count("auth/refresh"))
        assertEquals("refresh-2", env.tokens.refreshToken(u1)) // rotated
        assertEquals(VerificationStatus.UNDER_REVIEW, env.session.currentUser.first()!!.verification)
    }

    @Test fun rejectedRenewalEndsSessionOnceAndLogoutNever() = runBlocking {
        val env = loggedIn()
        val emitted = async { withTimeoutOrNull(2_000) { env.session.sessionExpired.first() } }
        env.backend.on("me", 401, error("unauthorized"))
        env.backend.on("auth/refresh", 401, error("unauthorized"))
        env.session.refreshCurrentUser()
        assertEquals(u1, emitted.await())
        assertNull(env.session.currentUser.first())
        assertNull(env.tokens.refreshToken(u1))

        val env2 = loggedIn()
        val none = async { withTimeoutOrNull(300) { env2.session.sessionExpired.first() } }
        env2.backend.on("auth/logout", 204, "")
        env2.session.logout()
        assertNull(none.await())
    }

    @Test fun biometricLoginUsesStoredCredential() = runBlocking {
        val env = loggedIn()
        env.session.logout() // clears the credential
        assertEquals(LoginResult.SessionExpired, env.session.loginWithBiometrics(u1))

        val env2 = loggedIn()
        env2.backend.on("auth/refresh", 200, tokens(3))
        assertEquals(LoginResult.Success, env2.session.loginWithBiometrics(u1))
        assertEquals("refresh-3", env2.tokens.refreshToken(u1))
        env2.backend.on("auth/refresh", 423, error("pin_locked"))
        assertEquals(LoginResult.Locked, env2.session.loginWithBiometrics(u1))
        assertNull(env2.tokens.refreshToken(u1))
    }

    @Test fun logoutClearsLocallyEvenOffline() = runBlocking {
        val env = loggedIn()
        env.backend.on("auth/logout", 204, "")
        env.session.logout()
        val call = env.backend.requests.last()
        assertEquals("auth/logout", call.path)
        assertEquals("Bearer access-1", call.auth)
        assertNull(env.session.currentUser.first())

        val env2 = loggedIn()
        env2.backend.offline = true
        env2.session.logout()
        assertNull(env2.session.currentUser.first())
        assertNull(env2.tokens.refreshToken(u1))
    }

    @Test fun currentUserKeepsLastValueOfflineAndMaternalOptional() = runBlocking {
        val env = RemoteEnv()
        env.backend.on("auth/login", 200, tokens(1))
        env.backend.on("me", 200, me(maternal = null))
        env.session.loginWithPhone("70000001", "1234")
        assertEquals("Lucía Flores", env.session.currentUser.first()!!.displayName)
        env.backend.offline = true
        env.session.refreshCurrentUser()
        assertEquals("Lucía Flores", env.session.currentUser.first()!!.displayName)
    }

    /** Backend code vs its doc (spec 004 Phase 10): `/me` sends `id`, `invalid_pin` sends `attemptsLeft`. */
    @Test fun toleratesBackendSpellings() = runBlocking {
        val env = RemoteEnv()
        env.backend.on("auth/login", 200, tokens(1))
        env.backend.on("me", 200, me(idKey = "id"))
        assertEquals(LoginResult.Success, env.session.loginWithPhone("70000001", "1234"))
        assertEquals(u1, env.session.currentUser.first()!!.id)
        env.backend.on("auth/login", 401, error("invalid_pin", """{"attemptsLeft":2}"""))
        assertEquals(LoginResult.WrongPin(2), env.session.login(u1, "0000"))
    }

    /** Device crash 2026-11-17: dev seed users have empty names; a stale row may hold the phone. */
    @Test fun blankNamesAndStalePhoneRowStillOpenSession() = runBlocking {
        val env = RemoteEnv()
        env.t.exec("INSERT INTO user (id, full_name, phone, email, created_at) VALUES ('old', 'Vieja', '+59170000001', 'x@example.com', 1)")
        env.backend.on("auth/login", 200, tokens(1))
        env.backend.on("me", 200, me(idKey = "id", id = "11111111-1111-1111-1111-111111111001", first = "", paternal = "", maternal = null))
        assertEquals(LoginResult.Success, env.session.loginWithPhone("70000001", "1234"))
        val user = env.session.currentUser.first()!!
        assertEquals("Cliente", user.displayName)
        assertEquals(1, env.t.count("SELECT count(*) FROM user"))
    }

    /** Device crash 2026-11-17: the backend allows 5 attempts; the 0–3 cache column must not throw. */
    @Test fun backendAttemptsAboveCacheRangeDoNotCrash() = runBlocking {
        val env = loggedIn()
        env.backend.on("auth/login", 401, error("invalid_pin", """{"attemptsLeft":4}"""))
        assertEquals(LoginResult.WrongPin(4), env.session.login(u1, "0000")) // UI shows the backend's number
        assertEquals(3, env.t.count("SELECT pin_attempts_left FROM user WHERE id = 'u-1'"))
        env.backend.on("auth/login", 401, error("invalid_pin", """{"attemptsLeft":-1}"""))
        assertEquals(LoginResult.Locked, env.session.login(u1, "0000"))
    }

    /** A /me the cache cannot store (bad phone) reports Unavailable instead of crashing on the FK. */
    @Test fun uncacheableUserIsUnavailableNotCrash() = runBlocking {
        val env = RemoteEnv()
        env.backend.on("auth/login", 200, tokens(1))
        env.backend.on("me", 200, MockBackend.ok("""{"id":"u-9","phoneNumber":"+15550000","email":"a@b.co","firstName":"A","lastNamePaternal":"B","verificationStatus":"VERIFIED"}"""))
        // Login phone is valid: it is used as the cache phone instead.
        assertEquals(LoginResult.Success, env.session.loginWithPhone("70000001", "1234"))
        val env2 = RemoteEnv()
        env2.backend.on("me", 200, MockBackend.ok("""{"id":"u-9","phoneNumber":"+15550000","email":"a@b.co","firstName":"A","lastNamePaternal":"B","verificationStatus":"VERIFIED"}"""))
        env2.tokens.saveRefreshToken(UserId("u-9"), "rt")
        env2.backend.on("auth/refresh", 200, tokens(2))
        assertEquals(LoginResult.Unavailable, env2.session.loginWithBiometrics(UserId("u-9"))) // no phone hint
        assertNull(env2.session.currentUser.first())
    }

    /** Backend f102c1d: `displayName` always present, legal names null until KYC (spec 004 Phase 12). */
    @Test fun displayNameWinsAndNullLegalNamesLogIn() = runBlocking {
        val env = RemoteEnv()
        env.backend.on("auth/login", 200, tokens(1))
        env.backend.on("me", 200, me(idKey = "id", displayName = "Luci", first = null, paternal = null, maternal = null))
        assertEquals(LoginResult.Success, env.session.loginWithPhone("70000001", "1234"))
        assertEquals("Luci", env.session.currentUser.first()!!.displayName)
        assertEquals(1, env.t.count("SELECT count(*) FROM user WHERE first_name IS NULL AND last_name_paternal IS NULL"))
        // Older payload without displayName: legal names, then the fallback.
        env.backend.on("me", 200, me(first = null, paternal = null, maternal = null))
        env.session.refreshCurrentUser()
        assertEquals("Cliente", env.session.currentUser.first()!!.displayName)
    }
}
