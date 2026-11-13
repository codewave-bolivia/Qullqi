package com.codewave.qullqi.feature.auth.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QBottomSheet
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QTextButton

/** Offered once after the first PIN login on a device with biometrics (FR-018). Dismissing = "Ahora no". */
@Composable
fun EnableBiometricsSheet(state: LoginState, onEvent: (LoginEvent) -> Unit) {
    val c = Q.colors
    QBottomSheet(visible = state.showEnableBiometrics, onDismiss = { onEvent(LoginEvent.EnableBiometrics(false)) }) {
        Spacer(Modifier.height(QSpacing.x8))
        QIcon(Ph.Fingerprint, null, size = QSize.iconResult, tint = c.ac)
        Spacer(Modifier.height(QSpacing.x12))
        Text("Ingresa con tu huella", style = Q.type.titleSheet, color = c.tx, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(QSpacing.x6))
        Text("Activa la huella para entrar más rápido. Tu PIN sigue funcionando.", style = Q.type.bodySm, color = c.tx2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(QSpacing.x20))
        QPrimaryButton("Activar huella", { onEvent(LoginEvent.EnableBiometrics(true)) })
        Spacer(Modifier.height(QSpacing.x6))
        QTextButton("Ahora no", { onEvent(LoginEvent.EnableBiometrics(false)) }, muted = true, modifier = Modifier.fillMaxWidth())
    }
}
