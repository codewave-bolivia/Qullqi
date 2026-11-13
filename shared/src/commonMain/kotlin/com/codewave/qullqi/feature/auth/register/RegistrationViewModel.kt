package com.codewave.qullqi.feature.auth.register

import androidx.lifecycle.viewModelScope
import com.codewave.qullqi.data.SqlRegistrationRepository
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.AccountPreferenceRepository
import com.codewave.qullqi.domain.repository.CodeConfirmResult
import com.codewave.qullqi.domain.repository.CodeRequestResult
import com.codewave.qullqi.domain.repository.RegistrationForm
import com.codewave.qullqi.domain.repository.RegistrationRepository
import com.codewave.qullqi.domain.repository.RegistrationResult
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.session.AppLock
import com.codewave.qullqi.feature.FlowViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Terms version accepted at registration (static placeholder text in M2). */
const val TERMS_VERSION = "2026-01"

data class RegState(
    // 1/4 phone
    val phone: String = "",
    val phoneValid: Boolean = false,
    val phoneHelp: String? = null,
    val sending: Boolean = false,
    // 2/4 code
    val code: String = "",
    val codeMessage: String? = null,
    val codeShake: Int = 0,
    val resendSeconds: Int = 0,
    // 3/4 info
    val fullName: String = "",
    val email: String = "",
    val emailValid: Boolean? = null,
    val emailHelp: String? = null,
    val termsAccepted: Boolean = false,
    // 4/4 PIN
    val pinStep: Int = 0,
    val pinFilled: Int = 0,
    val pinShake: Int = 0,
    val pinMessage: String? = null,
    val creating: Boolean = false,
    // done
    val createdName: String? = null,
) {
    val infoValid: Boolean get() = fullName.isNotBlank() && emailValid == true && termsAccepted
}

sealed interface RegEvent {
    data class Phone(val value: String) : RegEvent
    data object SendCode : RegEvent
    data class CodeDigit(val digit: Int) : RegEvent
    data object CodeDelete : RegEvent
    data class PasteCode(val value: String) : RegEvent
    data object Resend : RegEvent
    data class Name(val value: String) : RegEvent
    data class Email(val value: String) : RegEvent
    data class Terms(val accepted: Boolean) : RegEvent
    data object Continue : RegEvent
    data class PinDigit(val digit: Int) : RegEvent
    data object PinDelete : RegEvent
}

sealed interface RegEffect {
    data object ToCode : RegEffect
    data object ToInfo : RegEffect
    data object ToPin : RegEffect
    data object ToDone : RegEffect
    data class ToLogin(val userId: UserId) : RegEffect
    data class Toast(val text: String) : RegEffect
}

/**
 * One ViewModel for the 4 registration steps (US3). It holds the draft, so going back keeps the
 * values (FR-014); a new registration gets a new instance.
 */
