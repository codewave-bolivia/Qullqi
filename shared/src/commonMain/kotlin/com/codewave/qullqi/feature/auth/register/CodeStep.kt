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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.QCodeBoxes
import com.codewave.qullqi.designsystem.components.QKeypad
import com.codewave.qullqi.designsystem.components.QTextButton
import com.codewave.qullqi.designsystem.components.digitKeyboardInput
import com.codewave.qullqi.domain.system.SampleOtpCodeGenerator

/** Step 2/4: six-digit SMS code with a 30-second resend countdown (FR-010). */
@Suppress("DEPRECATION") // LocalClipboardManager: simplest text paste on every target.
@Composable
fun CodeStep(state: RegState, onEvent: (RegEvent) -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    StepScaffold(
        2,
        "Escribe el código",
        "Te enviamos un código por SMS al +591 ${state.phone}",
        onBack,
        Modifier
            .onPreviewKeyEvent { e ->
                // Ctrl/Cmd+V pastes the whole code (desktop/tablet keyboards).
                if (e.type == KeyEventType.KeyDown && e.key == Key.V && (e.isCtrlPressed || e.isMetaPressed)) {
                    clipboard.getText()?.text?.let { onEvent(RegEvent.PasteCode(it)) }
                    true
                } else false
            }
            .digitKeyboardInput(focus, { onEvent(RegEvent.CodeDigit(it)) }, { onEvent(RegEvent.CodeDelete) }),
    ) {
        QCodeBoxes(state.code, error = state.codeMessage != null && state.code.isEmpty(), shakeTrigger = state.codeShake)
        Spacer(Modifier.height(QSpacing.x12))
        Text(
            state.codeMessage ?: "Demo: usa el código ${SampleOtpCodeGenerator.CODE}",
            style = Q.type.caption,
            color = if (state.codeMessage != null) Q.colors.neg else Q.colors.tx2,
        )
        Spacer(Modifier.height(QSpacing.x4))
        QTextButton(
            if (state.resendSeconds > 0) "Reenviar en 0:${state.resendSeconds.toString().padStart(2, '0')}" else "Reenviar código",
            { onEvent(RegEvent.Resend) },
            enabled = state.resendSeconds == 0,
            muted = state.resendSeconds > 0,
        )
        Spacer(Modifier.weight(1f))
        QKeypad(
            onDigit = { onEvent(RegEvent.CodeDigit(it)) },
            onDelete = { onEvent(RegEvent.CodeDelete) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
