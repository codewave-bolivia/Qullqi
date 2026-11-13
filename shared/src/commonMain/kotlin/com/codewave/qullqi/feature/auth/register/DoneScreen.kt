package com.codewave.qullqi.feature.auth.register

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QTextButton
import org.jetbrains.compose.resources.DrawableResource

/** "¡Listo!" (FR-013): account created as "Sin verificar", with what is available before KYC. */
@Composable
fun DoneScreen(firstName: String?, onVerify: () -> Unit, onLater: () -> Unit) {
    val c = Q.colors
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(QMotion.POP_MS, easing = QMotion.popEasing)) }
    QScreen {
        Spacer(Modifier.height(QSpacing.x36))
        Box(
            Modifier.scale(pop.value).size(QSize.avatar).background(c.acT, CircleShape).border(QSize.border, c.ac, CircleShape),
            contentAlignment = Alignment.Center,
        ) { QIcon(Ph.Check, null, size = QSize.iconBar, tint = c.ac) }
        Spacer(Modifier.height(QSpacing.x20))
        Text(if (firstName != null) "¡Listo, $firstName!" else "¡Listo!", style = Q.type.titleFlow, color = c.tx)
        Spacer(Modifier.height(QSpacing.x8))
        Text("Tu cuenta está creada. Verifica tu identidad para mover dinero.", style = Q.type.bodySm, color = c.tx2)
        Spacer(Modifier.height(QSpacing.x24))

        val shape = RoundedCornerShape(QRadius.lg)
        Column(Modifier.fillMaxWidth().background(c.s1, shape).border(QSize.border, c.ln, shape).padding(QSpacing.x16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ESTADO DE LA CUENTA", style = Q.type.overline, color = c.tx2, modifier = Modifier.weight(1f))
                Text(
                    "Sin verificar",
                    style = Q.type.caption,
                    color = c.warn,
                    modifier = Modifier.border(QSize.border, c.warn, RoundedCornerShape(QRadius.xs)).padding(horizontal = QSpacing.x8, vertical = QSpacing.x2),
                )
            }
            Spacer(Modifier.height(QSpacing.x12))
            Column(verticalArrangement = Arrangement.spacedBy(QSpacing.x10)) {
                Limit(Ph.Compass, "Explorar la app", "Disponible", c.ac)
                Limit(Ph.ArrowDown, "Depositar dinero", "Requiere KYC", c.tx2)
                Limit(Ph.ArrowUp, "Retirar dinero", "Requiere KYC", c.tx2)
                Limit(Ph.PaperPlaneTilt, "Enviar, cobrar y pagar", "Requiere KYC", c.tx2)
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(QSpacing.x24))
        QPrimaryButton("Verificar mi identidad", onVerify)
        Spacer(Modifier.height(QSpacing.x6))
        QTextButton("Hacerlo más tarde", onLater, muted = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Limit(icon: DrawableResource, title: String, status: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QSpacing.x12)) {
        QIcon(icon, null, size = QSize.iconInline, tint = color)
        Text(title, style = Q.type.bodySm, color = Q.colors.tx, modifier = Modifier.weight(1f))
        Text(status, style = Q.type.caption, color = color)
    }
}
