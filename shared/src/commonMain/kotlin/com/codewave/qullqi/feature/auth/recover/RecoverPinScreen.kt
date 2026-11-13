package com.codewave.qullqi.feature.auth.recover

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.QBackButton
import com.codewave.qullqi.designsystem.components.QCodeBoxes
import com.codewave.qullqi.designsystem.components.QKeypad
import com.codewave.qullqi.designsystem.components.QPinBoxes
import com.codewave.qullqi.designsystem.components.QTextButton
import com.codewave.qullqi.designsystem.components.digitKeyboardInput
import com.codewave.qullqi.domain.system.SampleOtpCodeGenerator

/** PIN recovery screen (US4): SMS code to the account's masked phone, then new PIN twice. */
@Composable
fun RecoverPinScreen(state: RecoverState, onEvent: (RecoverEvent) -> Unit, onBack: () -> Unit) {
    val c = Q.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.step) { runCatching { focus.requestFocus() } }
    QScreen(Modifier.digitKeyboardInput(focus, { onEvent(RecoverEvent.Digit(it)) }, { onEvent(RecoverEvent.Delete) })) {
        QBackButton(onBack)
        Spacer(Modifier.height(QSpacing.x20))
        val (title, help) = when (state.step) {
            RecoverStep.Code -> "Te enviamos un código por SMS" to "Escríbelo para confirmar que eres tú. Número ${state.maskedPhone}"
            RecoverStep.NewPin -> "Crea un PIN de 4 dígitos" to "Lo usarás para entrar a la app y confirmar operaciones."
            RecoverStep.ConfirmPin -> "Confirma tu PIN" to "Escríbelo otra vez."
        }
        Text(title, style = Q.type.titleFlow, color = c.tx)
        Spacer(Modifier.height(QSpacing.x8))
        Text(help, style = Q.type.bodySm, color = c.tx2)
        Spacer(Modifier.height(QSpacing.x28))
        if (state.step == RecoverStep.Code) {
            QCodeBoxes(state.code, error = state.message != null && state.code.isEmpty(), shakeTrigger = state.shake)
            Spacer(Modifier.height(QSpacing.x12))
            Text(state.message ?: "Demo: usa el código ${SampleOtpCodeGenerator.CODE}", style = Q.type.caption, color = if (state.message != null) c.neg else c.tx2)
            QTextButton(
                if (state.resendSeconds > 0) "Reenviar en 0:${state.resendSeconds.toString().padStart(2, '0')}" else "Reenviar código",
                { onEvent(RecoverEvent.Resend) },
                enabled = state.resendSeconds == 0,
                muted = state.resendSeconds > 0,
            )
        } else {
            QPinBoxes(state.pinFilled, error = state.message != null && state.pinFilled == 0, shakeTrigger = state.shake, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(QSpacing.x12))
            Text(state.message.orEmpty(), style = Q.type.caption, color = c.neg, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.weight(1f))
        QKeypad(
            onDigit = { onEvent(RecoverEvent.Digit(it)) },
            onDelete = { onEvent(RecoverEvent.Delete) },
            enabled = !state.busy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
