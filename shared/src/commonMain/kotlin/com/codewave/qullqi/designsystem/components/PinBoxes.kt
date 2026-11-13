package com.codewave.qullqi.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing

/**
 * PIN boxes (UI_GUIDELINES §3.6): 54×62, current box with accent border and glow, filled boxes
 * show a 12 dp accent dot. The digits themselves are never rendered.
 */
@Composable
fun QPinBoxes(filled: Int, modifier: Modifier = Modifier, length: Int = 4, error: Boolean = false, shakeTrigger: Int = 0) {
    Row(
        modifier.shake(shakeTrigger).semantics { contentDescription = "$filled de $length dígitos" },
        horizontalArrangement = Arrangement.spacedBy(QSpacing.x12),
    ) {
        repeat(length) { i ->
            Box(
                Modifier.size(QSize.pinBoxWidth, QSize.pinBoxHeight).box(current = i == filled && !error, error = error),
                contentAlignment = Alignment.Center,
            ) {
                if (i < filled) Box(Modifier.size(QSize.pinDot).background(if (error) Q.colors.neg else Q.colors.ac, CircleShape))
            }
        }
    }
}

/** Six-box code grid (UI_GUIDELINES §3.6). Shows the typed digits (SMS codes are not secrets). */
@Composable
fun QCodeBoxes(value: String, modifier: Modifier = Modifier, length: Int = 6, error: Boolean = false, shakeTrigger: Int = 0) {
    Row(modifier.fillMaxWidth().shake(shakeTrigger), horizontalArrangement = Arrangement.spacedBy(QSpacing.x8)) {
        repeat(length) { i ->
            Box(
                Modifier.weight(1f).widthIn(max = QSize.pinBoxWidth).height(QSize.codeBoxHeight)
                    .box(current = i == value.length && !error, error = error),
                contentAlignment = Alignment.Center,
            ) {
                value.getOrNull(i)?.let { Text(it.toString(), style = Q.type.digits, color = Q.colors.tx) }
            }
        }
    }
}

@Composable
private fun Modifier.box(current: Boolean, error: Boolean): Modifier {
    val c = Q.colors
    val shape = RoundedCornerShape(QRadius.xs)
    val border = when {
        error -> c.neg
        current -> c.ac
        else -> c.ln
    }
    return this
        .shadow(if (current) QSize.glowBox else 0.dp, shape, clip = false, ambientColor = c.ac, spotColor = c.ac)
        .background(c.s1, shape)
        .border(QSize.border, border, shape)
}