class RegistrationViewModel(
    private val registration: RegistrationRepository,
    private val session: SessionRepository,
    private val preferences: AccountPreferenceRepository,
    private val appLock: AppLock,
) : FlowViewModel<RegState, RegEffect>(RegState()) {

    private val firstPin = StringBuilder()
    private val pin = StringBuilder()
    private var countdown: Job? = null

    fun onEvent(event: RegEvent) {
        when (event) {
            is RegEvent.Phone -> phone(event.value)
            RegEvent.SendCode -> sendCode()
            is RegEvent.CodeDigit -> if (current.code.length < CODE_LENGTH) setCode(current.code + event.digit)
            RegEvent.CodeDelete -> setState { copy(code = code.dropLast(1)) }
            is RegEvent.PasteCode -> setCode(event.value.filter(Char::isDigit).take(CODE_LENGTH))
            RegEvent.Resend -> resend()
            is RegEvent.Name -> setState { copy(fullName = event.value) }
            is RegEvent.Email -> email(event.value)
            is RegEvent.Terms -> setState { copy(termsAccepted = event.accepted) }
            RegEvent.Continue -> if (current.infoValid) startPin()
            is RegEvent.PinDigit -> pinDigit(event.digit)
            RegEvent.PinDelete -> if (pin.isNotEmpty()) {
                pin.deleteAt(pin.lastIndex)
                setState { copy(pinFilled = pin.length) }
            }
        }
    }

    private fun phone(raw: String) {
        val digits = raw.filter(Char::isDigit).take(PHONE_LENGTH)
        val valid = SqlRegistrationRepository.normalizePhone(digits) != null
        setState {
            copy(phone = digits, phoneValid = valid, phoneHelp = if (digits.length == PHONE_LENGTH && !valid) PHONE_RULE else null)
        }
    }

    private fun sendCode() {
        val s = current
        if (!s.phoneValid || s.sending) return
        setState { copy(sending = true) }
        viewModelScope.launch {
            val full = "+591${s.phone}"
            val existing = session.storedUsers().first().firstOrNull { it.phone == full }
            if (existing != null) {
                setState { copy(sending = false) }
                emit(RegEffect.Toast("Ya tienes una cuenta con este número. Ingresa con tu PIN."))
                emit(RegEffect.ToLogin(existing.id))
                return@launch
            }
            when (val r = registration.requestCode(s.phone)) {
                CodeRequestResult.Sent -> startCountdown(RESEND_SECONDS)
                is CodeRequestResult.TooSoon -> startCountdown(((r.waitMillis + 999) / 1000).toInt())
                CodeRequestResult.InvalidPhone -> {
                    setState { copy(sending = false, phoneValid = false, phoneHelp = PHONE_RULE) }
                    return@launch
                }
            }
            setState { copy(sending = false, code = "", codeMessage = null) }
            emit(RegEffect.ToCode)
        }
    }

    private fun resend() {
        if (current.resendSeconds > 0) return
        viewModelScope.launch {
            if (registration.requestCode(current.phone) == CodeRequestResult.Sent) {
                startCountdown(RESEND_SECONDS)
                setState { copy(code = "", codeMessage = null) }
                emit(RegEffect.Toast("Te enviamos un nuevo código"))
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        setState { copy(resendSeconds = seconds) }
        countdown = viewModelScope.launch {
            while (current.resendSeconds > 0) {
                delay(1_000)
                setState { copy(resendSeconds = resendSeconds - 1) }
            }
        }
    }

    private fun setCode(value: String) {
        setState { copy(code = value, codeMessage = if (value.isEmpty()) codeMessage else null) }
        if (value.length < CODE_LENGTH) return
        viewModelScope.launch {
            when (val r = registration.confirmCode(current.phone, value)) {
                CodeConfirmResult.Confirmed -> emit(RegEffect.ToInfo)
                is CodeConfirmResult.Wrong -> setState {
                    copy(code = "", codeShake = codeShake + 1, codeMessage = "Código incorrecto. Te quedan ${r.triesLeft} intento${if (r.triesLeft == 1) "" else "s"}.")
                }
                CodeConfirmResult.Expired, CodeConfirmResult.NoCode -> setState {
                    copy(code = "", codeShake = codeShake + 1, codeMessage = "El código venció. Pide uno nuevo.")
                }
            }
        }
    }

    private fun email(raw: String) {
        val normalized = raw.trim().lowercase()
        val valid = when {
            normalized.isEmpty() -> null
            else -> EMAIL.matches(normalized)
        }
        setState { copy(email = raw, emailValid = valid, emailHelp = if (valid == false) "Revisa el formato del correo." else null) }
    }

    private fun startPin() {
        firstPin.setLength(0)
        pin.setLength(0)
        setState { copy(pinStep = 0, pinFilled = 0, pinMessage = null) }
        emit(RegEffect.ToPin)
    }

    private fun pinDigit(d: Int) {
        if (current.creating || pin.length >= PIN_LENGTH) return
        pin.append(d)
        setState { copy(pinFilled = pin.length, pinMessage = null) }
        if (pin.length < PIN_LENGTH) return
        if (current.pinStep == 0) {
            firstPin.setLength(0)
            firstPin.append(pin)
            pin.setLength(0)
            setState { copy(pinStep = 1, pinFilled = 0) }
            return
        }
        if (pin.toString() != firstPin.toString()) {
            pin.setLength(0)
            setState { copy(pinFilled = 0, pinShake = pinShake + 1, pinMessage = "Los PIN no coinciden. Inténtalo de nuevo.") }
            return
        }
        create()
    }

    private fun create() {
        val s = current
        val chosen = firstPin.toString()
        pin.setLength(0)
        firstPin.setLength(0)
        setState { copy(creating = true) }
        viewModelScope.launch {
            val form = RegistrationForm(s.phone, s.fullName.trim(), s.email, TERMS_VERSION, chosen, chosen)
            when (val r = registration.register(form)) {
                is RegistrationResult.Created -> {
                    preferences.recordLogin(r.userId)
                    appLock.unlock()
                    setState { copy(creating = false, createdName = fullName.trim().substringBefore(' ')) }
                    emit(RegEffect.ToDone)
                }
                RegistrationResult.PhoneAlreadyRegistered -> {
                    setState { copy(creating = false) }
                    session.storedUsers().first().firstOrNull { it.phone == "+591${s.phone}" }?.let { emit(RegEffect.ToLogin(it.id)) }
                }
                RegistrationResult.PhoneNotConfirmed -> {
                    setState { copy(creating = false, pinStep = 0, pinFilled = 0) }
                    emit(RegEffect.Toast("Tu código venció. Confirma tu número otra vez."))
                    emit(RegEffect.ToCode)
                }
                is RegistrationResult.Invalid -> setState {
                    copy(creating = false, pinStep = 0, pinFilled = 0, pinShake = pinShake + 1, pinMessage = "Revisa tus datos e inténtalo de nuevo.")
                }
            }
        }
    }

    override fun onCleared() {
        pin.setLength(0)
        firstPin.setLength(0)
    }

    companion object {
        const val PHONE_LENGTH = 8
        const val CODE_LENGTH = 6
        const val PIN_LENGTH = 4
        const val RESEND_SECONDS = 30
        const val PHONE_RULE = "Debe tener 8 dígitos y empezar con 6 o 7."
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
