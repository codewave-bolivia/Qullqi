package com.codewave.qullqi.feature.auth

import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.security.BiometricResult
import com.codewave.qullqi.feature.MainDispatcherTest
import com.codewave.qullqi.feature.TestEnv
import com.codewave.qullqi.feature.auth.login.LoginEffect
import com.codewave.qullqi.feature.auth.login.LoginEvent
import com.codewave.qullqi.feature.auth.login.LoginMode
import com.codewave.qullqi.feature.auth.login.LoginStatus
import com.codewave.qullqi.feature.auth.login.LoginViewModel
import com.codewave.qullqi.feature.testEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginViewModelTest : MainDispatcherTest() {

    private val verified = UserId("usr-verified")

    private fun TestScope.setup(user: UserId? = verified, mode: LoginMode = LoginMode.Login, sensor: Boolean = false): Pair<TestEnv, LoginViewModel> {
        val env = testEnv(dispatcher)
        env.biometrics.available = sensor
        val vm = LoginViewModel(user, mode, env.deps.session, env.deps.preferences, env.deps.biometrics, env.deps.appLock, enteringMillis = 0)
        advanceUntilIdle()
        return env to vm
    }

    private fun TestScope.type(vm: LoginViewModel, pin: String) {
        pin.forEach { vm.onEvent(LoginEvent.Digit(it.digitToInt())) }
        advanceUntilIdle()
    }

    @Test fun showsTargetAccount() = runTest(dispatcher) {
        val (_, vm) = setup()
        assertEquals("Lucía", vm.state.value.firstName)
        assertEquals(LoginStatus.Idle, vm.state.value.status)
    }

    @Test fun nullTargetUsesMostRecentAccount() = runTest(dispatcher) {
        val (_, vm) = setup(user = null)
        assertEquals("usr-new", vm.state.value.account?.id?.value)
    }

    @Test fun correctPinEntersAndUnlocks() = runTest(dispatcher) {
        val (env, vm) = setup()
        type(vm, "1234")
        assertEquals(LoginEffect.EnterMain, vm.effects.first())
        assertFalse(env.deps.appLock.locked.value)
        assertEquals("usr-verified", env.deps.preferences.mostRecentUser()?.value)
    }

    @Test fun wrongPinsShakeCountDownAndLock() = runTest(dispatcher) {
        val (env, vm) = setup()
        type(vm, "0000")
        assertEquals(LoginStatus.Wrong(2), vm.state.value.status)
        assertEquals("Te quedan 2 intentos.", vm.state.value.message)
        assertEquals(0, vm.state.value.filled)
        assertEquals(1, vm.state.value.shakeCount)
        type(vm, "0000")
        assertEquals("Te quedan 1 intento.", vm.state.value.message)
        type(vm, "0000")
        assertEquals(LoginStatus.Locked, vm.state.value.status)
        assertEquals("Bloqueamos el acceso por seguridad. Recupera tu PIN.", vm.state.value.message)
        assertTrue(env.deps.appLock.locked.value)
    }

    @Test fun lockedAccountOpensLocked() = runTest(dispatcher) {
        val (_, vm) = setup(UserId("usr-locked"))
        assertEquals(LoginStatus.Locked, vm.state.value.status)
        vm.onEvent(LoginEvent.Recover)
        assertEquals(LoginEffect.ToRecover(UserId("usr-locked")), vm.effects.first())
    }

    @Test fun switchAccountListsMaskedAccounts() = runTest(dispatcher) {
        val (_, vm) = setup()
        vm.onEvent(LoginEvent.SwitchAccount)
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.showAccounts)
        assertEquals(6, s.accounts.size)
        assertEquals("+591 7••• ••01", s.accounts.first { it.id == verified }.maskedPhone)
        vm.onEvent(LoginEvent.PickAccount(UserId("usr-review")))
        advanceUntilIdle()
        assertEquals("Valeria", vm.state.value.firstName)
        assertFalse(vm.state.value.showAccounts)
    }

    @Test fun goToWelcomeFromAccountsSheet() = runTest(dispatcher) {
        val (_, vm) = setup()
        vm.onEvent(LoginEvent.SwitchAccount)
        advanceUntilIdle()
        vm.onEvent(LoginEvent.GoToWelcome)
        assertFalse(vm.state.value.showAccounts)
        assertEquals(LoginEffect.ToWelcome, vm.effects.first())
    }

    @Test fun addAccountStartsRegistration() = runTest(dispatcher) {
        val (_, vm) = setup()
        vm.onEvent(LoginEvent.AddAccount)
        assertEquals(LoginEffect.ToRegistration, vm.effects.first())
    }

    @Test fun deleteAndPartialEntryNeverExposeDigits() = runTest(dispatcher) {
        val (_, vm) = setup()
        vm.onEvent(LoginEvent.Digit(1))
        vm.onEvent(LoginEvent.Digit(2))
        vm.onEvent(LoginEvent.Delete)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.filled)
        assertFalse(vm.state.value.toString().contains("12"))
    }

    // --- US5: biometrics -------------------------------------------------------------

    private fun TestScope.attempts(env: TestEnv, id: String) = env.t.count("SELECT pin_attempts_left FROM user WHERE id = '$id'")

    @Test fun fingerprintKeyHiddenUntilEnabled() = runTest(dispatcher) {
        val (_, vm) = setup()
        assertFalse(vm.state.value.biometricKeyVisible)
    }

    @Test fun fingerprintKeyHiddenWhenDeviceUnavailableOrLocked() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.deps.preferences.setBiometrics(verified, true)
        env.deps.preferences.setBiometrics(UserId("usr-locked"), true)
        env.biometrics.available = false
        val noSensor = LoginViewModel(verified, LoginMode.Login, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        assertFalse(noSensor.state.value.biometricKeyVisible)
        env.biometrics.available = true
        val locked = LoginViewModel(UserId("usr-locked"), LoginMode.Login, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        assertFalse(locked.state.value.biometricKeyVisible)
    }

    @Test fun fingerprintSuccessEntersWithoutUsingAttempts() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.deps.preferences.setBiometrics(verified, true)
        val vm = LoginViewModel(verified, LoginMode.Login, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        assertTrue(vm.state.value.biometricKeyVisible)
        vm.onEvent(LoginEvent.Fingerprint)
        advanceUntilIdle()
        assertEquals(LoginEffect.EnterMain, vm.effects.first())
        assertEquals(3, attempts(env, "usr-verified"))
        assertEquals(1, env.biometrics.prompts)
    }

    @Test fun fingerprintCancelOrFailKeepsPinUsable() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.deps.preferences.setBiometrics(verified, true)
        val vm = LoginViewModel(verified, LoginMode.Login, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        for (r in listOf(BiometricResult.Cancelled, BiometricResult.Failed)) {
            env.biometrics.nextResult = r
            vm.onEvent(LoginEvent.Fingerprint)
            advanceUntilIdle()
            assertEquals(LoginStatus.Idle, vm.state.value.status)
            assertEquals(3, attempts(env, "usr-verified"))
        }
        type(vm, "1234")
        assertEquals(LoginEffect.EnterMain, vm.effects.first())
    }

    @Test fun enableBiometricsOfferedOnceAfterPinLogin() = runTest(dispatcher) {
        val (env, vm) = setup(sensor = true)
        type(vm, "1234")
        assertTrue(vm.state.value.showEnableBiometrics)
        vm.onEvent(LoginEvent.EnableBiometrics(true))
        advanceUntilIdle()
        assertEquals(LoginEffect.EnterMain, vm.effects.first())
        val pref = env.deps.preferences.preference(verified)
        assertTrue(pref.biometricsEnabled)
        assertTrue(pref.biometricsPrompted)

        // Declining is remembered too: no second offer.
        val other = UserId("usr-review")
        val vm2 = LoginViewModel(other, LoginMode.Login, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        type(vm2, "1234")
        vm2.onEvent(LoginEvent.EnableBiometrics(false))
        advanceUntilIdle()
        assertEquals(LoginEffect.EnterMain, vm2.effects.first())
        assertFalse(env.deps.preferences.preference(other).biometricsEnabled)
        assertTrue(env.deps.preferences.preference(other).biometricsPrompted)
        val vm3 = LoginViewModel(other, LoginMode.Login, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        type(vm3, "1234")
        assertFalse(vm3.state.value.showEnableBiometrics)
        assertEquals(LoginEffect.EnterMain, vm3.effects.first())
    }

    @Test fun noOfferWithoutSensor() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.biometrics.available = false
        val vm = LoginViewModel(verified, LoginMode.Login, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        type(vm, "1234")
        assertFalse(vm.state.value.showEnableBiometrics)
        assertEquals(LoginEffect.EnterMain, vm.effects.first())
    }

    // --- US6: unlock mode (MainActivity, 5-minute lock) ---------------------------------

    @Test fun unlockModeUnlocksWithoutChangingUser() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.deps.session.login(verified, "1234")
        val vm = LoginViewModel(verified, LoginMode.Unlock, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        assertTrue(env.deps.appLock.locked.value)
        type(vm, "1234")
        assertEquals(LoginEffect.Unlocked, vm.effects.first())
        assertFalse(env.deps.appLock.locked.value)
        assertEquals(verified, env.deps.currentUser.currentUser.first()?.id)
        assertFalse(vm.state.value.showEnableBiometrics)
    }

    @Test fun unlockModeSharesAttemptCounter() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        env.deps.session.login(verified, "0000") // one wrong attempt at login
        val vm = LoginViewModel(verified, LoginMode.Unlock, env.deps.session, env.deps.preferences, env.biometrics, env.deps.appLock, 0)
        advanceUntilIdle()
        type(vm, "0000")
        assertEquals(LoginStatus.Wrong(1), vm.state.value.status)
        type(vm, "0000")
        assertEquals(LoginStatus.Locked, vm.state.value.status)
        vm.onEvent(LoginEvent.Recover)
        assertEquals(LoginEffect.ToRecover(verified), vm.effects.first())
    }
}
