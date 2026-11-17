package com.codewave.qullqi.feature.kyc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codewave.qullqi.AppDependencies
import com.codewave.qullqi.navigation.FlowHost
import com.codewave.qullqi.navigation.FlowNavigator

/**
 * M3 entry (spec 005), plugged into `AuthFlow(kycEntry = …)`. Own back stack inside `kycIntro`.
 * [onDone] returns to the main area (spec 003 hand-off). A rejected session renewal while verifying is
 * handled by AuthFlow for every auth screen (spec 005 T021a).
 */
@Composable
fun KycFlow(deps: AppDependencies, onDone: () -> Unit) {
    val vm = viewModel { KycViewModel(deps.kyc, deps.session, deps.pageOpener, deps.today) }
    val navigator = remember { FlowNavigator(KycScreen.Intro) }
    val state by vm.state.collectAsState()

    LaunchedEffect(vm) {
        vm.effects.collect { e ->
            when (e) {
                is KycEffect.Go -> when {
                    e.reset -> navigator.resetTo(e.screen)
                    navigator.current == e.screen -> Unit
                    e.screen == KycScreen.Intro -> if (!navigator.back()) navigator.resetTo(e.screen)
                    else -> navigator.push(e.screen)
                }
                KycEffect.Done -> onDone()
            }
        }
    }
    // Back from the provider page = AuthActivity resumes (research R2).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onEvent(KycEvent.Resumed) }

    FlowHost(navigator) { screen ->
        when (screen) {
            KycScreen.Loading, KycScreen.Intro -> KycIntroScreen(state, vm::onEvent)
            KycScreen.Document -> KycDocumentScreen(state, vm::onEvent)
            KycScreen.Waiting -> KycWaitingScreen(state, vm::onEvent)
            KycScreen.Result -> KycResultScreen(state, vm::onEvent)
        }
    }
}
