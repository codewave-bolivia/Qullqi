package com.codewave.qullqi.data

import com.codewave.qullqi.db.QullqiDatabase
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.CodeConfirmResult
import com.codewave.qullqi.domain.repository.CodePurpose
import com.codewave.qullqi.domain.repository.CodeRequestResult
import com.codewave.qullqi.domain.repository.PinResetResult
import com.codewave.qullqi.domain.repository.RegistrationField
import com.codewave.qullqi.domain.repository.RegistrationForm
import com.codewave.qullqi.domain.repository.RegistrationRepository
import com.codewave.qullqi.domain.repository.RegistrationResult
import com.codewave.qullqi.domain.security.PinHasher
import com.codewave.qullqi.domain.system.Clock
import com.codewave.qullqi.domain.system.IdGenerator
import com.codewave.qullqi.domain.system.OtpCodeGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// ponytail: in-memory implementation kept for ViewModel tests; delete when they move to MockEngine (spec 004).
class SqlRegistrationRepository(
    private val db: QullqiDatabase,
    private val hasher: PinHasher,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val otp: OtpCodeGenerator,
    private val io: CoroutineDispatcher,
) : RegistrationRepository {

    private val codes = db.phoneConfirmationQueries
    private val users = db.userQueries

    override suspend fun requestCode(phone: String, purpose: CodePurpose): CodeRequestResult = withContext(io) {
        val normalized = normalizePhone(phone) ?: return@withContext CodeRequestResult.InvalidPhone
        val now = clock.nowMillis()
        db.transactionWithResult<CodeRequestResult> {
            val latest = codes.latestForPhone(normalized).executeAsOneOrNull()
            if (latest != null && now - latest.issued_at < RESEND_WINDOW_MS) {
                return@transactionWithResult CodeRequestResult.TooSoon(RESEND_WINDOW_MS - (now - latest.issued_at))
            }
            codes.insertCode(ids.newId(), normalized, otp.newCode(), now, now + CODE_VALIDITY_MS)
            CodeRequestResult.Sent
        }
    }

    override suspend fun confirmCode(phone: String, code: String, purpose: CodePurpose): CodeConfirmResult = withContext(io) {
        val normalized = normalizePhone(phone) ?: return@withContext CodeConfirmResult.NoCode
        val now = clock.nowMillis()
        db.transactionWithResult<CodeConfirmResult> {
            val latest = codes.latestForPhone(normalized).executeAsOneOrNull()
                ?: return@transactionWithResult CodeConfirmResult.NoCode
            when {
                latest.confirmed_at != null -> CodeConfirmResult.Confirmed
                now > latest.expires_at || latest.tries_left == 0L -> CodeConfirmResult.Expired
                latest.code != code -> {
                    codes.decrementTries(latest.id)
                    val left = (latest.tries_left - 1).toInt()
                    if (left == 0) CodeConfirmResult.Expired else CodeConfirmResult.Wrong(left)
                }
                else -> {
                    codes.markConfirmed(now, latest.id)
                    CodeConfirmResult.Confirmed
                }
            }
        }
    }

    override suspend fun register(form: RegistrationForm): RegistrationResult = withContext(io) {
        val phone = normalizePhone(form.phone)
        val email = form.email.trim().lowercase()
        val invalid = buildSet {
            addAll(nameErrors(form))
            if (!EMAIL.matches(email)) add(RegistrationField.EMAIL)
            if (form.termsVersion.isBlank()) add(RegistrationField.TERMS)
            if (!PIN.matches(form.pin)) add(RegistrationField.PIN)
            else if (form.pin != form.pinConfirmation) add(RegistrationField.PIN_MISMATCH)
        }
        val now = clock.nowMillis()
        db.transactionWithResult<RegistrationResult> {
            if (phone != null && users.userIdByPhone(phone).executeAsOneOrNull() != null) {
                return@transactionWithResult RegistrationResult.PhoneAlreadyRegistered
            }
            if (invalid.isNotEmpty()) return@transactionWithResult RegistrationResult.Invalid(invalid)
            val confirmation = phone?.let { confirmedCode(it, now) }
                ?: return@transactionWithResult RegistrationResult.PhoneNotConfirmed

            val id = ids.newId()
            users.insertUser(id, form.displayName.trim(), null, null, null, phone, email, now)
            val pin = hasher.hash(form.pin)
            users.insertCredential(id, pin.hashB64, pin.saltB64, pin.iterations.toLong(), now)
            users.insertTerms(id, form.termsVersion.trim(), now)
            codes.linkUser(id, confirmation)
            users.openSession(id, now)
            RegistrationResult.Created(UserId(id))
        }
    }

    override suspend fun resetPin(phone: String, newPin: String): PinResetResult = withContext(io) {
        val normalized = normalizePhone(phone) ?: return@withContext PinResetResult.UnknownPhone
        val now = clock.nowMillis()
        db.transactionWithResult<PinResetResult> {
            val userId = users.userIdByPhone(normalized).executeAsOneOrNull()
                ?: return@transactionWithResult PinResetResult.UnknownPhone
            if (!PIN.matches(newPin)) return@transactionWithResult PinResetResult.InvalidPin
            confirmedCode(normalized, now) ?: return@transactionWithResult PinResetResult.PhoneNotConfirmed
            val pin = hasher.hash(newPin)
            users.updateCredential(pin.hashB64, pin.saltB64, pin.iterations.toLong(), now, userId)
            users.resetAttempts(userId)
            PinResetResult.Success
        }
    }

    /** Id of the latest code for [phone] if it was confirmed recently enough to act on. */
    private fun confirmedCode(phone: String, now: Long): String? {
        val latest = codes.latestForPhone(phone).executeAsOneOrNull() ?: return null
        val confirmedAt = latest.confirmed_at ?: return null
        return latest.id.takeIf { now - confirmedAt <= CONFIRMATION_USABLE_MS }
    }

    companion object {
        /** Display name length (spec 004, FR-009; backend `displayName` 1–100). */
        const val NAME_MAX = 100

        /** Name rule shared with the registration screen and the remote repository. */
        fun nameErrors(form: RegistrationForm): Set<RegistrationField> =
            if (form.displayName.trim().length in 1..NAME_MAX) emptySet() else setOf(RegistrationField.NAME)

        const val RESEND_WINDOW_MS = 30_000L
        const val CODE_VALIDITY_MS = 5 * 60_000L
        const val CONFIRMATION_USABLE_MS = 15 * 60_000L
        private val PIN = Regex("^\\d{4}$")
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val LOCAL_MOBILE = Regex("^[67]\\d{7}$")

        /** "71234567" or "+59171234567" → "+59171234567"; anything else → null (FR-002). */
        fun normalizePhone(raw: String): String? {
            val digits = raw.trim().removePrefix("+591")
            return if (LOCAL_MOBILE.matches(digits)) "+591$digits" else null
        }
    }
}
