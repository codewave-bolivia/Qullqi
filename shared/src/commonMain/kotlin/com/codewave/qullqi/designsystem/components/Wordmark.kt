package com.codewave.qullqi.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.codewave.qullqi.designsystem.Q

/** Brand wordmark (STYLEGUIDE §6): "qullqi" in Inter 500, tight tracking, followed by an accent dot. */
@Composable
fun QWordmark(modifier: Modifier = Modifier, style: TextStyle = Q.type.wordmark) {
    val fontSize: TextUnit = style.fontSize
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("qullqi", style = style, color = Q.colors.tx)
        // Dot ≈ 0.2 em, sitting on the baseline.
        val dot = (fontSize.value * 0.2f).dp
        Box(Modifier.padding(start = (dot.value * 0.35f).dp, bottom = (fontSize.value * 0.22f).dp).size(dot).background(Q.colors.ac, CircleShape))
    }
}
