package com.codewave.qullqi.feature

import com.codewave.qullqi.AppDependencies
import com.codewave.qullqi.data.FakeClock
import com.codewave.qullqi.data.FakeIdGenerator
import com.codewave.qullqi.data.Pbkdf2PinHasher
import com.codewave.qullqi.data.TestDb
import com.codewave.qullqi.data.testDatabase
import com.codewave.qullqi.domain.security.BiometricAuthenticator
import com.codewave.qullqi.domain.security.BiometricResult
import com.codewave.qullqi.domain.system.SampleOtpCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** Installs a test Main dispatcher so viewModelScope runs under the test scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    protected val dispatcher: TestDispatcher = StandardTestDispatcher()

    @BeforeTest fun setMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterTest fun resetMainDispatcher() = Dispatchers.resetMain()
}

class FakeBiometricAuthenticator(
    var available: Boolean = true,
    var nextResult: BiometricResult = BiometricResult.Success,
) : BiometricAuthenticator {
    var prompts = 0
        private set

    override fun isAvailable() = available

    override suspend fun authenticate(title: String, subtitle: String): BiometricResult {
        prompts++
        return nextResult
    }
}

class TestEnv(val t: TestDb, val deps: AppDependencies, val clock: FakeClock, val biometrics: FakeBiometricAuthenticator)

/** Real repositories on an in-memory database. Repository IO runs on [io] (the test dispatcher). */
fun testEnv(io: TestDispatcher, withSamples: Boolean = true): TestEnv {
    val t = testDatabase(withSamples)
    val clock = FakeClock()
    val bio = FakeBiometricAuthenticator()
    val deps = AppDependencies.create(
        db = t.db,
        hasher = Pbkdf2PinHasher(),
        clock = clock,
        ids = FakeIdGenerator(),
        otp = SampleOtpCodeGenerator,
        io = io,
        debug = false,
        biometrics = bio,
    )
    return TestEnv(t, deps, clock, bio)
}
