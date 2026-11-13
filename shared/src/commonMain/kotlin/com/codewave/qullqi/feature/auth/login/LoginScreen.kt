package com.codewave.qullqi.feature.auth.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QKey
import com.codewave.qullqi.designsystem.components.QKeypad
import com.codewave.qullqi.designsystem.components.QPinBoxes
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QTextButton
import com.codewave.qullqi.designsystem.components.digitKeyboardInput

/**
 * PIN login (US2) and, with [unlock] = true, the MainActivity unlock screen (FR-002b).
 * Stateless: renders [state] and sends [onEvent].
 */
@Composable
fun LoginScreen(state: LoginState, onEvent: (LoginEvent) -> Unit, unlock: Boolean = false) {
    val c = Q.colors
    val focus = remember { FocusRequester() }
    val locked = state.status is LoginStatus.Locked
    LaunchedEffect(locked) { if (!locked) runCatching { focus.requestFocus() } }

    QScreen(
        Modifier.digitKeyboardInput(focus, onDigit = { onEvent(LoginEvent.Digit(it)) }, onDelete = { onEvent(LoginEvent.Delete) }),
    ) {
        Spacer(Modifier.height(QSpacing.x28))
        Column0 {
            Avatar(state.account?.initials.orEmpty())
            Spacer(Modifier.height(QSpacing.x16))
            Text(
                if (unlock) "Desbloquea, ${state.firstName}" else "Hola de nuevo, ${state.firstName}",
                style = Q.type.titleLg, color = c.tx, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QSpacing.x6))
            Text(
                if (locked) "Tu acceso está bloqueado" else "Ingresa tu PIN de 4 dígitos",
                style = Q.type.bodySm, color = c.tx2, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QSpacing.x24))
            if (locked) {
                QIcon(Ph.LockSimple, null, size = QSize.iconResult, tint = c.neg)
            } else {
                QPinBoxes(filled = state.filled, error = state.status is LoginStatus.Wrong && state.filled == 0, shakeTrigger = state.shakeCount)
            }
            Spacer(Modifier.height(QSpacing.x14))
            Text(
                state.message.orEmpty(),
                style = Q.type.caption,
                color = when (state.status) {
                    LoginStatus.Entering -> c.ac
                    is LoginStatus.Wrong, LoginStatus.Locked -> c.neg
                    else -> c.tx2
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.height(QSpacing.x20),
            )
        }
        Spacer(Modifier.weight(1f))
        if (locked) {
            QPrimaryButton("Recuperar PIN", { onEvent(LoginEvent.Recover) })
            Spacer(Modifier.height(QSpacing.x12))
        } else {
            QKeypad(
                onDigit = { onEvent(LoginEvent.Digit(it)) },
                onDelete = { onEvent(LoginEvent.Delete) },
                enabled = state.status !is LoginStatus.Checking && state.status !is LoginStatus.Entering,
                leftKey = if (state.biometricKeyVisible) {
                    { QKey(onClick = { onEvent(LoginEvent.Fingerprint) }) { QIcon(Ph.Fingerprint, "Ingresa con tu huella", tint = c.ac) } }
                } else null,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(QSpacing.x12))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (!locked) QTextButton("¿Olvidaste tu PIN?", { onEvent(LoginEvent.Recover) }, muted = true) else Spacer(Modifier)
            if (!unlock) {
                QTextButton("Cambiar de cuenta", { onEvent(LoginEvent.SwitchAccount) }, muted = true, icon = {
                    QIcon(Ph.UserSwitch, null, size = QSize.iconInline, tint = c.tx2)
                })
            }
        }
    }
}

@Composable
private fun Column0(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Composable
internal fun Avatar(initials: String) {
    val c = Q.colors
    Box(
        Modifier.size(QSize.avatar).background(c.acT, CircleShape).border(QSize.border, c.ac, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = Q.type.bodyMd, color = c.acTx)
    }
}
