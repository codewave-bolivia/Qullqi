package com.codewave.qullqi.feature.main

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QWordmark

/** Shown on platforms whose local data is not provided yet (iOS/Desktop until the Builder adds it). */
@Composable
fun UnavailableScreen() {
    QScreen {
        Spacer(Modifier.weight(1f))
        QWordmark()
        Spacer(Modifier.height(QSpacing.x24))
        QIcon(Ph.DeviceMobile, null, size = QSize.iconResult, tint = Q.colors.tx2)
        Spacer(Modifier.height(QSpacing.x12))
        Text("Qullqi aún no está disponible en esta plataforma", style = Q.type.titleSheet, color = Q.colors.tx)
        Spacer(Modifier.height(QSpacing.x8))
        Text("Por ahora puedes usarla en Android.", style = Q.type.bodySm, color = Q.colors.tx2)
        Spacer(Modifier.weight(1f))
    }
}
