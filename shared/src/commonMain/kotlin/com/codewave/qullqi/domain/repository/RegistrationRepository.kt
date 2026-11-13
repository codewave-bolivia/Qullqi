package com.codewave.qullqi.domain.repository

import com.codewave.qullqi.domain.model.UserId

interface RegistrationRepository {
    suspend fun requestCode(phone: String): CodeRequestResult
    suspend fun confirmCode(phone: String, code: String): CodeConfirmResult
    /** Creates the user as NOT_VERIFIED and opens their session. */
    suspend fun register(form: RegistrationForm): RegistrationResult
    /** Needs a recently confirmed code for the phone; unlocks the user. */
    suspend fun resetPin(phone: String, newPin: String): PinResetResult
}

data class RegistrationForm(
    /** "7XXXXXXX", "6XXXXXXX" or "+591…" */
    val phone: String,
    val fullName: String,
    val email: String,
    val termsVersion: String,
    val pin: String,
    val pinConfirmation: String,
)

sealed interface CodeRequestResult {
    data object Sent : CodeRequestResult
    data class TooSoon(val waitMillis: Long) : CodeRequestResult
    data object InvalidPhone : CodeRequestResult
}

sealed interface CodeConfirmResult {
    data object Confirmed : CodeConfirmResult
    data class Wrong(val triesLeft: Int) : CodeConfirmResult
    /** Older than 5 minutes or tries exhausted: request a new code. */
    data object Expired : CodeConfirmResult
    data object NoCode : CodeConfirmResult
}

enum class RegistrationField { NAME, EMAIL, TERMS, PIN, PIN_MISMATCH }

sealed interface RegistrationResult {
    data class Created(val userId: UserId) : RegistrationResult
    data object PhoneNotConfirmed : RegistrationResult
    data object PhoneAlreadyRegistered : RegistrationResult
    data class Invalid(val fields: Set<RegistrationField>) : RegistrationResult
}

enum class PinResetResult { Success, PhoneNotConfirmed, UnknownPhone, InvalidPin }
