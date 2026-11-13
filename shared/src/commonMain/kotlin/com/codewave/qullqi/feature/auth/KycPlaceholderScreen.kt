package com.codewave.qullqi.feature.auth

import androidx.compose.foundation.layout.Arrangement
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
import com.codewave.qullqi.designsystem.components.QPrimaryButton

/** M3 entry placeholder (`kycIntro`). M3 replaces it through the `kycEntry` slot of [AuthFlow]. */
@Composable
fun KycPlaceholderScreen(onDone: () -> Unit) {
    QScreen {
        Spacer(Modifier.weight(1f))
        QIcon(Ph.IdentificationCard, null, size = QSize.iconResult, tint = Q.colors.ac)
        Spacer(Modifier.height(QSpacing.x16))
        Text("Verifica tu identidad", style = Q.type.titleFlow, color = Q.colors.tx)
        Spacer(Modifier.height(QSpacing.x8))
        Text("La verificación llega en M3.", style = Q.type.bodyLg, color = Q.colors.tx2)
        Spacer(Modifier.weight(1f))
        QPrimaryButton("Volver", onDone)
    }
}
