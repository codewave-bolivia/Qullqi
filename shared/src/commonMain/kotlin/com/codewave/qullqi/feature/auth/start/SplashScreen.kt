package com.codewave.qullqi.feature.auth.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.codewave.qullqi.data.LocalDataReset
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.accentGlow
import com.codewave.qullqi.designsystem.components.QBottomSheet
import com.codewave.qullqi.designsystem.components.QPrimaryButton
import com.codewave.qullqi.designsystem.components.QSecondaryButton
import com.codewave.qullqi.designsystem.components.QWordmark

/**
 * Animated splash, a 1:1 port of the `is.splash` block of `Qullqi Pay Auth.dc.html` (FR-001):
 * left-aligned 64 sp wordmark with an accent period, pulsing 24×2 accent underline, tagline,
 * elliptical accent glow at (25 %, 42 %), and a 2 dp progress bar 64 dp above the bottom.
 * Routing is decided by [StartViewModel]. Debug builds: long-press the wordmark to reset local data.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SplashScreen(debugReset: LocalDataReset?, onReset: () -> Unit) {
    val c = Q.colors
    var debugSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.page), contentAlignment = Alignment.Center) {
        // Same responsive rule as QScreen: full screen on phones, centered 480 dp panel on wide windows.
        val wide = maxWidth >= QSize.wideWindow
        Box(
            Modifier
                .fillMaxHeight()
                .widthIn(max = QSize.contentMaxWidth)
                .fillMaxWidth()
                .then(if (wide) Modifier.padding(vertical = QSpacing.x24).clip(RoundedCornerShape(QRadius.lg)) else Modifier)
                .background(c.bg)
                .accentGlow(c.ac),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = QSize.splashPaddingH, end = QSize.splashPaddingH, bottom = QSize.splashPaddingBottom),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Rise(delayMs = 0) {
                    QWordmark(
                        style = Q.type.display,
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { if (debugReset != null) debugSheet = true },
                        ),
                    )
                }
                Spacer(Modifier.height(QSpacing.x10))
                GlowUnderline()
                Spacer(Modifier.height(QSpacing.x14))
                Rise(delayMs = QMotion.RISE_DELAY_MS) {
                    Text("Tu billetera en Bolivianos y dólares", style = Q.type.tagline, color = c.tx2)
                }
            }
            LoadingBar(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = QSize.splashBarInset, end = QSize.splashBarInset, bottom = QSize.splashBarBottom),
            )
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

/** `qRise`: opacity 0→1 and translateY 14 dp→0 over .7 s, optionally delayed (`both` fill mode). */
@Composable
private fun Rise(delayMs: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    // Delay inside the animation spec so it follows the frame clock (CSS `animation-delay`, fill `both`).
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(QMotion.RISE_MS, delayMillis = delayMs, easing = QMotion.pushEasing)) }
    val offsetPx = with(LocalDensity.current) { QMotion.riseOffset.toPx() }
    Box(Modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * offsetPx }) { content() }
}

/** 24×2 accent line with a 12 dp accent glow, pulsing .55↔1 every 1.6 s (`qGlow`). */
@Composable
private fun GlowUnderline() {
    val c = Q.colors
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = QMotion.GLOW_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(QMotion.GLOW_MS / 2, easing = QMotion.easeInOut), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    // CSS box-shadow `0 0 12px ac`: a blurred accent copy behind the line (blur is a no-op below Android 12,
    // where the line simply has no halo).
    Box(Modifier.alpha(pulse).size(QSize.splashUnderlineWidth, QSize.splashUnderlineHeight)) {
        Box(
            Modifier.matchParentSize()
                .blur(QSize.splashUnderlineGlow / 2, BlurredEdgeTreatment.Unbounded)
                .background(c.ac),
        )
        Box(Modifier.matchParentSize().background(c.ac))
    }
}

/** Bottom progress bar (`qLoad`): 2 dp, radius 1, `s3` track, accent fill 0→100 % in 1.7 s ease-in-out. */
@Composable
private fun LoadingBar(modifier: Modifier) {
    val c = Q.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(QMotion.SPLASH_BAR_MS, easing = QMotion.easeInOut)) }
    val shape = RoundedCornerShape(QSize.splashBarRadius)
    Box(modifier.fillMaxWidth().height(QSize.splashBarHeight).clip(shape).background(c.s3)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(c.ac))
    }
}
