package com.codewave.qullqi.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Responsive screen frame (research R8, constitution VI).
 * - Compact width (< 600 dp): full-bleed `bg`, 24 dp side margins.
 * - Wider windows: `page` around a centered `bg` panel of max 480 dp with large radius,
 *   like the phone frame in the reference.
 * - Content scrolls when taller than the window; system bars are respected.
 */
@Composable
fun QScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Q.colors
    BoxWithConstraints(modifier.fillMaxSize().background(c.page)) {
        val wide = maxWidth >= QSize.wideWindow
        val panel = Modifier
            .widthIn(max = QSize.contentMaxWidth)
            .fillMaxWidth()
            .then(if (wide) Modifier.padding(vertical = QSpacing.x24).clip(RoundedCornerShape(QRadius.lg)) else Modifier)
            .fillMaxHeight()
            .background(c.bg)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(panel) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .heightIn(min = 0.dp)
                        .padding(horizontal = QSpacing.x24, vertical = QSpacing.x16),
                    content = content,
                )
            }
        }
    }
}
