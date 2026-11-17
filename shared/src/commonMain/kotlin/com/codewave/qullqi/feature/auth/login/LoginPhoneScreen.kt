package com.codewave.qullqi.feature.auth.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codewave.qullqi.data.SqlRegistrationRepository
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.QBackButton
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QTextField
import com.codewave.qullqi.feature.auth.register.RegistrationViewModel
import com.codewave.qullqi.feature.FlowViewModel

data class LoginPhoneState(val phone: String = "", val valid: Boolean = false, val help: String? = null)

/** Phone entry for an account not stored on this device (spec 004, FR-014). */
class LoginPhoneViewModel(prefill: String?) : FlowViewModel<LoginPhoneState, Nothing>(LoginPhoneState()) {
    init {
        prefill?.let { onPhone(it.removePrefix("+591")) }
    }

    fun onPhone(raw: String) {
        val digits = raw.filter(Char::isDigit).take(RegistrationViewModel.PHONE_LENGTH)
        val valid = SqlRegistrationRepository.normalizePhone(digits) != null
        val complete = digits.length == RegistrationViewModel.PHONE_LENGTH
        setState { copy(phone = digits, valid = valid, help = if (complete && !valid) RegistrationViewModel.PHONE_RULE else null) }
    }

    /** "+591XXXXXXXX" when valid. */
    fun fullPhone(): String? = current.phone.takeIf { current.valid }?.let { "+591$it" }
}

@Composable
fun LoginPhoneScreen(prefill: String?, onBack: () -> Unit, onContinue: (String) -> Unit) {
    val vm = viewModel(key = "login-phone-$prefill") { LoginPhoneViewModel(prefill) }
    val state by vm.state.collectAsState()
    QScreen {
        QBackButton(onBack)
        Spacer(Modifier.height(QSpacing.x28))
        Text("Ingresa tu número", style = Q.type.titleFlow, color = Q.colors.tx)
        Spacer(Modifier.height(QSpacing.x8))
        Text("Usa el celular con el que creaste tu cuenta.", style = Q.type.bodySm, color = Q.colors.tx2)
        Spacer(Modifier.height(QSpacing.x28))
        QTextField(
            value = state.phone,
            onValueChange = vm::onPhone,
            label = "Número de celular",
            prefix = "BO +591",
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            valid = if (state.phone.length < RegistrationViewModel.PHONE_LENGTH) null else state.valid,
            help = state.help,
        )
        Spacer(Modifier.weight(1f))
        QPrimaryButton("Continuar", { vm.fullPhone()?.let(onContinue) }, enabled = state.valid)
    }
}
