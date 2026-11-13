package com.codewave.qullqi.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.codewave.qullqi.designsystem.QMotion

/** Horizontal shake on wrong input (STYLEGUIDE motion.shake). Increment [trigger] to play it. */
fun Modifier.shake(trigger: Int): Modifier = composed {
    val offset = remember { Animatable(0f) }
    val px = LocalDensity.current.density
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = QMotion.SHAKE_MS
                -10f at (QMotion.SHAKE_MS * 0.2f).toInt()
                9f at (QMotion.SHAKE_MS * 0.4f).toInt()
                -6f at (QMotion.SHAKE_MS * 0.6f).toInt()
                4f at (QMotion.SHAKE_MS * 0.8f).toInt()
            },
        )
    }
    graphicsLayer { translationX = offset.value * px }
}
