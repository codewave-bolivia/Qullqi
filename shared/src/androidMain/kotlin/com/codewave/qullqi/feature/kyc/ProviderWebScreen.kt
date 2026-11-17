package com.codewave.qullqi.feature.kyc

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.QullqiTheme
import com.codewave.qullqi.designsystem.components.QBackButton

/**
 * Frame of the identity provider page (spec 005 FR-005a): Nocturne bar with close, then the
 * platform web view built by [page]. Android-only (the WebView lives in androidApp).
 */
@Composable
fun ProviderWebScreen(onClose: () -> Unit, page: (Context) -> View) {
    QullqiTheme {
        Column(Modifier.fillMaxSize().background(Q.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = QSpacing.x8), verticalAlignment = Alignment.CenterVertically) {
                QBackButton(onBack = onClose)
                Text("Verificación de identidad", style = Q.type.titleBar, color = Q.colors.tx)
            }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = page)
        }
    }
}
