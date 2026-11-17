package com.codewave.qullqi.data.remote

import com.codewave.qullqi.domain.model.DeclineReason
import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.KycStatus
import com.codewave.qullqi.domain.model.SimpleDate
import com.codewave.qullqi.domain.model.Verification
import com.codewave.qullqi.domain.repository.KycField
import com.codewave.qullqi.domain.repository.KycRead
import com.codewave.qullqi.domain.repository.KycRepository
import com.codewave.qullqi.domain.repository.KycStart
import kotlinx.serialization.json.JsonObject

/** `GET`/`POST my/kyc` → domain results (spec 005, contracts/api-mapping.md). */
class RemoteKycRepository(private val api: QullqiApi) : KycRepository {

    override suspend fun current(): KycRead = when (val r = api.kycCurrent()) {
        // EXPIRED (provider session timed out) counts as "no verification": the user starts again.
        is ApiResult.Ok -> KycRead.Found(r.value?.takeIf { it.kycStatus != "EXPIRED" }?.toDomain())
        else -> KycRead.Unavailable
    }

    override suspend fun start(type: DocumentType, number: String, birthDate: SimpleDate): KycStart {
        val body = KycStartBody(ciType = type.name, ciNumber = number.trim(), birthDate = birthDate.iso())
        return when (val r = api.kycStart(body)) {
            // The backend answers PENDING with the page (change request C1): only the page matters.
            is ApiResult.Ok -> r.value.hostedUrl?.let { KycStart.Started(it) } ?: KycStart.Unavailable
            is ApiResult.Fail -> when {
                r.code == "kyc_already_verified" -> KycStart.AlreadyVerified
                r.code == "kyc_in_progress" -> KycStart.InProgress
                r.code == "ci_taken" -> KycStart.DocumentTaken
                r.code == "kyc_rejected" || r.status == 403 -> KycStart.Refused
                r.status == 422 -> KycStart.Invalid(fieldsOf(r.details))
                else -> KycStart.Unavailable
            }
            ApiResult.Unavailable -> KycStart.Unavailable
        }
    }

    private fun fieldsOf(details: JsonObject?): Set<KycField> {
        val keys = (details?.get("fields") as? JsonObject)?.keys.orEmpty()
        // No field detail (e.g. under-age refused as a whole): blame the birth date.
        return keys.mapNotNull { FIELDS[it] }.toSet().ifEmpty { setOf(KycField.BIRTH_DATE) }
    }

    private fun KycDto.toDomain() = Verification(
        // Unknown or COMPLETED (finished in the provider, decision pending) → in review.
        status = KycStatus.entries.firstOrNull { it.name == kycStatus } ?: KycStatus.PENDING,
        pageUrl = hostedUrl,
        declineReason = declineReason?.let { REASONS[it] ?: DeclineReason.OTHER },
    )

    private companion object {
        val FIELDS = mapOf("ciNumber" to KycField.NUMBER, "birthDate" to KycField.BIRTH_DATE, "ciType" to KycField.TYPE)
        val REASONS = mapOf(
            "data_mismatch" to DeclineReason.DATA_MISMATCH,
            "document_unreadable" to DeclineReason.DOCUMENT,
            "document_expired" to DeclineReason.DOCUMENT,
            "identity_not_verified" to DeclineReason.IDENTITY,
            "selfie_mismatch" to DeclineReason.IDENTITY,
        )
    }
}
