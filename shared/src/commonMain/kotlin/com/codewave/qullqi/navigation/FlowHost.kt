package com.codewave.qullqi.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import com.codewave.qullqi.designsystem.QMotion

/**
 * Renders the top route of [navigator] with the reference transitions (ARCHITECTURE.md §6):
 * push enters from the right (+26 dp + fade), back from the left, replace cross-fades.
 * System back pops within the flow; at the root it is not consumed, so the activity finishes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <R : Any> FlowHost(navigator: FlowNavigator<R>, content: @Composable (R) -> Unit) {
    val stack by navigator.stack.collectAsState()
    val offsetPx = with(LocalDensity.current) { QMotion.pushOffset.roundToPx() }
    BackHandler(enabled = stack.size > 1) { navigator.back() }
    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            val time = tween<Float>(QMotion.PUSH_MS, easing = QMotion.pushEasing)
            when (navigator.lastDirection) {
                FlowNavigator.Direction.Forward ->
                    (slideInHorizontally(tween(QMotion.PUSH_MS, easing = QMotion.pushEasing)) { offsetPx } + fadeIn(time)) togetherWith fadeOut(tween(QMotion.PUSH_MS / 2))
                FlowNavigator.Direction.Back ->
                    (slideInHorizontally(tween(QMotion.PUSH_MS, easing = QMotion.pushEasing)) { -offsetPx } + fadeIn(time)) togetherWith fadeOut(tween(QMotion.PUSH_MS / 2))
                FlowNavigator.Direction.Replace ->
                    fadeIn(tween(QMotion.TAB_MS)) togetherWith fadeOut(tween(QMotion.TAB_MS))
            }.let { ContentTransform(it.targetContentEnter, it.initialContentExit) }
        },
        label = "flow",
    ) { route -> content(route) }
}
