package com.codewave.qullqi.feature.main

import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.feature.MainDispatcherTest
import com.codewave.qullqi.feature.testEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainPlaceholderViewModelTest : MainDispatcherTest() {

    @Test fun showsCurrentUserAndStatus() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.deps.session.login(UserId("usr-unverified"), "1234")
        val vm = MainPlaceholderViewModel(env.deps.currentUser, env.deps.session)
        advanceUntilIdle()
        assertEquals("Mateo Demo Choque", vm.state.value.name)
        assertEquals(VerificationStatus.NOT_VERIFIED, vm.state.value.verification)
    }

    @Test fun verifyOnlyWhenNotVerified() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.deps.session.login(UserId("usr-unverified"), "1234")
        val vm = MainPlaceholderViewModel(env.deps.currentUser, env.deps.session)
        advanceUntilIdle()
        vm.onEvent(MainEvent.Verify)
        assertEquals(MainEffect.OpenKyc, vm.effects.first())
    }

    @Test fun logoutAsksThenClosesSession() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        val id = UserId("usr-verified")
        env.deps.session.login(id, "1234")
        val vm = MainPlaceholderViewModel(env.deps.currentUser, env.deps.session)
        advanceUntilIdle()
        vm.onEvent(MainEvent.Logout)
        assertTrue(vm.state.value.confirmLogout)
        vm.onEvent(MainEvent.ConfirmLogout)
        advanceUntilIdle()
        assertEquals(MainEffect.LoggedOut(id), vm.effects.first())
        assertNull(env.deps.currentUser.currentUser.first())
    }
}
