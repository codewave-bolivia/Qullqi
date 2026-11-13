package com.codewave.qullqi.data

import com.codewave.qullqi.domain.model.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountPreferenceRepositoryTest {
    private val t = testDatabase()
    private val clock = FakeClock()
    private val repo = SqlAccountPreferenceRepository(t.db, clock, Dispatchers.Unconfined)

    @Test fun mostRecentFallsBackToFirstByName() = runBlocking {
        // Names sorted: Andrés Nuevo Condori comes first.
        assertEquals("usr-new", repo.mostRecentUser()?.value)
    }

    @Test fun mostRecentFollowsLastLogin() = runBlocking {
        repo.recordLogin(UserId("usr-review"))
        clock.advance(1_000)
        repo.recordLogin(UserId("usr-verified"))
        assertEquals("usr-verified", repo.mostRecentUser()?.value)
    }

    @Test fun emptyDeviceHasNoUser() = runBlocking {
        assertNull(SqlAccountPreferenceRepository(testDatabase(withSamples = false).db, clock, Dispatchers.Unconfined).mostRecentUser())
    }

    @Test fun biometricsPreference() = runBlocking {
        val id = UserId("usr-verified")
        assertFalse(repo.preference(id).biometricsEnabled)
        repo.markBiometricsPrompted(id)
        assertTrue(repo.preference(id).biometricsPrompted)
        assertFalse(repo.preference(id).biometricsEnabled)
        repo.setBiometrics(id, true)
        assertTrue(repo.preference(id).biometricsEnabled)
    }

    @Test fun deletedUserCascades() = runBlocking {
        repo.recordLogin(UserId("usr-review"))
        t.exec("DELETE FROM user WHERE id = 'usr-review'")
        assertEquals(0, t.count("SELECT count(*) FROM account_preference"))
    }
}
