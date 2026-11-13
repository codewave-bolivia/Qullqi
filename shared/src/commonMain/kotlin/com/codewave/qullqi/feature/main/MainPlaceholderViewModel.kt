package com.codewave.qullqi.feature.main

import androidx.lifecycle.viewModelScope
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.domain.repository.CurrentUserSource
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.feature.FlowViewModel
import kotlinx.coroutines.launch

data class MainState(
    val userId: UserId? = null,
    val name: String = "",
    val verification: VerificationStatus = VerificationStatus.NOT_VERIFIED,
    val confirmLogout: Boolean = false,
)

sealed interface MainEvent {
    data object Verify : MainEvent
    data object Logout : MainEvent
    data object DismissLogout : MainEvent
    data object ConfirmLogout : MainEvent
}

sealed interface MainEffect {
    data object OpenKyc : MainEffect
    data class LoggedOut(val userId: UserId) : MainEffect
}

/**
 * Temporary main area (US6) until M4 replaces MainFlow's content. Uses only the M4 contract
 * ([CurrentUserSource]) plus logout.
 */
class MainPlaceholderViewModel(
    currentUser: CurrentUserSource,
    private val session: SessionRepository,
) : FlowViewModel<MainState, MainEffect>(MainState()) {

    init {
        viewModelScope.launch {
            currentUser.currentUser.collect { u ->
                if (u != null) setState { copy(userId = u.id, name = u.displayName, verification = u.verification) }
            }
        }
    }

    fun onEvent(event: MainEvent) {
        when (event) {
            MainEvent.Verify -> if (current.verification == VerificationStatus.NOT_VERIFIED) emit(MainEffect.OpenKyc)
            MainEvent.Logout -> setState { copy(confirmLogout = true) }
            MainEvent.DismissLogout -> setState { copy(confirmLogout = false) }
            MainEvent.ConfirmLogout -> {
                val id = current.userId ?: return
                setState { copy(confirmLogout = false) }
                viewModelScope.launch {
                    session.logout()
                    emit(MainEffect.LoggedOut(id))
                }
            }
        }
    }
}
