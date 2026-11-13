package com.codewave.qullqi.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Nocturne color roles (docs/references-views/_ds/nocturne-…/styles.css and the [data-theme]
 * blocks of "Qullqi Pay Auth.dc.html"). Screens use only these roles, never literal colors.
 */
@Immutable
data class QColors(
    val page: Color,
    val bg: Color,
    val s1: Color,
    val s2: Color,
    val s3: Color,
    val ln: Color,
    val tx: Color,
    val tx2: Color,
    val ac: Color,
    val acT: Color,
    val acTx: Color,
    val neg: Color,
    val warn: Color,
    val isDark: Boolean,
) {
    /** Scrim behind bottom sheets (UI_GUIDELINES §3.8). */
    val scrim: Color get() = Color(0xFF0E0F18).copy(alpha = 0.6f)

    companion object {
        val Dark = QColors(
            page = Color(0xFF0E0F18),
            bg = Color(0xFF161826),
            s1 = Color(0xFF232532),
            s2 = Color(0xFF2C2E3C),
            s3 = Color(0xFF3F424D),
            ln = Color(0xFFE9E9ED).copy(alpha = 0.16f),
            tx = Color(0xFFE9E9ED),
            tx2 = Color(0xFF9397AB),
            ac = Color(0xFF9184D9),
            acT = Color(0xFF2B2741),
            acTx = Color(0xFFD2CEFD),
            neg = Color(0xFFEB827B),
            warn = Color(0xFFEABC6E),
            isDark = true,
        )

        val Light = QColors(
            page = Color(0xFFC4C8DB),
            bg = Color(0xFFE4E7F5),
            s1 = Color(0xFFF3F5FE),
            s2 = Color(0xFFD9DDEE),
            s3 = Color(0xFFCFD3E5),
            ln = Color(0xFF161826).copy(alpha = 0.13f),
            tx = Color(0xFF161826),
            tx2 = Color(0xFF595D6C),
            ac = Color(0xFF796CBF),
            acT = Color(0xFFE7E5FE),
            acTx = Color(0xFF5D5294),
            neg = Color(0xFFBD413F),
            warn = Color(0xFFA06604),
            isDark = false,
        )
    }
}

/** Spacing scale, base 2 dp (STYLEGUIDE §3.1). */
object QSpacing {
    val x2 = 2.dp
    val x4 = 4.dp
    val x6 = 6.dp
    val x8 = 8.dp
    val x10 = 10.dp
    val x12 = 12.dp
    val x14 = 14.dp
    val x16 = 16.dp
    val x18 = 18.dp
    val x20 = 20.dp
    val x22 = 22.dp
    val x24 = 24.dp
    val x28 = 28.dp
    val x32 = 32.dp
    val x36 = 36.dp
}

/** Corner radii (STYLEGUIDE §3.2). */
object QRadius {
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 14.dp
    val sheet = 20.dp
}

/** Fixed component sizes from UI_GUIDELINES §3 (kept here so screens never hardcode them). */
object QSize {
    val buttonHeight = 52.dp
    val fieldHeight = 50.dp
    val pinBoxWidth = 54.dp
    val pinBoxHeight = 62.dp
    val codeBoxHeight = 56.dp
    val keyHeight = 56.dp
    val keyHeightCompact = 48.dp
    val iconButton = 42.dp
    val touchTarget = 44.dp
    val pinDot = 12.dp
    val checkbox = 22.dp
    val progressBar = 3.dp
    val sheetHandleWidth = 32.dp
    val sheetHandleHeight = 4.dp
    val contentMaxWidth = 480.dp
    val avatar = 56.dp
    val iconInline = 18.dp
    val iconBar = 22.dp
    val iconResult = 40.dp
    val border = 1.dp
    val focusOutline = 2.dp
    val splashBar = 120.dp
    val keypadMaxWidth = 360.dp
    /** Window height under which keypad keys shrink (research R8). */
    val compactHeight = 640.dp
    /** Accent glow radii (STYLEGUIDE §3.3 "brillo de acento"). */
    val glowButton = 14.dp
    val glowBox = 10.dp
    val glowBar = 6.dp
}
