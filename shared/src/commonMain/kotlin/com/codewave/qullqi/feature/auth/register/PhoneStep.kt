package com.codewave.qullqi.feature.auth.register

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QTextField

/** Step 1/4: Bolivian mobile number (FR-009). */
@Composable
fun PhoneStep(state: RegState, onEvent: (RegEvent) -> Unit, onBack: () -> Unit) {
    StepScaffold(1, "¿Cuál es tu número de celular?", "Te enviaremos un código por SMS para confirmarlo.", onBack) {
        QTextField(
            value = state.phone,
            onValueChange = { onEvent(RegEvent.Phone(it)) },
            label = "Número de celular",
            prefix = "BO +591",
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Send,
            valid = when {
                state.phone.length < RegistrationViewModel.PHONE_LENGTH -> null
                else -> state.phoneValid
            },
            help = state.phoneHelp,
        )
        Spacer(Modifier.weight(1f))
        QPrimaryButton("Enviar código", { onEvent(RegEvent.SendCode) }, enabled = state.phoneValid && !state.sending)
    }
}
