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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing

/**
 * PIN boxes, following the reference `dots()` rules: filled → accent border and 12 dp accent dot;
 * current (index == filled) → accent border + 16 dp accent glow; others → divider border, no dot;
 * [success] → everything accent; [error] with an empty entry → negative borders.
 * The digits themselves are never rendered.
 */
@Composable
fun QPinBoxes(
    filled: Int,
    modifier: Modifier = Modifier,
    length: Int = 4,
    error: Boolean = false,
    success: Boolean = false,
    shakeTrigger: Int = 0,
) {
    val c = Q.colors
    Row(
        modifier.shake(shakeTrigger).semantics { contentDescription = "$filled de $length dígitos" },
        horizontalArrangement = Arrangement.spacedBy(QSize.pinGap),
    ) {
        repeat(length) { i ->
            val isFilled = i < filled
            val isCurrent = i == filled && !error && !success
            val border = when {
                success -> c.ac
                error && filled == 0 -> c.neg
                isFilled || isCurrent -> c.ac
                else -> c.ln
            }
            Box(
                Modifier.size(QSize.pinBoxWidth, QSize.pinBoxHeight).box(border, glow = isCurrent),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(QSize.pinDot).background(if (isFilled || success) c.ac else Color.Transparent, CircleShape))
            }
        }
    }
}

/** Six-box code grid (UI_GUIDELINES §3.6). Shows the typed digits (SMS codes are not secrets). */
@Composable
fun QCodeBoxes(value: String, modifier: Modifier = Modifier, length: Int = 6, error: Boolean = false, shakeTrigger: Int = 0) {
    val c = Q.colors
    Row(modifier.fillMaxWidth().shake(shakeTrigger), horizontalArrangement = Arrangement.spacedBy(QSpacing.x8)) {
        repeat(length) { i ->
            val isCurrent = i == value.length && !error
            val border = when {
                error && value.isEmpty() -> c.neg
                i < value.length || isCurrent -> c.ac
                else -> c.ln
            }
            Box(
                Modifier.weight(1f).widthIn(max = QSize.pinBoxWidth).height(QSize.codeBoxHeight).box(border, glow = isCurrent),
                contentAlignment = Alignment.Center,
            ) {
                value.getOrNull(i)?.let { Text(it.toString(), style = Q.type.digits, color = c.tx) }
            }
        }
    }
}

@Composable
private fun Modifier.box(border: Color, glow: Boolean): Modifier {
    val c = Q.colors
    val shape = RoundedCornerShape(QRadius.sm)
    return this
        .shadow(if (glow) QSize.boxGlow else 0.dp, shape, clip = false, ambientColor = c.ac, spotColor = c.ac)
        .background(c.s1, shape)
        .border(QSize.border, border, shape)
}
