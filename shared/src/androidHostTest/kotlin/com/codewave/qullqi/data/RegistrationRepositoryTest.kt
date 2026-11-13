package com.codewave.qullqi.data

import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.model.VerificationStatus
import com.codewave.qullqi.domain.repository.CodeConfirmResult
import com.codewave.qullqi.domain.repository.CodeRequestResult
import com.codewave.qullqi.domain.repository.LoginResult
import com.codewave.qullqi.domain.repository.PinResetResult
import com.codewave.qullqi.domain.repository.RegistrationField
import com.codewave.qullqi.domain.repository.RegistrationForm
import com.codewave.qullqi.domain.repository.RegistrationResult
import com.codewave.qullqi.domain.system.SampleOtpCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RegistrationRepositoryTest {
    private val clock = FakeClock()
    private val hasher = Pbkdf2PinHasher()

    private fun setup(withSamples: Boolean = false): Triple<TestDb, SqlRegistrationRepository, SqlSessionRepository> {
        val t = testDatabase(withSamples)
        val session = SqlSessionRepository(t.db, hasher, clock, Dispatchers.Unconfined)
        val reg = SqlRegistrationRepository(t.db, hasher, clock, FakeIdGenerator(), SampleOtpCodeGenerator, Dispatchers.Unconfined)
        return Triple(t, reg, session)
    }

    private fun form(
        phone: String = "71234567",
        name: String = "Persona Prueba",
        email: String = "  Persona@Example.COM ",
        terms: String = "2026-01",
        pin: String = "4321",
        confirm: String = "4321",
    ) = RegistrationForm(phone, name, email, terms, pin, confirm)

    private val code = SampleOtpCodeGenerator.CODE

    @Test fun invalidPhoneRefused() = runBlocking {
        val (_, reg) = setup()
        assertEquals(CodeRequestResult.InvalidPhone, reg.requestCode("12345678"))
        assertEquals(CodeRequestResult.InvalidPhone, reg.requestCode("5123456"))
    }

    @Test fun resendWindowIs30Seconds() = runBlocking {
        val (_, reg) = setup()
        assertEquals(CodeRequestResult.Sent, reg.requestCode("71234567"))
        clock.advance(10_000)
        assertEquals(CodeRequestResult.TooSoon(20_000), reg.requestCode("71234567"))
        clock.advance(21_000)
        assertEquals(CodeRequestResult.Sent, reg.requestCode("71234567"))
    }

    @Test fun wrongCodesExhaustTries() = runBlocking {
        val (_, reg) = setup()
        reg.requestCode("71234567")
        assertEquals(CodeConfirmResult.Wrong(2), reg.confirmCode("71234567", "000000"))
        assertEquals(CodeConfirmResult.Wrong(1), reg.confirmCode("71234567", "000000"))
        assertEquals(CodeConfirmResult.Expired, reg.confirmCode("71234567", "000000"))
        assertEquals(CodeConfirmResult.Expired, reg.confirmCode("71234567", code))
    }

    @Test fun codeExpiresAfterFiveMinutes() = runBlocking {
        val (_, reg) = setup()
        reg.requestCode("71234567")
        clock.advance(6 * 60_000)
        assertEquals(CodeConfirmResult.Expired, reg.confirmCode("71234567", code))
    }

    @Test fun noCodeRequested() = runBlocking {
        val (_, reg) = setup()
        assertEquals(CodeConfirmResult.NoCode, reg.confirmCode("71234567", code))
    }

    @Test fun registrationNeedsConfirmedPhone() = runBlocking {
        val (_, reg) = setup()
        assertEquals(RegistrationResult.PhoneNotConfirmed, reg.register(form()))
    }

    @Test fun formValidation() = runBlocking {
        val (_, reg) = setup()
        assertEquals(RegistrationResult.Invalid(setOf(RegistrationField.PIN_MISMATCH)), reg.register(form(confirm = "9999")))
        assertEquals(RegistrationResult.Invalid(setOf(RegistrationField.EMAIL)), reg.register(form(email = "no-at-sign")))
        assertEquals(RegistrationResult.Invalid(setOf(RegistrationField.TERMS)), reg.register(form(terms = " ")))
        assertEquals(RegistrationResult.Invalid(setOf(RegistrationField.NAME)), reg.register(form(name = "  ")))
        assertEquals(RegistrationResult.Invalid(setOf(RegistrationField.PIN)), reg.register(form(pin = "12a4", confirm = "12a4")))
    }

    @Test fun successfulRegistration() = runBlocking {
        val (t, reg, session) = setup()
        reg.requestCode("71234567")
        assertEquals(CodeConfirmResult.Confirmed, reg.confirmCode("71234567", code))
        val result = reg.register(form())
        assertIs<RegistrationResult.Created>(result)
        val current = session.currentUser.first()!!
        assertEquals(result.userId, current.id)
        assertEquals("+59171234567", current.phone)
        assertEquals("persona@example.com", current.email)
        assertEquals(VerificationStatus.NOT_VERIFIED, current.verification)
        assertEquals(1, t.count("SELECT is_new FROM user WHERE id = '${result.userId.value}'"))
        assertEquals(1, t.count("SELECT count(*) FROM terms_acceptance WHERE terms_version = '2026-01'"))
        assertEquals(0, t.count("SELECT count(*) FROM credential WHERE pin_hash LIKE '%4321%' OR pin_salt LIKE '%4321%'"))
        session.logout()
        assertEquals(LoginResult.Success, session.login(result.userId, "4321"))
    }

    @Test fun duplicatePhoneRefused() = runBlocking {
        val (_, reg) = setup(withSamples = true)
        reg.requestCode("70000001")
        reg.confirmCode("70000001", code)
        assertEquals(RegistrationResult.PhoneAlreadyRegistered, reg.register(form(phone = "70000001")))
    }

    @Test fun resetPinUnlocks() = runBlocking {
        val (t, reg, session) = setup(withSamples = true)
        val locked = UserId("usr-locked")
        assertEquals(PinResetResult.PhoneNotConfirmed, reg.resetPin("70000005", "5678"))
        reg.requestCode("70000005")
        reg.confirmCode("70000005", code)
        assertEquals(PinResetResult.Success, reg.resetPin("70000005", "5678"))
        assertEquals(3, t.count("SELECT pin_attempts_left FROM user WHERE id = 'usr-locked'"))
        assertEquals(LoginResult.Success, session.login(locked, "5678"))
        assertFalse(session.login(locked, "1234") == LoginResult.Success)
        assertEquals(PinResetResult.UnknownPhone, reg.resetPin("79999999", "5678"))
    }
}
