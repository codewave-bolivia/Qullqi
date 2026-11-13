package com.codewave.qullqi.data

import com.codewave.qullqi.domain.model.CurrentUser
import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.KycDraftChange
import com.codewave.qullqi.domain.model.PlaceholderImages
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.domain.model.canOperateMoney
import com.codewave.qullqi.domain.repository.CurrentUserSource
import com.codewave.qullqi.domain.repository.StartResult
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
 * contract types. Session and KYC repositories are used only to cause changes.
 */
class CurrentUserContractTest {
    private val t = testDatabase()
    private val session = SqlSessionRepository(t.db, Pbkdf2PinHasher(), FakeClock(), Dispatchers.Default)
    private val kyc = SqlKycRepository(t.db, FakeClock(), FakeIdGenerator("att"), MockKycProcessor(0), Dispatchers.Default)
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

        // Collect continuously so a missed notification (status changed by the DB trigger) would time out.
        val id = (kyc.start(user, DocumentType.PASSPORT) as StartResult.Started).attemptId
        kyc.update(id, KycDraftChange.DocumentNumber("P1234561"))
        kyc.update(id, KycDraftChange.FrontImage(PlaceholderImages.PASSPORT))
        kyc.update(id, KycDraftChange.Selfie(PlaceholderImages.SELFIE))
        kyc.update(id, KycDraftChange.ExtractedData("Mateo Demo Choque", "P1234561", "1996-01-01", "2032-01-01"))
        kyc.update(id, KycDraftChange.DeclarationAccepted)
        kyc.submit(id)

        // Subscribe BEFORE the change: if the trigger-driven update were not notified, this would time out.
        val seen = mutableListOf<VerificationStatus?>()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                source.currentUser.first { seen += it?.verification; it?.verification == VerificationStatus.VERIFIED }
            }
        }
        kyc.process(id)
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
