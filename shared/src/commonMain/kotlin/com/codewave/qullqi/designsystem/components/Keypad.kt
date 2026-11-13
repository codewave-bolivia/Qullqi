package com.codewave.qullqi.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize
import com.codewave.qullqi.designsystem.QSpacing

/**
 * Custom 3×4 keypad (UI_GUIDELINES §3.6); the system keyboard never opens for PIN/code entry.
 * [leftKey] is the bottom-left slot (fingerprint on login), empty when null.
 */
@Composable
fun QKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leftKey: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().widthIn(max = 360.dp)) {
        // Shorter keys on short windows so the screen fits (research R8).
        val keyHeight = if (maxHeight < 640.dp && maxHeight != androidx.compose.ui.unit.Dp.Infinity) QSize.keyHeightCompact else QSize.keyHeight
        Column(verticalArrangement = Arrangement.spacedBy(QSpacing.x8)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(QSpacing.x8)) {
                    row.forEach { d -> Key(Modifier.weight(1f), keyHeight, enabled, label = d.toString(), onClick = { onDigit(d) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(QSpacing.x8)) {
                Box(Modifier.weight(1f).height(keyHeight), contentAlignment = Alignment.Center) { leftKey?.invoke() }
                Key(Modifier.weight(1f), keyHeight, enabled, label = "0", onClick = { onDigit(0) })
                Key(Modifier.weight(1f), keyHeight, enabled, label = null, transparent = true, onClick = onDelete) {
                    QIcon(Ph.Backspace, "Borrar", tint = Q.colors.tx2)
                }
            }
        }
    }
}

/** A keypad-styled key; also used by screens for the fingerprint slot. */
@Composable
fun QKey(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = QSize.keyHeight,
    enabled: Boolean = true,
    transparent: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) = Key(modifier.fillMaxWidth(), height, enabled, null, transparent, onClick, content)

@Composable
private fun Key(
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    label: String?,
    transparent: Boolean = false,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val c = Q.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(QRadius.sm)
    val fill = when {
        pressed -> c.s2
        transparent -> Color.Transparent
        else -> c.s1
    }
    Box(
        modifier
            .height(height)
            .pressScale(source)
            .background(fill, shape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, style = Q.type.digits, color = c.tx) else content?.invoke()
    }
}

/**
 * Hardware keyboard support for PIN/code screens (desktop, tablets): digits, Backspace, Enter.
 * Request focus with the returned requester so key events reach the screen.
 */
fun Modifier.digitKeyboardInput(
    focusRequester: FocusRequester,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit = {},
): Modifier = this
    .focusRequester(focusRequester)
    .focusable()
    .onKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
        val digit = DIGIT_KEYS.indexOf(e.key).takeIf { it >= 0 } ?: NUMPAD_KEYS.indexOf(e.key).takeIf { it >= 0 }
        when {
            digit != null -> { onDigit(digit); true }
            e.key == Key.Backspace || e.key == Key.Delete -> { onDelete(); true }
            e.key == Key.Enter || e.key == Key.NumPadEnter -> { onEnter(); true }
            else -> false
        }
    }

private val DIGIT_KEYS = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
private val NUMPAD_KEYS = listOf(
    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
    Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
)
