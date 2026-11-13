package com.codewave.qullqi.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.codewave.qullqi.designsystem.Q

/**
 * Brand wordmark (STYLEGUIDE §6), exactly as the reference markup:
 * `qullqi<span style="color:var(--ac)">.</span>` — an accent period character, not a dot shape.
 */
@Composable
fun QWordmark(modifier: Modifier = Modifier, style: TextStyle = Q.type.wordmark) {
    val c = Q.colors
    Text(
        buildAnnotatedString {
            append("qullqi")
            withStyle(SpanStyle(color = c.ac)) { append(".") }
        },
        style = style,
        color = c.tx,
        modifier = modifier,
    )
}
