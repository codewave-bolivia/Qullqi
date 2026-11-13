package com.codewave.qullqi.feature.auth.start

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QSecondaryButton
import com.codewave.qullqi.designsystem.components.QWordmark

/** Welcome (US1): value proposition with "Crear cuenta" and "Ya tengo cuenta". */
@Composable
fun WelcomeScreen(onEvent: (StartEvent) -> Unit) {
    QScreen {
        Spacer(Modifier.height(QSpacing.x36))
        QWordmark()
        Spacer(Modifier.weight(1f))
        Text("Tu billetera en Bolivianos y dólares", style = Q.type.titleFlow, color = Q.colors.tx)
        Spacer(Modifier.height(QSpacing.x10))
        Text("Envía, cobra y paga con QR en segundos.", style = Q.type.bodyLg, color = Q.colors.tx2)
        Spacer(Modifier.height(QSpacing.x32))
        QPrimaryButton("Crear cuenta", { onEvent(StartEvent.CreateAccount) })
        Spacer(Modifier.height(QSpacing.x10))
        QSecondaryButton("Ya tengo cuenta", { onEvent(StartEvent.HaveAccount) })
        Spacer(Modifier.height(QSpacing.x8))
    }
}
