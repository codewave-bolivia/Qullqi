package com.codewave.qullqi.data.remote

import com.codewave.qullqi.data.SqlRegistrationRepository
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.CodeConfirmResult
import com.codewave.qullqi.domain.repository.CodePurpose
import com.codewave.qullqi.domain.repository.CodeRequestResult
import com.codewave.qullqi.domain.repository.PinResetResult
import com.codewave.qullqi.domain.repository.RegistrationField
import com.codewave.qullqi.domain.repository.RegistrationForm
import com.codewave.qullqi.domain.repository.RegistrationRepository
import com.codewave.qullqi.domain.repository.RegistrationResult

/**
 * SMS codes, account creation and PIN reset against the backend (spec 004, contracts/api-mapping.md).
 * The phone proof returned by verify-otp lives only in memory and is used once (research R8).
 */
class RemoteRegistrationRepository(
    private val api: QullqiApi,
    private val session: RemoteSessionRepository,
    private val tokens: TokenStore,
    private val deviceId: () -> String,
) : RegistrationRepository {

    private val proofs = mutableMapOf<Pair<String, CodePurpose>, String>()

    override suspend fun requestCode(phone: String, purpose: CodePurpose): CodeRequestResult {
        val normalized = SqlRegistrationRepository.normalizePhone(phone) ?: return CodeRequestResult.InvalidPhone
        return when (val r = api.requestOtp(RequestOtpBody(normalized, purpose.wire))) {
            is ApiResult.Ok -> CodeRequestResult.Sent
            is ApiResult.Fail -> when (r.status) {
                429 -> CodeRequestResult.TooSoon((r.details.int("retry_after_seconds") ?: 30) * 1000L)
                422 -> CodeRequestResult.InvalidPhone
                else -> CodeRequestResult.Unavailable
            }
            ApiResult.Unavailable -> CodeRequestResult.Unavailable
        }
    }

    override suspend fun confirmCode(phone: String, code: String, purpose: CodePurpose): CodeConfirmResult {
        val normalized = SqlRegistrationRepository.normalizePhone(phone) ?: return CodeConfirmResult.NoCode
        return when (val r = api.verifyOtp(VerifyOtpBody(normalized, code, purpose.wire))) {
            is ApiResult.Ok -> {
                proofs[normalized to purpose] = r.value.phoneVerifiedToken
                CodeConfirmResult.Confirmed
            }
            is ApiResult.Fail -> when (r.status) {
                400 -> CodeConfirmResult.Wrong(r.details.int("tries_left") ?: 0)
                410 -> CodeConfirmResult.Expired
                404, 422 -> CodeConfirmResult.NoCode
                else -> CodeConfirmResult.Unavailable
            }
            ApiResult.Unavailable -> CodeConfirmResult.Unavailable
        }
    }

    override suspend fun register(form: RegistrationForm): RegistrationResult {
        val phone = SqlRegistrationRepository.normalizePhone(form.phone) ?: return RegistrationResult.PhoneNotConfirmed
        val invalid = localErrors(form)
        if (invalid.isNotEmpty()) return RegistrationResult.Invalid(invalid)
        val proof = proofs[phone to CodePurpose.REGISTER] ?: return RegistrationResult.PhoneNotConfirmed
        val body = RegisterBody(
            firstName = form.firstName.trim(),
            lastNamePaternal = form.lastNamePaternal.trim(),
            lastNameMaternal = form.lastNameMaternal?.trim()?.ifEmpty { null },
            email = form.email.trim().lowercase(),
            pin = form.pin,
            deviceId = deviceId(),
            termsVersion = form.termsVersion,
        )
        return when (val r = api.register(proof, body)) {
            is ApiResult.Ok -> {
                proofs.remove(phone to CodePurpose.REGISTER)
                session.establish(r.value)?.let { RegistrationResult.Created(it) } ?: RegistrationResult.Unavailable
            }
            is ApiResult.Fail -> when (r.status) {
                401 -> {
                    proofs.remove(phone to CodePurpose.REGISTER)
                    RegistrationResult.PhoneNotConfirmed
                }
                409 -> RegistrationResult.PhoneAlreadyRegistered
                422 -> RegistrationResult.Invalid(fieldsOf(r).ifEmpty { return RegistrationResult.Unavailable })
                else -> RegistrationResult.Unavailable
            }
            ApiResult.Unavailable -> RegistrationResult.Unavailable
        }
    }

    override suspend fun resetPin(phone: String, newPin: String): PinResetResult {
        val normalized = SqlRegistrationRepository.normalizePhone(phone) ?: return PinResetResult.UnknownPhone
        if (!PIN.matches(newPin)) return PinResetResult.InvalidPin
        val proof = proofs[normalized to CodePurpose.PIN_RESET] ?: return PinResetResult.PhoneNotConfirmed
        return when (val r = api.pinReset(proof, PinResetBody(newPin, deviceId()))) {
            is ApiResult.Ok -> {
                proofs.remove(normalized to CodePurpose.PIN_RESET)
                // ponytail: the returned session is discarded; the user logs in with the new PIN (spec 003 flow).
                session.cachedId(normalized)?.let {
                    session.setAttempts(it, 3)
                    tokens.clear(UserId(it))
                }
                PinResetResult.Success
            }
            is ApiResult.Fail -> when (r.status) {
                401 -> {
                    proofs.remove(normalized to CodePurpose.PIN_RESET)
                    PinResetResult.PhoneNotConfirmed
                }
                422 -> PinResetResult.InvalidPin
                else -> PinResetResult.Unavailable
            }
            ApiResult.Unavailable -> PinResetResult.Unavailable
        }
    }

    private fun localErrors(form: RegistrationForm): Set<RegistrationField> = buildSet {
        addAll(SqlRegistrationRepository.nameErrors(form))
        if (!EMAIL.matches(form.email.trim().lowercase())) add(RegistrationField.EMAIL)
        if (form.termsVersion.isBlank()) add(RegistrationField.TERMS)
        if (!PIN.matches(form.pin)) add(RegistrationField.PIN)
        else if (form.pin != form.pinConfirmation) add(RegistrationField.PIN_MISMATCH)
    }

    private fun fieldsOf(r: ApiResult.Fail): Set<RegistrationField> {
        val fields = (r.details?.get("fields") as? kotlinx.serialization.json.JsonObject)?.keys.orEmpty()
        return fields.mapNotNull { FIELD_MAP[it] }.toSet()
    }

    private val CodePurpose.wire get() = if (this == CodePurpose.PIN_RESET) "PIN_RESET" else "PHONE_VERIFICATION"

    private companion object {
        val PIN = Regex("^\\d{4}$")
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        val FIELD_MAP = mapOf(
            "firstName" to RegistrationField.FIRST_NAME,
            "lastNamePaternal" to RegistrationField.LAST_NAME_PATERNAL,
            "lastNameMaternal" to RegistrationField.LAST_NAME_MATERNAL,
            "email" to RegistrationField.EMAIL,
            "pin" to RegistrationField.PIN,
        )
    }
}
