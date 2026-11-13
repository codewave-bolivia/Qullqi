package com.codewave.qullqi.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.designsystem.QRadius
import com.codewave.qullqi.designsystem.QSize

/**
 * Custom 3×4 keypad, as the reference (`is.login` keys): full width with 12 dp side padding,
 * three equal columns, 2 dp gaps, 58 dp transparent keys, digits 24/400, `s1` only on hover/press,
 * press scale .93. The system keyboard never opens. [leftKey] is the bottom-left slot (fingerprint).
 */
@Composable
fun QKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leftKey: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(horizontal = QSize.keypadPaddingH)) {
        // Shorter keys on short windows so the screen fits (research R8).
        val keyHeight = if (maxHeight < QSize.compactHeight && maxHeight != Dp.Infinity) QSize.keyHeightCompact else QSize.keyHeight
        Column(verticalArrangement = Arrangement.spacedBy(QSize.keypadGap)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(QSize.keypadGap)) {
                    row.forEach { d -> KeyCell(Modifier.weight(1f), keyHeight, enabled, onClick = { onDigit(d) }) { Digit(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(QSize.keypadGap)) {
                Box(Modifier.weight(1f).height(keyHeight), contentAlignment = Alignment.Center) { leftKey?.invoke() }
                KeyCell(Modifier.weight(1f), keyHeight, enabled, onClick = { onDigit(0) }) { Digit(0) }
                KeyCell(Modifier.weight(1f), keyHeight, enabled, onClick = onDelete) {
                    QIcon(Ph.Backspace, "Borrar", size = QSize.keyIcon, tint = Q.colors.tx)
                }
            }
        }
    }
}

/** A key with the keypad style; used by screens for the fingerprint slot (icon tinted by the caller). */
@Composable
fun QKey(
    modifier: Modifier = Modifier,
    height: Dp = QSize.keyHeight,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) = KeyCell(modifier.fillMaxWidth(), height, enabled, onClick, content)

@Composable
private fun Digit(d: Int) = Text(d.toString(), style = Q.type.key, color = Q.colors.tx)

@Composable
private fun KeyCell(
    modifier: Modifier,
    height: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) QMotion.KEY_PRESS_SCALE else 1f, tween(QMotion.PRESS_MS))
    Box(
        modifier
            .height(height)
            .scale(scale)
            .background(if (pressed || hovered) Q.colors.s1 else Color.Transparent, RoundedCornerShape(QRadius.sm))
            .hoverable(source, enabled)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
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
