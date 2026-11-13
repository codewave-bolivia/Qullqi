package com.codewave.qullqi.feature.auth.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codewave.qullqi.data.LocalDataReset
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.QBottomSheet
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QSecondaryButton
import com.codewave.qullqi.designsystem.components.QWordmark

/**
 * Animated splash (FR-001): wordmark and a loading bar that fills in 1.9 s, on the same background
 * as the system launch screen so the hand-off is invisible. Routing is decided by [StartViewModel].
 * Debug builds: long-press the wordmark to reset local data (research R13).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SplashScreen(debugReset: LocalDataReset?, onReset: () -> Unit) {
    val c = Q.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(QMotion.SPLASH_MS, easing = QMotion.linear)) }
    var debugSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(QSpacing.x24)) {
            QWordmark(
                Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { if (debugReset != null) debugSheet = true },
                ),
            )
            Box(Modifier.width(SPLASH_BAR_WIDTH).height(QSize.progressBar).background(c.s3, RoundedCornerShape(50))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(c.ac, RoundedCornerShape(50)))
            }
        }
    }

    if (debugReset != null) {
        QBottomSheet(visible = debugSheet, onDismiss = { debugSheet = false }) {
            Text("Reiniciar datos (debug)", style = Q.type.titleSheet, color = c.tx)
            Spacer(Modifier.height(QSpacing.x8))
            Text("Solo en builds de desarrollo. Borra todo lo guardado en este dispositivo.", style = Q.type.bodySm, color = c.tx2)
            Spacer(Modifier.height(QSpacing.x20))
            QPrimaryButton("Con datos de ejemplo", { debugReset.reset(withSamples = true); debugSheet = false; onReset() })
            Spacer(Modifier.height(QSpacing.x10))
            QSecondaryButton("Sin datos de ejemplo", { debugReset.reset(withSamples = false); debugSheet = false; onReset() })
        }
    }
}

private val SPLASH_BAR_WIDTH = 120.dp
