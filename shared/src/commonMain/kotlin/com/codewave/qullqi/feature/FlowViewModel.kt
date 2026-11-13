package com.codewave.qullqi.feature

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * MVVM base (constitution II): immutable [state] for rendering, one-off [effects] for navigation,
 * toasts and shakes. Screens never mutate state directly; they send events to the ViewModel.
 */
abstract class FlowViewModel<S, E>(initial: S) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val current: S get() = _state.value

    protected fun setState(transform: S.() -> S) = _state.update(transform)

    protected fun emit(effect: E) {
        _effects.trySend(effect)
    }
}
