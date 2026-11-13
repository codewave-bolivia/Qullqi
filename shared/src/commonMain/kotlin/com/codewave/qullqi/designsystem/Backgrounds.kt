package com.codewave.qullqi.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * Accent glow behind a screen, reproducing CSS
 * `radial-gradient(rx% ry% at cx% cy%, color-mix(accent alpha), transparent)` — an **elliptical**
 * gradient. Compose gradients are circular, so the circle is drawn and then scaled vertically by ry/rx.
 * Defaults are the splash values: `radial-gradient(70% 45% at 25% 42%, ac 18%, transparent)`.
 */
fun Modifier.accentGlow(
    color: Color,
    alpha: Float = 0.18f,
    centerX: Float = 0.25f,
    centerY: Float = 0.42f,
    radiusX: Float = 0.70f,
    radiusY: Float = 0.45f,
): Modifier = drawBehind {
    val rx = size.width * radiusX
    val ry = size.height * radiusY
    if (rx <= 0f || ry <= 0f) return@drawBehind
    val center = Offset(size.width * centerX, size.height * centerY)
    val scaleY = ry / rx
    withTransform({ scale(1f, scaleY, pivot = center) }) {
        // In the scaled space the rect must be taller by 1/scaleY to still cover the whole screen.
        val top = center.y - center.y / scaleY
        val height = size.height / scaleY
        drawRect(
            brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = rx),
            topLeft = Offset(0f, top),
            size = Size(size.width, height),
        )
    }
}
