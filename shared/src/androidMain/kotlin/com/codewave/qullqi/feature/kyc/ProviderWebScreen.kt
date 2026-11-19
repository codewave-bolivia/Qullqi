package com.codewave.qullqi.feature.kyc

import android.content.Context
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.QullqiTheme

/**
 * Frame of the identity provider page (spec 005 FR-005a): only the platform web view built by
 * [page]; the provider shows its own close control and system back leaves. While [loading], a
 * Nocturne loader covers the not-yet-painted page, which then fades in (T049). Android-only.
 */
@Composable
fun ProviderWebScreen(loading: Boolean, page: (Context) -> View) {
    QullqiTheme {
        val pageAlpha by animateFloatAsState(
            targetValue = if (loading) 0f else 1f,
            animationSpec = tween(QMotion.ENTERING_MS, easing = QMotion.easeInOut),
        )
        Box(Modifier.fillMaxSize().background(Q.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            AndroidView(modifier = Modifier.fillMaxSize().alpha(pageAlpha), factory = page)
            if (pageAlpha < 1f) {
                Column(
                    Modifier.fillMaxSize().alpha(1f - pageAlpha),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Q.colors.ac, modifier = Modifier.size(QSize.iconResult))
                    Spacer(Modifier.height(QSpacing.x16))
                    Text("Abriendo la verificación…", style = Q.type.bodySm, color = Q.colors.tx2)
                }
            }
        }
    }
}
