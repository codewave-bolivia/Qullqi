package com.codewave.qullqi.feature.auth.recover

import androidx.lifecycle.viewModelScope
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.CodeConfirmResult
import com.codewave.qullqi.domain.repository.CodeRequestResult
import com.codewave.qullqi.domain.repository.PinResetResult
import com.codewave.qullqi.domain.repository.RegistrationRepository
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.feature.FlowViewModel
import com.codewave.qullqi.feature.auth.login.LoginViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class RecoverStep { Code, NewPin, ConfirmPin }

data class RecoverState(
    val maskedPhone: String = "",
    val step: RecoverStep = RecoverStep.Code,
    val code: String = "",
    val pinFilled: Int = 0,
    val message: String? = null,
    val shake: Int = 0,
    val resendSeconds: Int = 0,
    val busy: Boolean = false,
)

sealed interface RecoverEvent {
    data class Digit(val digit: Int) : RecoverEvent
    data object Delete : RecoverEvent
    data object Resend : RecoverEvent
}

sealed interface RecoverEffect {
    data class Toast(val text: String) : RecoverEffect
    data class ToLogin(val userId: UserId) : RecoverEffect
}

/** PIN recovery (US4, FR-015): confirm the account's phone with an SMS code, then set a new PIN twice. */
class RecoverPinViewModel(
    private val userId: UserId,
    private val registration: RegistrationRepository,
    private val session: SessionRepository,
) : FlowViewModel<RecoverState, RecoverEffect>(RecoverState()) {

    private var phone = ""
    private val newPin = StringBuilder()
    private val pin = StringBuilder()
    private var countdown: Job? = null

    init {
        viewModelScope.launch {
            phone = session.storedUsers().first().firstOrNull { it.id == userId }?.phone.orEmpty()
            setState { copy(maskedPhone = LoginViewModel.maskPhone(phone)) }
            requestCode()
        }
    }

    fun onEvent(event: RecoverEvent) {
        if (current.busy) return
        when (event) {
            is RecoverEvent.Digit -> if (current.step == RecoverStep.Code) codeDigit(event.digit) else pinDigit(event.digit)
            RecoverEvent.Delete -> if (current.step == RecoverStep.Code) {
                setState { copy(code = code.dropLast(1)) }
            } else if (pin.isNotEmpty()) {
                pin.deleteAt(pin.lastIndex)
                setState { copy(pinFilled = pin.length) }
            }
            RecoverEvent.Resend -> if (current.resendSeconds == 0) viewModelScope.launch { requestCode() }
        }
    }

    private suspend fun requestCode() {
        when (val r = registration.requestCode(phone)) {
            CodeRequestResult.Sent -> startCountdown(RESEND_SECONDS)
            is CodeRequestResult.TooSoon -> startCountdown(((r.waitMillis + 999) / 1000).toInt())
            CodeRequestResult.InvalidPhone -> Unit
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

    private fun codeDigit(d: Int) {
        if (current.code.length >= CODE_LENGTH) return
        val code = current.code + d
        setState { copy(code = code, message = null) }
        if (code.length < CODE_LENGTH) return
        setState { copy(busy = true) }
        viewModelScope.launch {
            when (val r = registration.confirmCode(phone, code)) {
                CodeConfirmResult.Confirmed -> setState { copy(busy = false, step = RecoverStep.NewPin, code = "") }
                is CodeConfirmResult.Wrong -> setState {
                    copy(busy = false, code = "", shake = shake + 1, message = "Código incorrecto. Te quedan ${r.triesLeft} intento${if (r.triesLeft == 1) "" else "s"}.")
                }
                CodeConfirmResult.Expired, CodeConfirmResult.NoCode -> setState {
                    copy(busy = false, code = "", shake = shake + 1, message = "El código venció. Pide uno nuevo.")
                }
            }
        }
    }

    private fun pinDigit(d: Int) {
        if (pin.length >= PIN_LENGTH) return
        pin.append(d)
        setState { copy(pinFilled = pin.length, message = null) }
        if (pin.length < PIN_LENGTH) return
        if (current.step == RecoverStep.NewPin) {
            newPin.setLength(0)
            newPin.append(pin)
            pin.setLength(0)
            setState { copy(step = RecoverStep.ConfirmPin, pinFilled = 0) }
            return
        }
        if (pin.toString() != newPin.toString()) {
            pin.setLength(0)
            setState { copy(pinFilled = 0, shake = shake + 1, message = "Los PIN no coinciden. Inténtalo de nuevo.") }
            return
        }
        val chosen = newPin.toString()
        pin.setLength(0)
        newPin.setLength(0)
        setState { copy(busy = true) }
        viewModelScope.launch {
            when (registration.resetPin(phone, chosen)) {
                PinResetResult.Success -> {
                    emit(RecoverEffect.Toast("PIN actualizado"))
                    emit(RecoverEffect.ToLogin(userId))
                }
                PinResetResult.PhoneNotConfirmed -> setState {
                    copy(busy = false, step = RecoverStep.Code, pinFilled = 0, message = "El código venció. Pide uno nuevo.")
                }
                PinResetResult.InvalidPin, PinResetResult.UnknownPhone -> setState {
                    copy(busy = false, step = RecoverStep.NewPin, pinFilled = 0, shake = shake + 1)
                }
            }
        }
    }

    override fun onCleared() {
        pin.setLength(0)
        newPin.setLength(0)
    }

    companion object {
        const val CODE_LENGTH = 6
        const val PIN_LENGTH = 4
        const val RESEND_SECONDS = 30
    }
}
