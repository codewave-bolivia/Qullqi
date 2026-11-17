package com.codewave.qullqi.domain.repository

import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.SimpleDate
import com.codewave.qullqi.domain.model.Verification

/** Identity verification against the backend (spec 005, contracts/kyc-repository.md). */
interface KycRepository {
    /** Latest verification of the logged-in user; `Found(null)` = never started (FR-002). */
    suspend fun current(): KycRead

    /** Creates a verification and returns the provider page; never retried automatically (FR-008). */
    suspend fun start(type: DocumentType, number: String, birthDate: SimpleDate): KycStart
}

sealed interface KycRead {
    data class Found(val verification: Verification?) : KycRead
    data object Unavailable : KycRead
}

sealed interface KycStart {
    data class Started(val pageUrl: String) : KycStart
    data object AlreadyVerified : KycStart
    /** A verification is already open: re-read with [KycRepository.current] and resume. */
    data object InProgress : KycStart
    data object DocumentTaken : KycStart
    data class Invalid(val fields: Set<KycField>) : KycStart
    /** No more attempts allowed: "Contacta a soporte". */
    data object Refused : KycStart
    data object Unavailable : KycStart
}

enum class KycField { NUMBER, BIRTH_DATE, TYPE }
