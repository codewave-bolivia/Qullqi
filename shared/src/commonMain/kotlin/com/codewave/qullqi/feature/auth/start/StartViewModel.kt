package com.codewave.qullqi.feature.auth.start

import androidx.lifecycle.viewModelScope
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.AccountPreferenceRepository
import com.codewave.qullqi.feature.FlowViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class StartState(
    /** Where the splash decided to go; null while the splash is still running. */
    val routed: StartEffect? = null,
)

sealed interface StartEvent {
    data object HaveAccount : StartEvent
    data object CreateAccount : StartEvent
}

sealed interface StartEffect {
    data class ToLogin(val userId: UserId) : StartEffect
    data object ToWelcome : StartEffect
    data object ToRegistration : StartEffect
    /** No account on this device: log in an existing backend account by phone (spec 004). */
    data object ToLoginPhone : StartEffect
}

/** Splash and welcome (US1, FR-001/FR-002): waits for the splash time and the most recent account. */
class StartViewModel(
    private val preferences: AccountPreferenceRepository,
    private val splashMillis: Long = QMotion.SPLASH_MS.toLong(),
) : FlowViewModel<StartState, StartEffect>(StartState()) {

    init {
        viewModelScope.launch {
            val account = async { preferences.mostRecentUser() }
            delay(splashMillis)
            val target = account.await()?.let { StartEffect.ToLogin(it) } ?: StartEffect.ToWelcome
            setState { copy(routed = target) }
            emit(target)
        }
    }

    fun onEvent(event: StartEvent) {
        when (event) {
            StartEvent.CreateAccount -> emit(StartEffect.ToRegistration)
            StartEvent.HaveAccount -> viewModelScope.launch {
                val id = preferences.mostRecentUser()
                emit(if (id != null) StartEffect.ToLogin(id) else StartEffect.ToLoginPhone)
            }
        }
    }
}
