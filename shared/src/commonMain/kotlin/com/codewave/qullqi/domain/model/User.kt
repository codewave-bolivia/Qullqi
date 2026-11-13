package com.codewave.qullqi.domain.model

import kotlin.jvm.JvmInline

/** Stable identifier of a user for its whole life. Part of the M4+ contract. */
@JvmInline
value class UserId(val value: String)

/** Exactly three statuses; a rejected verification returns the user to [NOT_VERIFIED]. */
enum class VerificationStatus { NOT_VERIFIED, UNDER_REVIEW, VERIFIED }

/** KYC gate: money operations are allowed only for verified users. */
fun VerificationStatus.canOperateMoney(): Boolean = this == VerificationStatus.VERIFIED

data class CurrentUser(
    val id: UserId,
    val displayName: String,
    /** "+591XXXXXXXX" */
    val phone: String,
    /** lowercase */
    val email: String,
    val verification: VerificationStatus,
)

/** A user stored on this device, as listed for login and "switch account". */
data class StoredUser(
    val id: UserId,
    val displayName: String,
    val phone: String,
    val locked: Boolean,
)
