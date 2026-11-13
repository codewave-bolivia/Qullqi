package com.codewave.qullqi.feature.auth.register

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
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.QKeypad
import com.codewave.qullqi.designsystem.components.QPinBoxes
import com.codewave.qullqi.designsystem.components.digitKeyboardInput

/** Step 4/4: create and confirm the PIN (FR-012). */
@Composable
fun PinStep(state: RegState, onEvent: (RegEvent) -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    StepScaffold(
        4,
        if (state.pinStep == 0) "Crea un PIN de 4 dígitos" else "Confirma tu PIN",
        "Lo usarás para entrar a la app y confirmar operaciones.",
        onBack,
        Modifier.digitKeyboardInput(focus, { onEvent(RegEvent.PinDigit(it)) }, { onEvent(RegEvent.PinDelete) }),
    ) {
        QPinBoxes(
            filled = state.pinFilled,
            error = state.pinMessage != null && state.pinFilled == 0,
            shakeTrigger = state.pinShake,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(QSpacing.x12))
        Text(state.pinMessage.orEmpty(), style = Q.type.caption, color = Q.colors.neg, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.weight(1f))
        QKeypad(
            onDigit = { onEvent(RegEvent.PinDigit(it)) },
            onDelete = { onEvent(RegEvent.PinDelete) },
            enabled = !state.creating,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
