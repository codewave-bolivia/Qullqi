package com.codewave.qullqi.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * Veil used when switching activities (ARCHITECTURE.md §6): the origin is covered by a
 * background-colored veil 0→1 before leaving, and the destination starts covered and reveals 1→0.
 */
@Stable
class VeilState(startCovered: Boolean) {
    internal val alpha = Animatable(if (startCovered) 1f else 0f)

    /** Covers the screen, then runs [action] (e.g. start the next activity). */
    suspend fun coverThen(action: () -> Unit) {
        alpha.animateTo(1f, tween(QMotion.VEIL_OUT_MS, easing = QMotion.veilEasing))
        action()
    }

    suspend fun reveal() {
        alpha.animateTo(0f, tween(QMotion.VEIL_IN_MS, easing = QMotion.veilEasing))
    }
}

@Composable
fun rememberVeilState(startCovered: Boolean): VeilState {
    val state = remember { VeilState(startCovered) }
    LaunchedEffect(Unit) { if (startCovered) state.reveal() }
    return state
}

@Composable
fun VeilOverlay(state: VeilState) {
    val a = state.alpha.value
    if (a > 0f) Box(Modifier.fillMaxSize().alpha(a).background(Q.colors.bg))
}
