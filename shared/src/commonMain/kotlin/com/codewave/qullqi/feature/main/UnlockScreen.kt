package com.codewave.qullqi.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codewave.qullqi.AppDependencies
import com.codewave.qullqi.feature.auth.login.LoginEffect
import com.codewave.qullqi.feature.auth.login.LoginMode
import com.codewave.qullqi.feature.auth.login.LoginScreen
import com.codewave.qullqi.feature.auth.login.LoginViewModel

/**
 * Re-authentication over MainActivity (FR-002a/b): login UI fixed to the current account. MainFlow's
 * content is not composed while this is shown, and comes back as it was after unlocking.
 */
@Composable
fun UnlockScreen(deps: AppDependencies, actions: MainActions) {
    val user by deps.currentUser.currentUser.collectAsState(initial = null)
    val id = user?.id
    if (id == null) {
        // No open session behind MainActivity (should not happen): go back to login for the latest account.
        LaunchedEffect(Unit) { deps.preferences.mostRecentUser()?.let(actions.requireLogin) }
        return
    }
    val vm = viewModel(key = "unlock-${id.value}") {
        LoginViewModel(id, LoginMode.Unlock, deps.session, deps.preferences, deps.biometrics, deps.appLock)
    }
    LaunchedEffect(vm) {
        vm.effects.collect { e ->
            when (e) {
                is LoginEffect.ToRecover -> actions.requireLogin(e.userId)
                LoginEffect.Unlocked, LoginEffect.EnterMain, LoginEffect.ToRegistration, LoginEffect.ToWelcome -> Unit
            }
        }
    }
    val state by vm.state.collectAsState()
    LoginScreen(state, vm::onEvent, unlock = true)
}
