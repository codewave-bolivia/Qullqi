package com.codewave.qullqi.feature.auth.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.Ph
import com.codewave.qullqi.designsystem.components.QBottomSheet
import com.codewave.qullqi.designsystem.components.QIcon

/** "Cambiar de cuenta" (US2-5, FR-007): stored accounts with masked phones plus "Agregar otra cuenta". */
@Composable
fun AccountsSheet(state: LoginState, onEvent: (LoginEvent) -> Unit) {
    val c = Q.colors
    QBottomSheet(visible = state.showAccounts, onDismiss = { onEvent(LoginEvent.DismissAccounts) }) {
        Text("Cambiar de cuenta", style = Q.type.titleSheet, color = c.tx, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(QSpacing.x12))
        Column(verticalArrangement = Arrangement.spacedBy(QSpacing.x4)) {
            state.accounts.forEach { a ->
                SheetRow(onClick = { onEvent(LoginEvent.PickAccount(a.id)) }) {
                    Box(Modifier.size(QSize.touchTarget).background(c.s2, RoundedCornerShape(QRadius.md)), contentAlignment = Alignment.Center) {
                        Text(a.initials, style = Q.type.bodyMd, color = c.tx)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = Q.type.bodyMd, color = c.tx)
                        Text(a.maskedPhone, style = Q.type.caption, color = c.tx2)
                    }
                    if (a.locked) QIcon(Ph.LockSimple, "Bloqueada", size = QSize.iconInline, tint = c.neg)
                    if (a.id == state.account?.id) QIcon(Ph.Check, "Actual", size = QSize.iconInline, tint = c.ac)
                }
            }
            SheetRow(onClick = { onEvent(LoginEvent.AddAccount) }) {
                Box(Modifier.size(QSize.touchTarget).background(c.acT, RoundedCornerShape(QRadius.md)), contentAlignment = Alignment.Center) {
                    QIcon(Ph.UserSwitch, null, size = QSize.iconInline, tint = c.ac)
                }
                Text("Agregar otra cuenta", style = Q.type.bodyMd, color = c.ac, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SheetRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = QSize.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = QSpacing.x8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QSpacing.x12),
        content = content,
    )
}
