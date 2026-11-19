package com.codewave.qullqi.feature.kyc

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QullqiTheme

/**
 * Frame of the identity provider page (spec 005 FR-005a): only the platform web view built by
 * [page]; the provider shows its own close control and system back leaves. Android-only.
 */
@Composable
fun ProviderWebScreen(page: (Context) -> View) {
    QullqiTheme {
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Q.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing),
            factory = page,
        )
    }
}
