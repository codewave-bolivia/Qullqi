package com.codewave.qullqi.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing

/** Press feedback shared by buttons and keys (STYLEGUIDE §5 motion.press). */
@Composable
internal fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) QMotion.PRESS_SCALE else 1f, tween(QMotion.PRESS_MS))
    return scale(s)
}

/**
 * Primary CTA (UI_GUIDELINES §3.1): accent outline, 8 % accent fill, accent text and glow.
 * Never a solid fill. Disabled = 40 % opacity and clicks ignored.
 */
@Composable
fun QPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val c = Q.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(QRadius.sm)
    Box(
        modifier
            .fillMaxWidth()
            .height(QSize.buttonHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .pressScale(source)
            .shadow(if (enabled) QSize.glowButton else 0.dp, shape, clip = false, ambientColor = c.ac, spotColor = c.ac)
            .background(c.bg, shape)
            .background(c.ac.copy(alpha = if (pressed) 0.26f else 0.08f), shape)
            .border(QSize.border, c.ac, shape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, c.ac, icon)
    }
}

/** Secondary (UI_GUIDELINES §3.1): divider border, transparent fill, text color. */
@Composable
fun QSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Q.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(QRadius.sm)
    Box(
        modifier
            .fillMaxWidth()
            .height(QSize.buttonHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .pressScale(source)
            .background(if (pressed) c.tx.copy(alpha = 0.07f) else Color.Transparent, shape)
            .border(QSize.border, c.ln, shape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, c.tx, null)
    }
}

/** Text button: no border; accent by default, secondary text when [muted], negative when [destructive]. */
@Composable
fun QTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
    destructive: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val c = Q.colors
    val color = when {
        destructive -> c.neg
        muted -> c.tx2
        else -> c.ac
    }
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = QSize.touchTarget)
            .alpha(if (enabled) 1f else 0.4f)
            .pressScale(source)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = QSpacing.x8),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, color, icon)
    }
}

@Composable
private fun ButtonContent(text: String, color: Color, icon: (@Composable () -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QSpacing.x8)) {
        icon?.invoke()
        Text(text, style = Q.type.bodyLg.copy(fontWeight = Q.type.bodyMd.fontWeight), color = color)
    }
}

/** Footer link as in the reference login ("¿Olvidaste tu PIN?", "Cambiar de cuenta"): 13.5/500, no border or fill. */
@Composable
fun QLinkButton(text: String, onClick: () -> Unit, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = QSize.touchTarget)
            .alpha(if (enabled) 1f else 0.4f)
            .pressScale(source)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = QSpacing.x6),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Q.type.link, color = color)
    }
}
