package com.codewave.qullqi.data.remote

import com.codewave.qullqi.domain.model.DeclineReason
import com.codewave.qullqi.domain.model.KycStatus
import com.codewave.qullqi.domain.model.Verification
import com.codewave.qullqi.domain.repository.KycRead
import com.codewave.qullqi.domain.repository.KycRepository
import com.codewave.qullqi.domain.repository.KycStart

/** `GET`/`POST my/kyc` → domain results (spec 005, contracts/api-mapping.md). */
class RemoteKycRepository(private val api: QullqiApi) : KycRepository {

    override suspend fun current(): KycRead = when (val r = api.kycCurrent()) {
        // EXPIRED (provider session timed out) counts as "no verification": the user starts again.
        is ApiResult.Ok -> KycRead.Found(r.value?.takeIf { it.kycStatus != "EXPIRED" }?.toDomain())
        else -> KycRead.Unavailable
    }

    override suspend fun start(): KycStart = when (val r = api.kycStart()) {
        // The backend may answer PENDING or CREATED with the page: only the page matters.
        is ApiResult.Ok -> r.value.hostedUrl?.let { KycStart.Started(it) } ?: KycStart.Unavailable
        is ApiResult.Fail -> when {
            r.code == "kyc_already_verified" -> KycStart.AlreadyVerified
            r.code == "kyc_in_progress" -> KycStart.InProgress
            r.code == "kyc_rejected" || r.status == 403 -> KycStart.Refused
            else -> KycStart.Unavailable
        }
        ApiResult.Unavailable -> KycStart.Unavailable
    }

    private fun KycDto.toDomain() = Verification(
        // Unknown or COMPLETED (finished in the provider, decision pending) → in review.
        status = KycStatus.entries.firstOrNull { it.name == kycStatus } ?: KycStatus.PENDING,
        pageUrl = hostedUrl,
        declineReason = declineReason?.let { REASONS[it] ?: DeclineReason.OTHER },
    )

    private companion object {
        val REASONS = mapOf(
            "data_mismatch" to DeclineReason.DATA_MISMATCH,
            "document_unreadable" to DeclineReason.DOCUMENT,
            "document_expired" to DeclineReason.DOCUMENT,
            "identity_not_verified" to DeclineReason.IDENTITY,
            "selfie_mismatch" to DeclineReason.IDENTITY,
            "underage" to DeclineReason.UNDERAGE,
            "document_in_use" to DeclineReason.DOCUMENT_IN_USE,
        )
    }
}
