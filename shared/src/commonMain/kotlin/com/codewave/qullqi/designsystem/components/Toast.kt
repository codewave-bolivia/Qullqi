package com.codewave.qullqi.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import kotlinx.coroutines.delay

@Stable
class QToastState {
    var text by mutableStateOf<String?>(null)
        private set
    private var serial by mutableIntStateOf(0)

    fun show(message: String) {
        text = message
        serial++
    }

    internal suspend fun autoHide(expected: Int) {
        delay(QMotion.TOAST_MS.toLong())
        if (serial == expected) text = null
    }

    internal val current: Int get() = serial
}

/** Short message near the bottom for 1.9 s (UI_GUIDELINES §4). */
@Composable
fun QToastHost(state: QToastState) {
    val c = Q.colors
    val serial = state.current
    LaunchedEffect(serial) { if (state.text != null) state.autoHide(serial) }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(state.text != null, enter = fadeIn(), exit = fadeOut()) {
            val shape = RoundedCornerShape(QRadius.sm)
            Text(
                state.text.orEmpty(),
                style = Q.type.bodySm,
                color = c.tx,
                modifier = Modifier.padding(bottom = QSpacing.x32)
                    .background(c.s2, shape)
                    .border(QSize.border, c.ln, shape)
                    .padding(horizontal = QSpacing.x16, vertical = QSpacing.x10),
            )
        }
    }
}
