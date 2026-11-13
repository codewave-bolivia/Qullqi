package com.codewave.qullqi.domain.model

enum class DocumentType { CI, PASSPORT }

enum class AttemptState { DRAFT, SUBMITTED, APPROVED, REVIEW, REJECTED }

data class KycAttempt(
    val id: String,
    val userId: UserId,
    val documentType: DocumentType,
    val documentNumber: String?,
    val frontRef: String?,
    val backRef: String?,
    val selfieRef: String?,
    val extractedFullName: String?,
    val extractedDocumentNumber: String?,
    /** YYYY-MM-DD */
    val extractedBirthDate: String?,
    /** YYYY-MM-DD */
    val extractedExpiryDate: String?,
    val declarationAcceptedAt: Long?,
    val state: AttemptState,
    val rejectionReason: String?,
    val createdAt: Long,
    val finishedAt: Long?,
)

/** One change to a draft attempt. Image values must be [PlaceholderImages] references. */
sealed interface KycDraftChange {
    data class DocumentNumber(val value: String) : KycDraftChange
    data class FrontImage(val ref: String) : KycDraftChange
    data class BackImage(val ref: String) : KycDraftChange
    data class Selfie(val ref: String) : KycDraftChange
    data class ExtractedData(
        val fullName: String,
        val documentNumber: String,
        val birthDate: String,
        val expiryDate: String,
    ) : KycDraftChange
    data object DeclarationAccepted : KycDraftChange
}

/**
 * Real camera captures are never stored (spec 002, FR-019): attempts only reference
 * synthetic images bundled with the app.
 */
object PlaceholderImages {
    const val PREFIX = "placeholder:"
    const val CI_FRONT = "placeholder:ci-front"
    const val CI_BACK = "placeholder:ci-back"
    const val PASSPORT = "placeholder:passport"
    const val SELFIE = "placeholder:selfie"
}
