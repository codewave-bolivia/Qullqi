package com.codewave.qullqi.feature.auth.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.accentGlow
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QSecondaryButton
import com.codewave.qullqi.designsystem.components.QWordmark
import org.jetbrains.compose.resources.DrawableResource

/**
 * Welcome (US1), a port of the `is.welcome` block of `Qullqi Pay Auth.dc.html`: small wordmark,
 * centered headline + lead + three perks, buttons at the bottom, elliptical accent glow at (90 %, 18 %).
 */
@Composable
fun WelcomeScreen(onEvent: (StartEvent) -> Unit) {
    val c = Q.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(c.page), contentAlignment = Alignment.Center) {
        val wide = maxWidth >= QSize.wideWindow
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = QSize.contentMaxWidth)
                .fillMaxWidth()
                .then(if (wide) Modifier.padding(vertical = QSpacing.x24).clip(RoundedCornerShape(QRadius.lg)) else Modifier)
                .background(c.bg)
                // radial-gradient(80% 40% at 90% 18%, ac 16%, transparent)
                .accentGlow(c.ac, alpha = 0.16f, centerX = 0.90f, centerY = 0.18f, radiusX = 0.80f, radiusY = 0.40f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = QSpacing.x24, end = QSpacing.x24, top = QSpacing.x56, bottom = QSpacing.x28),
        ) {
            QWordmark(style = Q.type.welcomeWordmark)
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Envía, cobra y paga con QR en segundos.", style = Q.type.welcomeTitle, color = c.tx)
                Spacer(Modifier.height(QSpacing.x14))
                Text(
                    "Abre tu cuenta con tu celular y tu correo. Verificas tu identidad cuando quieras.",
                    style = Q.type.lead,
                    color = c.tx2,
                    modifier = Modifier.widthIn(max = QSize.leadMaxWidth),
                )
                Spacer(Modifier.height(QSpacing.x28))
                Column(verticalArrangement = Arrangement.spacedBy(QSpacing.x12)) {
                    Perk(Ph.QrCode, "Paga y cobra con QR")
                    Perk(Ph.ArrowsLeftRight, "Cambia Bs ⇄ USD sin comisión")
                    Perk(Ph.CreditCard, "Tarjeta virtual al instante")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(QSpacing.x8)) {
                QPrimaryButton("Crear cuenta", { onEvent(StartEvent.CreateAccount) })
                QSecondaryButton("Ya tengo cuenta", { onEvent(StartEvent.HaveAccount) })
            }
        }
    }
}

@Composable
private fun Perk(icon: DrawableResource, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QSpacing.x12)) {
        QIcon(icon, null, size = QSize.perkIcon, tint = Q.colors.ac)
        Text(text, style = Q.type.perk, color = Q.colors.tx)
    }
}
