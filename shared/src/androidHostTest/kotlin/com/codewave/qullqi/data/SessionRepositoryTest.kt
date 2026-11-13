package com.codewave.qullqi.data

import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.LoginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionRepositoryTest {
    private val t = testDatabase()
    private val repo = SqlSessionRepository(t.db, Pbkdf2PinHasher(), FakeClock(), Dispatchers.Unconfined)
    private val verified = UserId("usr-verified")

    private fun attemptsLeft(id: String) = t.count("SELECT pin_attempts_left FROM user WHERE id = '$id'")

    @Test fun storedUsersListsSamplesWithLockFlag() = runBlocking {
        val users = repo.storedUsers().first()
        assertEquals(6, users.size)
        assertEquals(listOf("usr-locked"), users.filter { it.locked }.map { it.id.value })
    }

    @Test fun correctPinOpensSession() = runBlocking {
        assertNull(repo.currentUser.first())
        assertEquals(LoginResult.Success, repo.login(verified, "1234"))
        assertEquals(verified, repo.currentUser.first()?.id)
    }

    @Test fun wrongPinsDecrementThenLock() = runBlocking {
        assertEquals(LoginResult.WrongPin(2), repo.login(verified, "0000"))
        assertEquals(LoginResult.WrongPin(1), repo.login(verified, "0000"))
        assertEquals(LoginResult.Locked, repo.login(verified, "0000"))
        assertEquals(LoginResult.Locked, repo.login(verified, "1234"))
        assertNull(repo.currentUser.first())
    }

    @Test fun successResetsAttempts() = runBlocking {
        repo.login(verified, "0000")
        assertEquals(LoginResult.Success, repo.login(verified, "1234"))
        assertEquals(3, attemptsLeft("usr-verified"))
    }

    @Test fun lockedSampleUserIsRefused() = runBlocking {
        assertEquals(LoginResult.Locked, repo.login(UserId("usr-locked"), "1234"))
    }

    @Test fun unknownUser() = runBlocking {
        assertEquals(LoginResult.UnknownUser, repo.login(UserId("nope"), "1234"))
    }

    @Test fun logoutKeepsUserAndMarksNotNew() = runBlocking {
        val newUser = UserId("usr-new")
        repo.login(newUser, "1234")
        repo.logout()
        assertNull(repo.currentUser.first())
        assertEquals(6, repo.storedUsers().first().size)
        assertEquals(0, t.count("SELECT is_new FROM user WHERE id = 'usr-new'"))
    }

    @Test fun switchAccountReplacesSession() = runBlocking {
        repo.login(verified, "1234")
        repo.login(UserId("usr-review"), "1234")
        assertEquals("usr-review", repo.currentUser.first()?.id?.value)
        assertEquals(1, t.count("SELECT count(*) FROM current_session"))
    }
}
