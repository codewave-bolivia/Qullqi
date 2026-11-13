package com.codewave.qullqi.data

import com.codewave.qullqi.domain.repository.KycOutcome
import kotlinx.coroutines.delay

/**
 * Stand-in for a verification backend (spec 002, FR-018). The outcome is chosen by the last digit
 * of the document number so testers can reproduce each one: 0 → rejected, 5 → under review,
 * anything else → approved. Four steps of [stepDelayMillis], as in the reference design.
 */
class MockKycProcessor(private val stepDelayMillis: Long = 700) {
    suspend fun decide(documentNumber: String): KycOutcome {
        repeat(STEPS) { delay(stepDelayMillis) }
        return when (documentNumber.lastOrNull()) {
            '0' -> KycOutcome.Rejected(REJECTION_REASON)
            '5' -> KycOutcome.UnderReview
            else -> KycOutcome.Approved
        }
    }

    companion object {
        const val REJECTION_REASON = "Documento ilegible"
        private const val STEPS = 4
    }
}
