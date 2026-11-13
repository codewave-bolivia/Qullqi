package com.codewave.qullqi.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing

/**
 * Text input (UI_GUIDELINES §3.5): surface fill, divider border; live validation turns border
 * and help accent ([valid] = true) or negative ([valid] = false). Null = not yet validated.
 */
@Composable
fun QTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    help: String? = null,
    valid: Boolean? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    val c = Q.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val stateColor = when (valid) {
        true -> c.ac
        false -> c.neg
        null -> if (focused) c.ac else c.ln
    }
    val shape = RoundedCornerShape(QRadius.sm)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QSpacing.x6)) {
        Text(label, style = Q.type.caption, color = c.tx2)
        Row(
            Modifier
                .fillMaxWidth()
                .height(QSize.fieldHeight)
                .background(c.s1, shape)
                .border(QSize.border, stateColor, shape)
                .padding(horizontal = QSpacing.x14),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QSpacing.x10),
        ) {
            if (prefix != null) Text(prefix, style = Q.type.bodyLg, color = c.tx2)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = Q.type.bodyLg.copy(color = c.tx),
                cursorBrush = SolidColor(c.ac),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                interactionSource = source,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (help != null) {
            Text(help, style = Q.type.caption, color = if (valid == null) c.tx2 else stateColor)
        }
    }
}
