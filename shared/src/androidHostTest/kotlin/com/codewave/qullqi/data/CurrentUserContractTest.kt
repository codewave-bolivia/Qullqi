package com.codewave.qullqi.data

import com.codewave.qullqi.domain.model.CurrentUser
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.domain.model.canOperateMoney
import com.codewave.qullqi.domain.repository.CurrentUserSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises only what M4+ may rely on (contracts/current-user.md): [CurrentUserSource] and the
 * contract types. The session repository and a cache update (as /me does) cause the changes.
 */
class CurrentUserContractTest {
    private val t = testDatabase()
    private val session = SqlSessionRepository(t.db, Pbkdf2PinHasher(), FakeClock(), Dispatchers.Default)
    private val source: CurrentUserSource = session

    private suspend fun awaitUser(predicate: (CurrentUser?) -> Boolean): CurrentUser? =
        withTimeout(5_000) { source.currentUser.first(predicate) }

    @Test fun noSessionEmitsNull() = runBlocking {
        assertNull(withTimeout(5_000) { source.currentUser.first() })
    }

    @Test fun emitsOnLoginSwitchAndLogout() = runBlocking {
        session.login(UserId("usr-verified"), "1234")
        assertEquals("usr-verified", awaitUser { it != null }?.id?.value)
        session.login(UserId("usr-review"), "1234")
        val switched = awaitUser { it?.id?.value == "usr-review" }!!
        assertEquals(VerificationStatus.UNDER_REVIEW, switched.verification)
        session.logout()
        assertNull(awaitUser { it == null })
    }

    @Test fun emitsWhenVerificationStatusChanges() = runBlocking {
        val user = UserId("usr-unverified")
        session.login(user, "1234")
        assertEquals(VerificationStatus.NOT_VERIFIED, awaitUser { it != null }?.verification)

        // Subscribe BEFORE the change: if the trigger-driven update were not notified, this would time out.
        val seen = mutableListOf<VerificationStatus?>()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                source.currentUser.first { seen += it?.verification; it?.verification == VerificationStatus.VERIFIED }
            }
        }
        // The backend changes the status; the app learns it through /me → cache (spec 004/005).
        t.db.userQueries.upsertUser(
            id = "usr-unverified", full_name = "Mateo Demo Choque", first_name = "Mateo", last_name_paternal = "Demo",
            last_name_maternal = null, phone = "+59170000002", email = "usr-unverified@example.com",
            verification = "VERIFIED", created_at = 1,
        )
        val verified = waiter.await()!!
        assertEquals(VerificationStatus.NOT_VERIFIED, seen.first(), "collector must have started before the change")
        assertTrue(verified.verification.canOperateMoney())
    }

    @Test fun onlyVerifiedCanOperateMoney() {
        assertTrue(VerificationStatus.VERIFIED.canOperateMoney())
        assertFalse(VerificationStatus.UNDER_REVIEW.canOperateMoney())
        assertFalse(VerificationStatus.NOT_VERIFIED.canOperateMoney())
    }
}
