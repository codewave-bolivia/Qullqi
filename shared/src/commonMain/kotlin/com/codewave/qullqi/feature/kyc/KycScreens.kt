package com.codewave.qullqi.feature.kyc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QSecondaryButton
import com.codewave.qullqi.designsystem.components.QTextButton
import com.codewave.qullqi.designsystem.components.QTextField
import com.codewave.qullqi.domain.model.DocumentType
import org.jetbrains.compose.resources.DrawableResource

// Spec 005 screens. Capture happens in the provider's pages; these screens only collect the
// document data, open the provider and show the backend's result. Tokens only (constitution VI).

/** Title + help + content, same rhythm as the registration steps (UI_GUIDELINES §2.3). */
@Composable
private fun KycScaffold(
    title: String,
    help: String,
    icon: DrawableResource? = null,
    iconTint: androidx.compose.ui.graphics.Color = Q.colors.ac,
    content: @Composable ColumnScope.() -> Unit,
) {
    QScreen {
        Spacer(Modifier.height(QSpacing.x28))
        if (icon != null) {
            QIcon(icon, null, size = QSize.iconResult, tint = iconTint)
            Spacer(Modifier.height(QSpacing.x16))
        }
        Text(title, style = Q.type.titleFlow, color = Q.colors.tx)
        Spacer(Modifier.height(QSpacing.x8))
        Text(help, style = Q.type.bodySm, color = Q.colors.tx2)
        Spacer(Modifier.height(QSpacing.x28))
        content()
    }
}

/** FR-005b: shown on Intro/Document when the camera was denied. */
@Composable
private fun CameraNeeded(state: KycState, onEvent: (KycEvent) -> Unit) {
    if (state.cameraDenied == null) return
    Spacer(Modifier.height(QSpacing.x16))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QSpacing.x10)) {
        QIcon(Ph.Warning, null, size = QSize.iconInline, tint = Q.colors.warn)
        Text("Necesitamos tu cámara para verificarte", style = Q.type.bodySm, color = Q.colors.tx)
    }
    Spacer(Modifier.height(QSpacing.x4))
    Text("Para fotografiar tu documento y tomar tu selfie.", style = Q.type.caption, color = Q.colors.tx2)
    Spacer(Modifier.height(QSpacing.x10))
    QSecondaryButton(
        if (state.cameraDenied == CameraDenied.Settings) "Permitir cámara en Ajustes" else "Permitir cámara",
        { onEvent(KycEvent.AllowCamera) },
    )
}

@Composable
private fun Message(text: String?) {
    if (text == null) return
    Spacer(Modifier.height(QSpacing.x12))
    Text(text, style = Q.type.caption, color = Q.colors.neg)
}

/** FR-001: requirements + privacy notice; FR-002 resume variant. */
@Composable
fun KycIntroScreen(state: KycState, onEvent: (KycEvent) -> Unit) {
    val c = Q.colors
    if (state.screen == KycScreen.Loading) return KycWaitingBody("Cargando…")
    KycScaffold(
        title = if (state.pendingResume) "Tienes una verificación pendiente" else "Verifica tu identidad",
        help = if (state.pendingResume) "Continúa donde la dejaste. No tendrás que escribir tus datos otra vez."
        else "Así protegemos tu cuenta y podrás enviar, recibir y pagar.",
        icon = Ph.IdentificationCard,
    ) {
        listOf(
            "Ten a mano tu CI o pasaporte vigente.",
            "Busca un lugar con buena luz.",
            "Toma unos 3 minutos.",
        ).forEach {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QSpacing.x10)) {
                QIcon(Ph.Check, null, size = QSize.iconInline, tint = c.ac)
                Text(it, style = Q.type.bodySm, color = c.tx)
            }
            Spacer(Modifier.height(QSpacing.x10))
        }
        Spacer(Modifier.height(QSpacing.x12))
        Row(
            Modifier.fillMaxWidth().heightIn(min = QSize.touchTarget)
                .toggleable(state.privacyAccepted, role = Role.Checkbox) { onEvent(KycEvent.AcceptPrivacy(it)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QSpacing.x12),
        ) {
            val shape = RoundedCornerShape(QRadius.xs)
            Box(
                Modifier.size(QSize.checkbox).background(if (state.privacyAccepted) c.acT else c.s1, shape)
                    .border(QSize.border, if (state.privacyAccepted) c.ac else c.ln, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (state.privacyAccepted) QIcon(Ph.Check, null, size = QSize.iconInline, tint = c.ac)
            }
            Text(
                "Acepto que un proveedor de verificación procese mi documento y mi selfie en nombre de Qullqi.",
                style = Q.type.bodySm,
                color = c.tx2,
                modifier = Modifier.weight(1f),
            )
        }
        Message(state.message)
        CameraNeeded(state, onEvent)
        Spacer(Modifier.weight(1f))
        QPrimaryButton(
            if (state.pendingResume) "Continuar verificación" else "Continuar",
            { onEvent(KycEvent.Continue) },
            enabled = state.privacyAccepted && !state.busy,
        )
    }
}

