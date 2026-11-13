package com.codewave.qullqi.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.codewave.qullqi.db.Kyc_attempt
import com.codewave.qullqi.db.QullqiDatabase
import com.codewave.qullqi.domain.model.AttemptState
import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.KycAttempt
import com.codewave.qullqi.domain.model.KycDraftChange
import com.codewave.qullqi.domain.model.PlaceholderImages
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.KycOutcome
import com.codewave.qullqi.domain.repository.KycRepository
import com.codewave.qullqi.domain.repository.MissingItem
import com.codewave.qullqi.domain.repository.StartResult
import com.codewave.qullqi.domain.repository.SubmitResult
import com.codewave.qullqi.domain.system.Clock
import com.codewave.qullqi.domain.system.IdGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlKycRepository(
    private val db: QullqiDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val processor: MockKycProcessor,
    private val io: CoroutineDispatcher,
) : KycRepository {

    private val q = db.kycAttemptQueries

    override fun attempts(userId: UserId): Flow<List<KycAttempt>> =
        q.attemptsForUser(userId.value).asFlow().mapToList(io).map { rows -> rows.map(::toDomain) }

    override suspend fun start(userId: UserId, type: DocumentType): StartResult = withContext(io) {
        db.transactionWithResult<StartResult> {
            val status = db.userQueries.userVerification(userId.value).executeAsOne()
            when {
                status == "VERIFIED" -> StartResult.AlreadyVerified
                q.openAttemptForUser(userId.value).executeAsOneOrNull() != null -> StartResult.AttemptOpen
                else -> {
                    val id = ids.newId()
                    q.insertDraft(id, userId.value, type.name, clock.nowMillis())
                    StartResult.Started(id)
                }
            }
        }
    }

    override suspend fun update(attemptId: String, change: KycDraftChange): Unit = withContext(io) {
        when (change) {
            is KycDraftChange.DocumentNumber -> q.setDocumentNumber(change.value.trim(), attemptId)
            is KycDraftChange.FrontImage -> q.setFront(placeholder(change.ref), attemptId)
            is KycDraftChange.BackImage -> q.setBack(placeholder(change.ref), attemptId)
            is KycDraftChange.Selfie -> q.setSelfie(placeholder(change.ref), attemptId)
            is KycDraftChange.ExtractedData -> q.setExtracted(
                change.fullName.trim(), change.documentNumber.trim(), change.birthDate, change.expiryDate, attemptId,
            )
            KycDraftChange.DeclarationAccepted -> q.setDeclaration(clock.nowMillis(), attemptId)
        }
        Unit
    }

    override suspend fun submit(attemptId: String): SubmitResult = withContext(io) {
        db.transactionWithResult<SubmitResult> {
            val a = q.attemptById(attemptId).executeAsOne()
            val missing = buildSet {
                if (a.document_number.isNullOrBlank()) add(MissingItem.DOCUMENT_NUMBER)
                if (a.front_ref == null) add(MissingItem.FRONT_IMAGE)
                if (a.document_type == DocumentType.CI.name && a.back_ref == null) add(MissingItem.BACK_IMAGE)
                if (a.selfie_ref == null) add(MissingItem.SELFIE)
                if (listOf(a.extracted_full_name, a.extracted_document_number, a.extracted_birth_date, a.extracted_expiry_date)
                        .any { it.isNullOrBlank() }) add(MissingItem.EXTRACTED_DATA)
                if (a.declaration_accepted_at == null) add(MissingItem.DECLARATION)
            }
            if (missing.isNotEmpty()) return@transactionWithResult SubmitResult.Missing(missing)
            q.setSubmitted(attemptId)
            SubmitResult.Submitted
        }
    }

    override suspend fun process(attemptId: String): KycOutcome = withContext(io) {
        val number = q.attemptById(attemptId).executeAsOne().document_number.orEmpty()
        val outcome = processor.decide(number)
        val (state, reason) = when (outcome) {
            KycOutcome.Approved -> AttemptState.APPROVED to null
            KycOutcome.UnderReview -> AttemptState.REVIEW to null
            is KycOutcome.Rejected -> AttemptState.REJECTED to outcome.reason
        }
        // The kyc_attempt_final_sets_user_status trigger updates the user's status (FR-016).
        q.setFinal(state.name, clock.nowMillis(), reason, attemptId)
        outcome
    }

    override suspend fun discard(attemptId: String) = withContext(io) {
        q.deleteDraft(attemptId)
        Unit
    }

    private fun placeholder(ref: String): String {
        require(ref.startsWith(PlaceholderImages.PREFIX)) { "Only placeholder image references can be stored (FR-019)" }
        return ref
    }

    private fun toDomain(r: Kyc_attempt) = KycAttempt(
        id = r.id,
        userId = UserId(r.user_id),
        documentType = DocumentType.valueOf(r.document_type),
        documentNumber = r.document_number,
        frontRef = r.front_ref,
        backRef = r.back_ref,
        selfieRef = r.selfie_ref,
        extractedFullName = r.extracted_full_name,
        extractedDocumentNumber = r.extracted_document_number,
        extractedBirthDate = r.extracted_birth_date,
        extractedExpiryDate = r.extracted_expiry_date,
        declarationAcceptedAt = r.declaration_accepted_at,
        state = AttemptState.valueOf(r.state),
        rejectionReason = r.rejection_reason,
        createdAt = r.created_at,
        finishedAt = r.finished_at,
    )
}
