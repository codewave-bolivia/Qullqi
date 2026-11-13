package com.codewave.qullqi.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.unit.dp

/** Motion tokens (STYLEGUIDE §5). Respect reduce-motion by using [fade] instead of moves. */
object QMotion {
    val pushEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val sheetEasing: Easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
    val popEasing: Easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.2f)
    val veilEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val linear: Easing = LinearEasing
    /** CSS `ease-in-out`. */
    val easeInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    const val PUSH_MS = 300
    const val TAB_MS = 250
    const val SHEET_MS = 320
    const val SCRIM_MS = 200
    const val SHAKE_MS = 420
    const val POP_MS = 500
    const val PRESS_MS = 110
    const val VEIL_OUT_MS = 460
    const val VEIL_IN_MS = 600
    const val REDUCED_MS = 150
    const val SPLASH_MS = 1900
    const val TOAST_MS = 1900
    const val ENTERING_MS = 380
    /** Splash: qRise (.7 s, tagline delayed .15 s), qGlow (1.6 s loop, .55↔1), qLoad (1.7 s ease-in-out). */
    const val RISE_MS = 700
    const val RISE_DELAY_MS = 150
    const val GLOW_MS = 1600
    const val GLOW_MIN_ALPHA = 0.55f
    const val SPLASH_BAR_MS = 1700

    val pushOffset = 26.dp
    val riseOffset = 14.dp
    const val PRESS_SCALE = 0.94f
}
