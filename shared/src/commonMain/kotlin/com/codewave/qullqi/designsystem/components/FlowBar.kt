package com.codewave.qullqi.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing

/** Flow step header (UI_GUIDELINES §2.3): back, segmented progress bar with glowing current step, "n/total". */
@Composable
fun QFlowTopBar(step: Int, total: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Q.colors
    Row(
        modifier.fillMaxWidth().height(QSize.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QSpacing.x12),
    ) {
        QBackButton(onBack)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(QSpacing.x4)) {
            repeat(total) { i ->
                val shape = RoundedCornerShape(50)
                val current = i == step - 1
                Box(
                    Modifier.weight(1f).height(QSize.progressBar)
                        .shadow(if (current) QSize.glowBar else 0.dp, shape, clip = false, ambientColor = c.ac, spotColor = c.ac)
                        .background(if (i < step) c.ac else c.s3, shape),
                )
            }
        }
        Text("$step/$total", style = Q.type.caption, color = c.tx2)
    }
}

@Composable
fun QBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(QSize.touchTarget).clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        QIcon(Ph.CaretLeft, "Volver", tint = Q.colors.tx)
    }
}
