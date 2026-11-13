package com.codewave.qullqi.feature.auth.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QKey
import com.codewave.qullqi.designsystem.components.QKeypad
import com.codewave.qullqi.designsystem.components.QLinkButton
import com.codewave.qullqi.designsystem.components.QPinBoxes
import com.codewave.qullqi.designsystem.components.digitKeyboardInput

/**
 * PIN login (US2), a port of the `is.login` block of `Qullqi Pay Auth.dc.html`, and with
 * [unlock] = true the MainActivity unlock screen (FR-002b). Everything in the upper block is
 * left-aligned; the keypad spans the width; the footer holds the two links. Stateless.
 */
@Composable
fun LoginScreen(state: LoginState, onEvent: (LoginEvent) -> Unit, unlock: Boolean = false) {
    val c = Q.colors
    val focus = remember { FocusRequester() }
    val locked = state.status is LoginStatus.Locked
    LaunchedEffect(locked) { if (!locked) runCatching { focus.requestFocus() } }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(c.page)
            .digitKeyboardInput(focus, onDigit = { onEvent(LoginEvent.Digit(it)) }, onDelete = { onEvent(LoginEvent.Delete) }),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= QSize.wideWindow
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = QSize.contentMaxWidth)
                .fillMaxWidth()
                .then(if (wide) Modifier.padding(vertical = QSpacing.x24).clip(RoundedCornerShape(QRadius.lg)) else Modifier)
                .background(c.bg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = QSize.loginPaddingTop),
        ) {
            // Upper block: vertically centered, left-aligned.
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = QSize.loginPaddingH),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Avatar(state.account?.initials.orEmpty())
                Spacer(Modifier.height(QSpacing.x18))
                Text(
                    if (unlock) "Desbloquea, ${state.firstName}" else "Hola de nuevo, ${state.firstName}",
                    style = Q.type.titleLg,
                    color = c.tx,
                )
                Spacer(Modifier.height(QSpacing.x4))
                Text("Ingresa tu PIN de 4 dígitos", style = Q.type.bodySm, color = c.tx2)
                Spacer(Modifier.height(QSpacing.x26))
                QPinBoxes(
                    filled = state.filled,
                    error = (state.status is LoginStatus.Wrong || locked) && state.filled == 0,
                    success = state.status is LoginStatus.Entering,
                    shakeTrigger = state.shakeCount,
                )
                Spacer(Modifier.height(QSpacing.x14))
                Text(
                    state.message.orEmpty(),
                    style = Q.type.message,
                    color = when (state.status) {
                        LoginStatus.Entering -> c.ac
                        is LoginStatus.Wrong, LoginStatus.Locked -> c.neg
                        else -> c.tx2
                    },
                    modifier = Modifier.heightIn(min = QSize.messageMinHeight),
                )
            }
            QKeypad(
                onDigit = { onEvent(LoginEvent.Digit(it)) },
                onDelete = { onEvent(LoginEvent.Delete) },
                enabled = !locked && state.status !is LoginStatus.Checking && state.status !is LoginStatus.Entering,
                leftKey = if (state.biometricKeyVisible) {
                    { QKey(onClick = { onEvent(LoginEvent.Fingerprint) }) { QIcon(Ph.Fingerprint, "Ingresa con tu huella", size = QSize.keyIcon, tint = c.ac) } }
                } else null,
            )
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = QSpacing.x24, end = QSpacing.x24, top = QSpacing.x10, bottom = QSpacing.x30),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QLinkButton(if (locked) "Recuperar PIN" else "¿Olvidaste tu PIN?", { onEvent(LoginEvent.Recover) }, color = c.ac)
                if (!unlock) QLinkButton("Cambiar de cuenta", { onEvent(LoginEvent.SwitchAccount) }, color = c.tx2)
            }
        }
    }
}

/** 56 dp avatar: accent-tint fill, initials 19/500 in accent text, 1 dp accent ring and 26 dp accent glow. */
@Composable
internal fun Avatar(initials: String) {
    val c = Q.colors
    Box(
        Modifier
            .size(QSize.avatar)
            .shadow(QSize.avatarGlow, CircleShape, clip = false, ambientColor = c.ac, spotColor = c.ac)
            .background(c.acT, CircleShape)
            .border(QSize.border, c.ac, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = Q.type.avatar, color = c.acTx)
    }
}
