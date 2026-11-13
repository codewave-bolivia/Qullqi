package com.codewave.qullqi.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing

/**
 * Bottom sheet (UI_GUIDELINES §3.8): scrim fades in 200 ms, sheet rises in 320 ms, handle 32×4,
 * top radius 20, max 82 % of the height. Tapping the scrim dismisses it.
 */
@Composable
fun QBottomSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Q.colors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.82f
        AnimatedVisibility(visible, enter = fadeIn(tween(QMotion.SCRIM_MS)), exit = fadeOut(tween(QMotion.SCRIM_MS))) {
            Box(
                Modifier.fillMaxSize().background(c.scrim)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(QMotion.SHEET_MS, easing = QMotion.sheetEasing)) { it },
            exit = slideOutVertically(tween(QMotion.SHEET_MS, easing = QMotion.sheetEasing)) { it },
        ) {
            val shape = RoundedCornerShape(topStart = QRadius.sheet, topEnd = QRadius.sheet)
            Column(
                Modifier.widthIn(max = QSize.contentMaxWidth).fillMaxWidth().heightIn(max = maxSheet)
                    .background(c.s1, shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = QSpacing.x18, end = QSpacing.x18, bottom = QSpacing.x28)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.padding(vertical = QSpacing.x10)
                        .size(QSize.sheetHandleWidth, QSize.sheetHandleHeight)
                        .background(c.s3, RoundedCornerShape(50)),
                )
                content()
            }
        }
    }
}
