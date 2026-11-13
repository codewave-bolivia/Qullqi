package com.codewave.qullqi.feature.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QBottomSheet
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QTextButton
import com.codewave.qullqi.designsystem.components.QTextField

/** Step 3/4: name, email and terms with live validation (FR-011). */
@Composable
fun InfoStep(state: RegState, onEvent: (RegEvent) -> Unit, onBack: () -> Unit) {
    val c = Q.colors
    var termsSheet by remember { mutableStateOf(false) }
    Box {
        StepScaffold(3, "Cuéntanos sobre ti", "Usaremos tu correo para comprobantes y avisos de seguridad.", onBack) {
            QTextField(
                value = state.fullName,
                onValueChange = { onEvent(RegEvent.Name(it)) },
                label = "Nombre y apellido",
                valid = if (state.fullName.isEmpty()) null else state.fullName.isNotBlank(),
            )
            Spacer(Modifier.height(QSpacing.x16))
            QTextField(
                value = state.email,
                onValueChange = { onEvent(RegEvent.Email(it)) },
                label = "Correo electrónico",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                valid = state.emailValid,
                help = state.emailHelp,
            )
            Spacer(Modifier.height(QSpacing.x20))
            Row(
                Modifier.fillMaxWidth().heightIn(min = QSize.touchTarget)
                    .toggleable(state.termsAccepted, role = Role.Checkbox) { onEvent(RegEvent.Terms(it)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QSpacing.x12),
            ) {
                val shape = RoundedCornerShape(QRadius.xs)
                Box(
                    Modifier.size(QSize.checkbox).background(if (state.termsAccepted) c.acT else c.s1, shape)
                        .border(QSize.border, if (state.termsAccepted) c.ac else c.ln, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.termsAccepted) QIcon(Ph.Check, null, size = QSize.iconInline, tint = c.ac)
                }
                Text(
                    buildAnnotatedString {
                        append("Acepto los ")
                        withStyle(SpanStyle(color = c.ac)) { append("Términos y condiciones") }
                        append(" y la ")
                        withStyle(SpanStyle(color = c.ac)) { append("Política de privacidad") }
                    },
                    style = Q.type.bodySm,
                    color = c.tx2,
                    modifier = Modifier.weight(1f),
                )
            }
            QTextButton("Leer términos", { termsSheet = true }, muted = true)
            Spacer(Modifier.weight(1f))
            QPrimaryButton("Continuar", { onEvent(RegEvent.Continue) }, enabled = state.infoValid)
        }
        QBottomSheet(visible = termsSheet, onDismiss = { termsSheet = false }) {
            Text("Términos y condiciones", style = Q.type.titleSheet, color = c.tx, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(QSpacing.x10))
            Text(
                "Documento de demostración. El texto legal definitivo se publicará antes del lanzamiento.",
                style = Q.type.bodySm,
                color = c.tx2,
            )
            Spacer(Modifier.height(QSpacing.x20))
            QPrimaryButton("Entendido", { termsSheet = false })
        }
    }
}

