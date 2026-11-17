package com.codewave.qullqi.feature

import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.SimpleDate
import com.codewave.qullqi.domain.repository.KycRead
import com.codewave.qullqi.domain.repository.KycRepository
import com.codewave.qullqi.domain.repository.KycStart
import com.codewave.qullqi.domain.system.Today
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
    val deps = AppDependencies.createInMemory(
        db = t.db,
        hasher = Pbkdf2PinHasher(),
        clock = clock,
        ids = FakeIdGenerator(),
        otp = SampleOtpCodeGenerator,
        io = io,
        today = TEST_TODAY,
        kyc = FakeKycRepository(),
        biometrics = bio,
    )
    return TestEnv(t, deps, clock, bio)
}

/** Fixed "today" for age checks (spec 005). */
val TEST_TODAY = Today { SimpleDate(2026, 11, 17) }

/** Scriptable [KycRepository]: `reads` are consumed in order (the last one repeats). */
class FakeKycRepository(
    var reads: MutableList<KycRead> = mutableListOf(KycRead.Found(null)),
    var startResult: KycStart = KycStart.Started("https://provider.test/inq_1"),
) : KycRepository {
    var currentCalls = 0
        private set
    val starts = mutableListOf<Triple<DocumentType, String, SimpleDate>>()

    override suspend fun current(): KycRead {
        currentCalls++
        return if (reads.size > 1) reads.removeAt(0) else reads.first()
    }

    override suspend fun start(type: DocumentType, number: String, birthDate: SimpleDate): KycStart {
        starts += Triple(type, number, birthDate)
        return startResult
    }
}
