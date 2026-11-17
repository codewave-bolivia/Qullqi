package com.codewave.qullqi.feature.auth

import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.domain.repository.RegistrationForm
import com.codewave.qullqi.domain.repository.RegistrationRepository
import com.codewave.qullqi.domain.repository.RegistrationResult
import com.codewave.qullqi.domain.system.SampleOtpCodeGenerator
import com.codewave.qullqi.feature.MainDispatcherTest
import com.codewave.qullqi.feature.TestEnv
import com.codewave.qullqi.feature.auth.register.RegEffect
import com.codewave.qullqi.feature.auth.register.RegEvent
import com.codewave.qullqi.feature.auth.register.RegistrationViewModel
import com.codewave.qullqi.feature.testEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistrationViewModelTest : MainDispatcherTest() {

    private fun TestScope.setup(withSamples: Boolean = false): Pair<TestEnv, RegistrationViewModel> {
        val env = testEnv(dispatcher, withSamples)
        val vm = RegistrationViewModel(env.deps.registration, env.deps.session, env.deps.preferences, env.deps.appLock)
        runCurrent()
        return env to vm
    }

    private fun TestScope.code(vm: RegistrationViewModel, value: String) {
        value.forEach { vm.onEvent(RegEvent.CodeDigit(it.digitToInt())) }
        runCurrent()
    }

    private fun TestScope.pin(vm: RegistrationViewModel, value: String) {
        value.forEach { vm.onEvent(RegEvent.PinDigit(it.digitToInt())) }
        runCurrent()
    }

    private suspend fun TestScope.toCodeStep(vm: RegistrationViewModel) {
        vm.onEvent(RegEvent.Phone("71234567"))
        vm.onEvent(RegEvent.SendCode)
        runCurrent()
        assertEquals(RegEffect.ToCode, vm.effects.first())
    }

    @Test fun phoneValidation() = runTest(dispatcher) {
        val (_, vm) = setup()
        vm.onEvent(RegEvent.Phone("5123"))
        assertFalse(vm.state.value.phoneValid)
        vm.onEvent(RegEvent.Phone("51234567"))
        assertFalse(vm.state.value.phoneValid)
        assertEquals("Debe tener 8 dígitos y empezar con 6 o 7.", vm.state.value.phoneHelp)
        vm.onEvent(RegEvent.Phone("7123abc4567999"))
        assertEquals("71234567", vm.state.value.phone)
        assertTrue(vm.state.value.phoneValid)
    }

    @Test fun existingPhoneGoesToLogin() = runTest(dispatcher) {
        val (_, vm) = setup(withSamples = true)
        vm.onEvent(RegEvent.Phone("70000001"))
        vm.onEvent(RegEvent.SendCode)
        runCurrent()
        assertEquals(RegEffect.Toast("Ya tienes una cuenta con este número. Ingresa con tu PIN."), vm.effects.first())
        assertEquals(RegEffect.ToLogin(UserId("usr-verified")), vm.effects.first())
    }

    @Test fun resendCountdown() = runTest(dispatcher) {
        val (env, vm) = setup()
        toCodeStep(vm)
        assertEquals(30, vm.state.value.resendSeconds)
        advanceTimeBy(10_001)
        assertEquals(20, vm.state.value.resendSeconds)
        advanceTimeBy(20_000)
        assertEquals(0, vm.state.value.resendSeconds)
        // Resend is allowed only after the countdown (the repository checks its own clock too).
        env.clock.advance(30_001)
        vm.onEvent(RegEvent.Resend)
        runCurrent()
        assertEquals(RegEffect.Toast("Te enviamos un nuevo código"), vm.effects.first())
        assertEquals(30, vm.state.value.resendSeconds)
    }

    @Test fun wrongCodeShakesAndCountsTries() = runTest(dispatcher) {
        val (_, vm) = setup()
        toCodeStep(vm)
        code(vm, "000000")
        assertEquals("", vm.state.value.code)
        assertEquals(1, vm.state.value.codeShake)
        assertEquals("Código incorrecto. Te quedan 2 intentos.", vm.state.value.codeMessage)
    }

    @Test fun pasteFillsCode() = runTest(dispatcher) {
        val (_, vm) = setup()
        toCodeStep(vm)
        vm.onEvent(RegEvent.PasteCode(" 12-34-56 "))
        runCurrent()
        assertEquals(RegEffect.ToInfo, vm.effects.first())
    }

    @Test fun infoValidation() = runTest(dispatcher) {
        val (_, vm) = setup()
        vm.onEvent(RegEvent.FirstName("Persona"))
        vm.onEvent(RegEvent.LastNamePaternal("Prueba"))
        vm.onEvent(RegEvent.Email("no-arroba"))
        assertFalse(vm.state.value.emailValid ?: true)
        assertEquals("Revisa el formato del correo.", vm.state.value.emailHelp)
        vm.onEvent(RegEvent.Email("persona@example.com"))
        assertFalse(vm.state.value.infoValid) // terms not accepted yet
        vm.onEvent(RegEvent.Terms(true))
        assertTrue(vm.state.value.infoValid)
    }

    @Test fun fullRegistration() = runTest(dispatcher) {
        val (env, vm) = setup()
        toCodeStep(vm)
        code(vm, SampleOtpCodeGenerator.CODE)
        assertEquals(RegEffect.ToInfo, vm.effects.first())
        vm.onEvent(RegEvent.FirstName("Persona"))
        vm.onEvent(RegEvent.LastNamePaternal("Prueba"))
        vm.onEvent(RegEvent.Email(" Persona@Example.com "))
        vm.onEvent(RegEvent.Terms(true))
        vm.onEvent(RegEvent.Continue)
        assertEquals(RegEffect.ToPin, vm.effects.first())

        pin(vm, "4321")
        assertEquals(1, vm.state.value.pinStep)
        pin(vm, "9999")
        assertEquals(1, vm.state.value.pinShake)
        assertEquals(0, vm.state.value.pinFilled)
        assertEquals("Los PIN no coinciden. Inténtalo de nuevo.", vm.state.value.pinMessage)

        pin(vm, "4321")
        advanceUntilIdle()
        assertEquals(RegEffect.ToDone, vm.effects.first())
        val user = env.deps.currentUser.currentUser.first()!!
        assertEquals(VerificationStatus.NOT_VERIFIED, user.verification)
        assertEquals("persona@example.com", user.email)
        assertFalse(env.deps.appLock.locked.value)
        assertIs<String>(vm.state.value.createdName)
    }

    @Test fun backKeepsDraft() = runTest(dispatcher) {
        val (_, vm) = setup()
        vm.onEvent(RegEvent.Phone("71234567"))
        vm.onEvent(RegEvent.FirstName("Persona"))
        // Navigating back does not touch the ViewModel: values stay.
        assertEquals("71234567", vm.state.value.phone)
        assertEquals("Persona", vm.state.value.firstName)
        assertNull(vm.state.value.createdName)
    }

    /** Fills steps 3 and 4 with valid data. */
    private suspend fun TestScope.infoAndPin(vm: RegistrationViewModel) {
        vm.onEvent(RegEvent.FirstName("Persona"))
        vm.onEvent(RegEvent.LastNamePaternal("Prueba"))
        vm.onEvent(RegEvent.Email("persona@example.com"))
        vm.onEvent(RegEvent.Terms(true))
        vm.onEvent(RegEvent.Continue)
        assertEquals(RegEffect.ToPin, vm.effects.first())
        pin(vm, "4321")
        pin(vm, "4321")
        advanceUntilIdle()
    }

    @Test fun nameFieldsValidation() = runTest(dispatcher) {
        val (_, vm) = setup()
        vm.onEvent(RegEvent.Email("persona@example.com"))
        vm.onEvent(RegEvent.Terms(true))
        vm.onEvent(RegEvent.FirstName("Persona"))
        assertFalse(vm.state.value.infoValid) // paternal surname required
        vm.onEvent(RegEvent.LastNamePaternal("Prueba"))
        assertTrue(vm.state.value.infoValid) // maternal surname optional
        vm.onEvent(RegEvent.LastNameMaternal("x".repeat(70)))
        assertEquals(60, vm.state.value.lastNameMaternal.length) // capped at 60
        vm.onEvent(RegEvent.FirstName("   "))
        assertFalse(vm.state.value.infoValid)
    }

    @Test fun expiredPhoneProofReturnsToCodeKeepingData() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        val expired = object : RegistrationRepository by env.deps.registration {
            override suspend fun register(form: RegistrationForm) = RegistrationResult.PhoneNotConfirmed
        }
        val vm = RegistrationViewModel(expired, env.deps.session, env.deps.preferences, env.deps.appLock)
        toCodeStep(vm)
        code(vm, SampleOtpCodeGenerator.CODE)
        assertEquals(RegEffect.ToInfo, vm.effects.first())
        infoAndPin(vm)
        assertEquals(RegEffect.Toast("Tu código venció. Confirma tu número otra vez."), vm.effects.first())
        assertEquals(RegEffect.ToCode, vm.effects.first())
        assertEquals("Persona", vm.state.value.firstName)
        assertEquals("Prueba", vm.state.value.lastNamePaternal)
    }

    @Test fun unavailableKeepsDataAndAllowsRetry() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        var calls = 0
        val offline = object : RegistrationRepository by env.deps.registration {
            override suspend fun register(form: RegistrationForm): RegistrationResult { calls++; return RegistrationResult.Unavailable }
        }
        val vm = RegistrationViewModel(offline, env.deps.session, env.deps.preferences, env.deps.appLock)
        toCodeStep(vm)
        code(vm, SampleOtpCodeGenerator.CODE)
        assertEquals(RegEffect.ToInfo, vm.effects.first())
        infoAndPin(vm)
        assertEquals(1, calls) // never retried automatically (FR-004)
        assertEquals(AuthMessages.UNAVAILABLE, vm.state.value.pinMessage)
        assertFalse(vm.state.value.creating)
        assertEquals("Persona", vm.state.value.firstName)
    }

    @Test fun loginInsteadFromCodeStep() = runTest(dispatcher) {
        val (_, vm) = setup()
        toCodeStep(vm)
        vm.onEvent(RegEvent.LoginInstead)
        assertEquals(RegEffect.ToLoginPhone("+59171234567"), vm.effects.first())
    }
}
