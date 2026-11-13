package com.codewave.qullqi.data

import com.codewave.qullqi.domain.model.AttemptState
import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.KycDraftChange
import com.codewave.qullqi.domain.model.PlaceholderImages
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.KycOutcome
import com.codewave.qullqi.domain.repository.MissingItem
import com.codewave.qullqi.domain.repository.StartResult
import com.codewave.qullqi.domain.repository.SubmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KycRepositoryTest {
    private val t = testDatabase()
    private val clock = FakeClock()
    private val repo = SqlKycRepository(t.db, clock, FakeIdGenerator("att"), MockKycProcessor(stepDelayMillis = 0), Dispatchers.Unconfined)
    private val unverified = UserId("usr-unverified")

    private fun status(id: String) =
        t.db.userQueries.userVerification(id).executeAsOne()

    private suspend fun started(user: UserId, type: DocumentType): String =
        (repo.start(user, type) as StartResult.Started).attemptId

    private suspend fun fill(id: String, type: DocumentType, number: String) {
        repo.update(id, KycDraftChange.DocumentNumber(number))
        repo.update(id, KycDraftChange.FrontImage(if (type == DocumentType.CI) PlaceholderImages.CI_FRONT else PlaceholderImages.PASSPORT))
        if (type == DocumentType.CI) repo.update(id, KycDraftChange.BackImage(PlaceholderImages.CI_BACK))
        repo.update(id, KycDraftChange.Selfie(PlaceholderImages.SELFIE))
        repo.update(id, KycDraftChange.ExtractedData("Mateo Demo Choque", number, "1996-01-01", "2032-01-01"))
        repo.update(id, KycDraftChange.DeclarationAccepted)
    }

    @Test fun verifiedUserCannotStart() = runBlocking {
        assertEquals(StartResult.AlreadyVerified, repo.start(UserId("usr-verified"), DocumentType.CI))
    }

    @Test fun onlyOneOpenAttempt() = runBlocking {
        assertIs<StartResult.Started>(repo.start(unverified, DocumentType.CI))
        assertEquals(StartResult.AttemptOpen, repo.start(unverified, DocumentType.PASSPORT))
    }

    @Test fun incompleteCiListsMissingItems() = runBlocking {
        val id = started(unverified, DocumentType.CI)
        repo.update(id, KycDraftChange.DocumentNumber("1234561"))
        repo.update(id, KycDraftChange.FrontImage(PlaceholderImages.CI_FRONT))
        repo.update(id, KycDraftChange.ExtractedData("Mateo Demo Choque", "1234561", "1996-01-01", "2032-01-01"))
        repo.update(id, KycDraftChange.DeclarationAccepted)
        assertEquals(SubmitResult.Missing(setOf(MissingItem.BACK_IMAGE, MissingItem.SELFIE)), repo.submit(id))
    }

    @Test fun passportDoesNotNeedBack() = runBlocking {
        val id = started(unverified, DocumentType.PASSPORT)
        fill(id, DocumentType.PASSPORT, "P123451")
        assertEquals(SubmitResult.Submitted, repo.submit(id))
    }

    @Test fun realImageReferenceRejected() = runBlocking {
        val id = started(unverified, DocumentType.CI)
        assertFailsWith<IllegalArgumentException> { repo.update(id, KycDraftChange.Selfie("file:///selfie.jpg")) }
        Unit
    }

    @Test fun outcomeByLastDigitUpdatesUserStatus() = runBlocking {
        for ((number, outcome, expected) in listOf(
            Triple("1234561", KycOutcome.Approved, "VERIFIED"),
            Triple("1234565", KycOutcome.UnderReview, "UNDER_REVIEW"),
            Triple("1234560", KycOutcome.Rejected("Documento ilegible"), "NOT_VERIFIED"),
        )) {
            val t2 = testDatabase()
            val r = SqlKycRepository(t2.db, clock, FakeIdGenerator("att"), MockKycProcessor(0), Dispatchers.Unconfined)
            val id = (r.start(unverified, DocumentType.CI) as StartResult.Started).attemptId
            r.update(id, KycDraftChange.DocumentNumber(number))
            r.update(id, KycDraftChange.FrontImage(PlaceholderImages.CI_FRONT))
            r.update(id, KycDraftChange.BackImage(PlaceholderImages.CI_BACK))
            r.update(id, KycDraftChange.Selfie(PlaceholderImages.SELFIE))
            r.update(id, KycDraftChange.ExtractedData("Mateo Demo Choque", number, "1996-01-01", "2032-01-01"))
            r.update(id, KycDraftChange.DeclarationAccepted)
            assertEquals(SubmitResult.Submitted, r.submit(id))
            assertEquals(outcome, r.process(id))
            assertEquals(expected, t2.db.userQueries.userVerification("usr-unverified").executeAsOne())
        }
    }

    @Test fun retryAfterRejectionKeepsHistory() = runBlocking {
        val rejected = UserId("usr-rejected")
        val id = started(rejected, DocumentType.CI)
        fill(id, DocumentType.CI, "1234561")
        repo.submit(id)
        repo.process(id)
        val history = repo.attempts(rejected).first()
        assertEquals(listOf(id, "att-rejected-1"), history.map { it.id })
        assertEquals(listOf(AttemptState.APPROVED, AttemptState.REJECTED), history.map { it.state })
        assertEquals("VERIFIED", status("usr-rejected"))
    }

    @Test fun discardOnlyDeletesDrafts() = runBlocking {
        val id = started(unverified, DocumentType.CI)
        repo.discard(id)
        assertEquals(0, repo.attempts(unverified).first().size)
        repo.discard("att-verified-1")
        assertEquals(1, repo.attempts(UserId("usr-verified")).first().size)
    }
}
