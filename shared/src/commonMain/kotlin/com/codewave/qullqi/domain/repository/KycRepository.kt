package com.codewave.qullqi.domain.repository

import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.KycAttempt
import com.codewave.qullqi.domain.model.KycDraftChange
import com.codewave.qullqi.domain.model.UserId
import kotlinx.coroutines.flow.Flow

interface KycRepository {
    /** Newest first; earlier attempts are never overwritten (FR-017). */
    fun attempts(userId: UserId): Flow<List<KycAttempt>>
    suspend fun start(userId: UserId, type: DocumentType): StartResult
    /** @throws IllegalArgumentException if an image value is not a placeholder reference. */
    suspend fun update(attemptId: String, change: KycDraftChange)
    suspend fun submit(attemptId: String): SubmitResult
    suspend fun process(attemptId: String): KycOutcome
    /** Deletes a draft; no-op for any other state. */
    suspend fun discard(attemptId: String)
}

sealed interface StartResult {
    data object AlreadyVerified : StartResult
    data object AttemptOpen : StartResult
    data class Started(val attemptId: String) : StartResult
}

enum class MissingItem { DOCUMENT_NUMBER, FRONT_IMAGE, BACK_IMAGE, SELFIE, EXTRACTED_DATA, DECLARATION }

sealed interface SubmitResult {
    data object Submitted : SubmitResult
    data class Missing(val items: Set<MissingItem>) : SubmitResult
}

sealed interface KycOutcome {
    data object Approved : KycOutcome
    data object UnderReview : KycOutcome
    data class Rejected(val reason: String) : KycOutcome
}
