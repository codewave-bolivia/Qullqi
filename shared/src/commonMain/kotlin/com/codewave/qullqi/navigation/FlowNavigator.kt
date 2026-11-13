package com.codewave.qullqi.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ponytail: own back stack for the short per-activity flows (≤ 10 routes, linear). Switch to
// navigation-compose / Navigation 3 when M4 needs tabs with their own stacks or deep links.
class FlowNavigator<R : Any>(start: R) {

    enum class Direction { Forward, Back, Replace }

    private val _stack = MutableStateFlow(listOf(start))
    val stack: StateFlow<List<R>> = _stack.asStateFlow()

    var lastDirection: Direction = Direction.Replace
        private set

    val current: R get() = _stack.value.last()

    fun push(route: R) {
        lastDirection = Direction.Forward
        _stack.value = _stack.value + route
    }

    /** Replaces the top route (e.g. Login → after recovery). */
    fun replace(route: R) {
        lastDirection = Direction.Replace
        _stack.value = _stack.value.dropLast(1) + route
    }

    /** Clears the stack and starts again at [route]. */
    fun resetTo(route: R) {
        lastDirection = Direction.Replace
        _stack.value = listOf(route)
    }

    /** Pops one route; returns false at the root so the host can let the activity finish. */
    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        lastDirection = Direction.Back
        _stack.value = _stack.value.dropLast(1)
        return true
    }
}