/** FR-003/FR-004: type, number and birth date; no names (they come from the document). */
@Composable
fun KycDocumentScreen(state: KycState, onEvent: (KycEvent) -> Unit) {
    KycScaffold(
        title = "Tu documento",
        help = "Usaremos estos datos para comparar con tu documento.",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QSpacing.x10)) {
            listOf(DocumentType.CI to "CI", DocumentType.PASSPORT to "Pasaporte").forEach { (type, label) ->
                val pick = { onEvent(KycEvent.Type(type)) }
                if (state.documentType == type) QPrimaryButton(label, pick, Modifier.weight(1f))
                else QSecondaryButton(label, pick, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(QSpacing.x16))
        QTextField(
            value = state.documentNumber,
            onValueChange = { onEvent(KycEvent.Number(it)) },
            label = if (state.documentType == DocumentType.CI) "Número de CI" else "Número de pasaporte",
            help = state.numberError,
            valid = if (state.numberError != null) false else null,
        )
        Spacer(Modifier.height(QSpacing.x16))
        QTextField(
            value = formatBirth(state.birthDigits),
            onValueChange = { onEvent(KycEvent.BirthDigits(it)) },
            label = "Fecha de nacimiento (DD/MM/AAAA)",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            help = state.birthError,
            valid = if (state.birthError != null) false else null,
        )
        Message(state.message)
        CameraNeeded(state, onEvent)
        Spacer(Modifier.weight(1f))
        QPrimaryButton(
            if (state.busy) "Preparando…" else "Continuar a la verificación",
            { onEvent(KycEvent.Submit) },
            enabled = state.documentReady,
        )
    }
}

/** "15041992" → "15/04/1992" while typing. */
internal fun formatBirth(digits: String): String = buildString {
    digits.forEachIndexed { i, ch ->
        if (i == 2 || i == 4) append('/')
        append(ch)
    }
}

@Composable
private fun KycWaitingBody(text: String) {
    QScreen {
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(color = Q.colors.ac, modifier = Modifier.size(QSize.iconResult).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(QSpacing.x16))
        Text(text, style = Q.type.bodyLg, color = Q.colors.tx, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.weight(1f))
    }
}

/** FR-006/FR-007: back from the provider, checking the result. */
@Composable
fun KycWaitingScreen(state: KycState, onEvent: (KycEvent) -> Unit) {
    when (state.phase) {
        WaitPhase.Checking -> KycWaitingBody("Comprobando tu verificación…")
        WaitPhase.InProvider, WaitPhase.NotFinished, WaitPhase.Offline -> KycScaffold(
            title = when (state.phase) {
                WaitPhase.NotFinished -> "No terminaste la verificación"
                WaitPhase.Offline -> "No pudimos comprobar tu verificación"
                else -> "Completa la verificación"
            },
            help = when (state.phase) {
                WaitPhase.NotFinished -> "Vuelve para terminar de tomar las fotos."
                WaitPhase.Offline -> state.message ?: ""
                else -> "Se abrió la página del proveedor. Cuando termines, vuelve aquí."
            },
            icon = if (state.phase == WaitPhase.Offline) Ph.Warning else Ph.IdentificationCard,
            iconTint = if (state.phase == WaitPhase.Offline) Q.colors.warn else Q.colors.ac,
        ) {
            Spacer(Modifier.weight(1f))
            QPrimaryButton("Ya terminé", { onEvent(KycEvent.CheckNow) })
            Spacer(Modifier.height(QSpacing.x10))
            QTextButton("Volver a la verificación", { onEvent(KycEvent.ReopenProvider) })
        }
    }
}

/** FR-009/FR-010/FR-011. */
@Composable
fun KycResultScreen(state: KycState, onEvent: (KycEvent) -> Unit) {
    val c = Q.colors
    when (val o = state.outcome) {
        is KycOutcome.Approved -> KycScaffold(
            title = if (o.already) "Ya verificaste tu identidad" else "Identidad verificada",
            help = "Ya puedes usar todas las funciones de Qullqi.",
            icon = Ph.SealCheck,
        ) {
            Spacer(Modifier.weight(1f))
            QPrimaryButton("Ir al inicio", { onEvent(KycEvent.Finish) }, enabled = !state.busy)
        }
        KycOutcome.InReview -> KycScaffold(
            title = "Estamos revisando tus datos",
            help = "Te avisaremos cuando terminemos. Mientras tanto puedes usar la app.",
            icon = Ph.IdentificationCard,
            iconTint = c.warn,
        ) {
            Spacer(Modifier.weight(1f))
            QPrimaryButton("Ir al inicio", { onEvent(KycEvent.Finish) }, enabled = !state.busy)
        }
        is KycOutcome.Rejected -> KycScaffold(
            title = "No pudimos verificarte",
            help = o.reason,
            icon = Ph.X,
            iconTint = c.neg,
        ) {
            Spacer(Modifier.weight(1f))
            QPrimaryButton("Intentar de nuevo", { onEvent(KycEvent.Retry) })
            Spacer(Modifier.height(QSpacing.x10))
            QTextButton("Hacerlo más tarde", { onEvent(KycEvent.Finish) })
        }
        KycOutcome.Refused -> KycScaffold(
            title = "Contacta a soporte",
            help = "No podemos iniciar otra verificación desde la app. Nuestro equipo te ayudará.",
            icon = Ph.Warning,
            iconTint = c.warn,
        ) {
            Spacer(Modifier.weight(1f))
            QPrimaryButton("Ir al inicio", { onEvent(KycEvent.Finish) }, enabled = !state.busy)
        }
        null -> KycWaitingBody("Cargando…")
    }
}
