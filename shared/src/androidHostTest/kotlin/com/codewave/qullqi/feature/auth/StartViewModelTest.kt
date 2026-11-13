package com.codewave.qullqi.feature.auth

import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.feature.MainDispatcherTest
import com.codewave.qullqi.feature.auth.start.StartEffect
import com.codewave.qullqi.feature.auth.start.StartEvent
import com.codewave.qullqi.feature.auth.start.StartViewModel
import com.codewave.qullqi.feature.testEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StartViewModelTest : MainDispatcherTest() {

    private fun vm(withSamples: Boolean = true, splash: Long = 1_900) =
        testEnv(dispatcher, withSamples).let { it to StartViewModel(it.deps.preferences, splashMillis = splash) }

    @Test fun routesToMostRecentAccountAfterSplash() = runTest(dispatcher) {
        val (_, vm) = vm()
        advanceTimeBy(1_000)
        assertNull(vm.state.value.routed)
        advanceUntilIdle()
        // No login yet: first account by name.
        assertEquals(StartEffect.ToLogin(UserId("usr-new")), vm.effects.first())
    }

    @Test fun routesToLastLoggedInAccount() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.deps.preferences.recordLogin(UserId("usr-review"))
        val vm = StartViewModel(env.deps.preferences, splashMillis = 0)
        advanceUntilIdle()
        assertEquals(StartEffect.ToLogin(UserId("usr-review")), vm.effects.first())
    }

    @Test fun emptyDeviceRoutesToWelcome() = runTest(dispatcher) {
        val (_, vm) = vm(withSamples = false, splash = 0)
        advanceUntilIdle()
        assertEquals(StartEffect.ToWelcome, vm.effects.first())
    }

    @Test fun haveAccountOnEmptyDeviceShowsMessage() = runTest(dispatcher) {
        val (_, vm) = vm(withSamples = false, splash = 0)
        advanceUntilIdle()
        vm.effects.first() // ToWelcome
        vm.onEvent(StartEvent.HaveAccount)
        advanceUntilIdle()
        assertEquals(StartEffect.Toast("No hay cuentas en este dispositivo. Crea una para empezar."), vm.effects.first())
    }

    @Test fun createAccountStartsRegistration() = runTest(dispatcher) {
        val (_, vm) = vm(splash = 0)
        advanceUntilIdle()
        vm.effects.first()
        vm.onEvent(StartEvent.CreateAccount)
        assertEquals(StartEffect.ToRegistration, vm.effects.first())
    }
}
