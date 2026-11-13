package com.codewave.qullqi.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import qullqi.shared.generated.resources.Res
import qullqi.shared.generated.resources.inter_medium
import qullqi.shared.generated.resources.inter_regular

/** Type scale (STYLEGUIDE §1). Inter only, weights 400 and 500 — never 600 or more. */
@Immutable
data class QType(
    val titleFlow: TextStyle,
    val titleLg: TextStyle,
    val titleSheet: TextStyle,
    val titleBar: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val bodySm: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val wordmark: TextStyle,
    /** Splash wordmark: 64 / 500 / −0.05em. */
    val display: TextStyle,
    /** Splash tagline: 15 / 400. */
    val tagline: TextStyle,
    /** Digits in boxes and keypads: tabular figures so they never jump. */
    val digits: TextStyle,
)

@Composable
fun interFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.W400),
    Font(Res.font.inter_medium, FontWeight.W500),
)

@Composable
fun qType(): QType {
    val inter = interFamily()
    fun style(size: Float, weight: Int = 400, tracking: Float = 0f, lineHeight: Float? = null) = TextStyle(
        fontFamily = inter,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        letterSpacing = tracking.em,
        lineHeight = lineHeight?.let { (size * it).sp } ?: TextStyle.Default.lineHeight,
    )
    return QType(
        titleFlow = style(28f, 500, -0.025f, 1.15f),
        titleLg = style(25f, 500, -0.02f, 1.15f),
        titleSheet = style(20f, 500, 0f, 1.2f),
        titleBar = style(18f, 500, -0.01f, 1.2f),
        bodyLg = style(15.5f, 400, 0f, 1.5f),
        bodyMd = style(14.5f, 500, 0f, 1.4f),
        bodySm = style(14f, 400, 0f, 1.5f),
        caption = style(12.5f, 400, 0f, 1.4f),
        overline = style(11f, 500, 0.08f, 1.3f),
        wordmark = style(34f, 500, -0.04f, 1.05f),
        display = style(64f, 500, -0.05f, 1.0f),
        tagline = style(15f, 400, 0f, 1.4f),
        digits = style(24f, 500, 0f, 1.1f).copy(fontFeatureSettings = "tnum"),
    )
}
