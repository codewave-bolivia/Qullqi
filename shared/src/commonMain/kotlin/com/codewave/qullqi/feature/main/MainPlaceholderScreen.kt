package com.codewave.qullqi.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codewave.qullqi.AppDependencies
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QBottomSheet
import com.codewave.qullqi.designsystem.components.QIcon
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QSecondaryButton
import com.codewave.qullqi.designsystem.components.QTextButton
import com.codewave.qullqi.designsystem.components.QWordmark
import com.codewave.qullqi.domain.model.VerificationStatus
import androidx.compose.foundation.layout.Box

/** Temporary main area (US6): name, verification status, "Verificar" and "Cerrar sesión". M4 replaces it. */
@Composable
fun MainPlaceholderScreen(deps: AppDependencies, actions: MainActions) {
    val vm = viewModel(key = "main-placeholder") { MainPlaceholderViewModel(deps.currentUser, deps.session) }
    LaunchedEffect(vm) {
        vm.effects.collect { e ->
            when (e) {
                MainEffect.OpenKyc -> actions.verify()
                is MainEffect.LoggedOut -> actions.logout(e.userId)
            }
        }
    }
    val state by vm.state.collectAsState()
    val c = Q.colors
    Box {
        QScreen {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QWordmark(style = Q.type.titleBar)
                Spacer(Modifier.weight(1f))
                QTextButton("Cerrar sesión", { vm.onEvent(MainEvent.Logout) }, destructive = true, icon = {
                    QIcon(Ph.SignOut, null, size = QSize.iconInline, tint = c.neg)
                })
            }
            Spacer(Modifier.height(QSpacing.x28))
            Text("Hola, ${state.name.substringBefore(' ')}", style = Q.type.titleFlow, color = c.tx)
            Spacer(Modifier.height(QSpacing.x8))
            StatusBadge(state.verification)
            Spacer(Modifier.height(QSpacing.x24))
            if (state.verification == VerificationStatus.NOT_VERIFIED) {
                val shape = RoundedCornerShape(QRadius.lg)
                Column(Modifier.fillMaxWidth().background(c.s1, shape).border(QSize.border, c.warn, shape).padding(QSpacing.x16)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QSpacing.x10)) {
                        QIcon(Ph.Warning, null, size = QSize.iconInline, tint = c.warn)
                        Text("Verifica tu identidad", style = Q.type.bodyMd, color = c.tx)
                    }
                    Spacer(Modifier.height(QSpacing.x6))
                    Text("Sin verificación no puedes depositar, enviar ni pagar.", style = Q.type.bodySm, color = c.tx2)
                    Spacer(Modifier.height(QSpacing.x14))
                    QPrimaryButton("Verificar", { vm.onEvent(MainEvent.Verify) })
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Área principal en construcción (M4)",
                style = Q.type.caption,
                color = c.tx2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(QSpacing.x8))
        }
        QBottomSheet(visible = state.confirmLogout, onDismiss = { vm.onEvent(MainEvent.DismissLogout) }) {
            QIcon(Ph.SignOut, null, size = QSize.iconResult, tint = c.neg)
            Spacer(Modifier.height(QSpacing.x12))
            Text("¿Cerrar sesión?", style = Q.type.titleSheet, color = c.tx)
            Spacer(Modifier.height(QSpacing.x6))
            Text("Tu cuenta queda guardada en este dispositivo.", style = Q.type.bodySm, color = c.tx2)
            Spacer(Modifier.height(QSpacing.x20))
            QSecondaryButton("Cerrar sesión", { vm.onEvent(MainEvent.ConfirmLogout) })
            Spacer(Modifier.height(QSpacing.x6))
            QTextButton("Cancelar", { vm.onEvent(MainEvent.DismissLogout) }, muted = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusBadge(status: VerificationStatus) {
    val c = Q.colors
    val (text, color) = when (status) {
        VerificationStatus.NOT_VERIFIED -> "Sin verificar" to c.warn
        VerificationStatus.UNDER_REVIEW -> "Verificación en revisión" to c.warn
        VerificationStatus.VERIFIED -> "Verificado · Nivel 2" to c.ac
    }
    Text(
        text,
        style = Q.type.caption,
        color = color,
        modifier = Modifier.border(QSize.border, color, RoundedCornerShape(QRadius.xs)).padding(horizontal = QSpacing.x8, vertical = QSpacing.x2),
    )
}
