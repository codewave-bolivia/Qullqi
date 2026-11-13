package com.codewave.qullqi.designsystem

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalQColors = staticCompositionLocalOf { QColors.Dark }
val LocalQType = staticCompositionLocalOf<QType> { error("QullqiTheme not set") }

/** Nocturne theme. Dark is the default (UI_GUIDELINES §8); only the Nocturne accent is supported. */
@Composable
fun QullqiTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (dark) QColors.Dark else QColors.Light
    val type = qType()
    // Material is used only for primitives (ripples, text fields internals); its scheme mirrors ours.
    val scheme = if (dark) {
        darkColorScheme(primary = colors.ac, background = colors.bg, surface = colors.s1, onSurface = colors.tx, onBackground = colors.tx, error = colors.neg)
    } else {
        lightColorScheme(primary = colors.ac, background = colors.bg, surface = colors.s1, onSurface = colors.tx, onBackground = colors.tx, error = colors.neg)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalQColors provides colors,
            LocalQType provides type,
            LocalContentColor provides colors.tx,
            LocalTextSelectionColors provides TextSelectionColors(colors.ac, colors.ac.copy(alpha = 0.3f)),
            content = content,
        )
    }
}

object Q {
    val colors: QColors
        @Composable @ReadOnlyComposable get() = LocalQColors.current
    val type: QType
        @Composable @ReadOnlyComposable get() = LocalQType.current
}
