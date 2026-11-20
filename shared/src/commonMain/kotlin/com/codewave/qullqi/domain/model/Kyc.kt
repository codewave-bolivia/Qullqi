package com.codewave.qullqi.domain.model

/**
 * Identity verification (spec 005). The provider (Persona) captures the document and reads its
 * data; the backend owns the status. The app asks for no document data.
 */

/** `CREATED` = started, not finished in the provider · `PENDING` = in review. */
enum class KycStatus { CREATED, PENDING, APPROVED, DECLINED }

enum class DeclineReason { DATA_MISMATCH, DOCUMENT, IDENTITY, UNDERAGE, DOCUMENT_IN_USE, OTHER }

data class Verification(
    val status: KycStatus,
    /** Provider page, only while [KycStatus.CREATED]. Never stored or logged. */
    val pageUrl: String?,
    val declineReason: DeclineReason?,
)
