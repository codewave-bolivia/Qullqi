package com.codewave.qullqi.feature.auth

import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.LoginResult
import com.codewave.qullqi.domain.system.SampleOtpCodeGenerator
import com.codewave.qullqi.feature.MainDispatcherTest
import com.codewave.qullqi.feature.auth.recover.RecoverEffect
import com.codewave.qullqi.feature.auth.recover.RecoverEvent
import com.codewave.qullqi.feature.auth.recover.RecoverPinViewModel
import com.codewave.qullqi.feature.auth.recover.RecoverStep
import com.codewave.qullqi.feature.testEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecoverPinViewModelTest : MainDispatcherTest() {
    private val locked = UserId("usr-locked")

    private fun TestScope.type(vm: RecoverPinViewModel, digits: String) {
        digits.forEach { vm.onEvent(RecoverEvent.Digit(it.digitToInt())) }
        runCurrent()
    }

    @Test fun recoversLockedAccount() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        val vm = RecoverPinViewModel(locked, env.deps.registration, env.deps.session)
        runCurrent()
        assertEquals("+591 7••• ••05", vm.state.value.maskedPhone)
        assertEquals(RecoverStep.Code, vm.state.value.step)

        type(vm, SampleOtpCodeGenerator.CODE)
        assertEquals(RecoverStep.NewPin, vm.state.value.step)
        type(vm, "5678")
        assertEquals(RecoverStep.ConfirmPin, vm.state.value.step)
        type(vm, "1111")
        assertEquals(1, vm.state.value.shake)
        assertEquals(RecoverStep.ConfirmPin, vm.state.value.step)
        type(vm, "5678")

        assertEquals(RecoverEffect.Toast("PIN actualizado"), vm.effects.first())
        assertEquals(RecoverEffect.ToLogin(locked), vm.effects.first())
        assertEquals(LoginResult.Success, env.deps.session.login(locked, "5678"))
    }

    @Test fun wrongCodeShakes() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        val vm = RecoverPinViewModel(locked, env.deps.registration, env.deps.session)
        runCurrent()
        type(vm, "000000")
        assertEquals(RecoverStep.Code, vm.state.value.step)
        assertEquals(1, vm.state.value.shake)
        assertEquals("Código incorrecto. Te quedan 2 intentos.", vm.state.value.message)
    }
}
