package com.codewave.qullqi.data.remote

import com.codewave.qullqi.data.remote.MockBackend.Companion.error
import com.codewave.qullqi.data.remote.MockBackend.Companion.me
import com.codewave.qullqi.data.remote.MockBackend.Companion.ok
import com.codewave.qullqi.data.remote.MockBackend.Companion.tokens
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.CodeConfirmResult
import com.codewave.qullqi.domain.repository.CodePurpose
import com.codewave.qullqi.domain.repository.CodeRequestResult
import com.codewave.qullqi.domain.repository.PinResetResult
import com.codewave.qullqi.domain.repository.RegistrationField
import com.codewave.qullqi.domain.repository.RegistrationForm
import com.codewave.qullqi.domain.repository.RegistrationResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** contracts/api-mapping.md rows: request/verify code, register, PIN reset (spec 004 US2, US5). */
class RemoteRegistrationRepositoryTest {
    private val form = RegistrationForm("70000001", " Lucía F ", "lucia@example.com", "2026-11", "1234", "1234")

    private fun RemoteEnv.confirmed(purpose: CodePurpose = CodePurpose.REGISTER) = runBlocking {
        backend.on("auth/verify-otp", 200, ok("""{"phoneVerifiedToken":"phone-token"}"""))
        assertEquals(CodeConfirmResult.Confirmed, registration.confirmCode("70000001", "123456", purpose))
    }

    @Test fun requestCodeMapping() = runBlocking {
        val env = RemoteEnv()
        env.backend.on("auth/request-otp", 200, ok("null"))
        assertEquals(CodeRequestResult.Sent, env.registration.requestCode("70000001"))
        assertTrue(env.backend.requests.last().body.contains("\"purpose\":\"PHONE_VERIFICATION\""))
        env.backend.on("auth/request-otp", 429, error("too_many_requests", """{"retry_after_seconds":12}"""))
        assertEquals(CodeRequestResult.TooSoon(12_000), env.registration.requestCode("70000001"))
        assertEquals(CodeRequestResult.InvalidPhone, env.registration.requestCode("12345678"))
        env.backend.offline = true
        assertEquals(CodeRequestResult.Unavailable, env.registration.requestCode("70000001"))
    }

    @Test fun confirmCodeMapping() = runBlocking {
        val env = RemoteEnv()
        env.backend.on("auth/verify-otp", 400, error("wrong_code", """{"tries_left":1}"""))
        assertEquals(CodeConfirmResult.Wrong(1), env.registration.confirmCode("70000001", "000000"))
        env.backend.on("auth/verify-otp", 410, error("code_expired"))
        assertEquals(CodeConfirmResult.Expired, env.registration.confirmCode("70000001", "000000"))
        env.backend.on("auth/verify-otp", 404, error("not_found"))
        assertEquals(CodeConfirmResult.NoCode, env.registration.confirmCode("70000001", "000000"))
    }

    @Test fun registerSendsNamesAndOpensSession() = runBlocking {
        val env = RemoteEnv()
        env.confirmed()
        env.backend.on("auth/register", 201, tokens(1))
        env.backend.on("me", 200, me())
        assertEquals(RegistrationResult.Created(UserId("u-1")), env.registration.register(form))
        val call = env.backend.requests.first { it.path == "auth/register" }
        assertEquals("Bearer phone-token", call.auth)
        assertTrue(call.body.contains("\"displayName\":\"Lucía F\"")) // trimmed
        assertFalse(call.body.contains("firstName") || call.body.contains("lastName")) // legal names come from KYC
        assertTrue(call.body.contains("\"termsVersion\":\"2026-11\"") && call.body.contains("\"deviceId\""))
        assertEquals("refresh-1", env.tokens.refreshToken(UserId("u-1")))
        // The phone proof is used once.
        assertEquals(RegistrationResult.PhoneNotConfirmed, env.registration.register(form))
    }

    @Test fun registerErrors() = runBlocking {
        val env = RemoteEnv()
        assertEquals(RegistrationResult.PhoneNotConfirmed, env.registration.register(form)) // no proof yet
        assertEquals(RegistrationResult.Invalid(setOf(RegistrationField.NAME)), env.registration.register(form.copy(displayName = " ")))
        env.confirmed()
        env.backend.on("auth/register", 409, error("phone_registered"))
        assertEquals(RegistrationResult.PhoneAlreadyRegistered, env.registration.register(form))
        env.backend.on("auth/register", 422, error("validation_failed", """{"fields":{"displayName":"blank"}}"""))
        assertEquals(RegistrationResult.Invalid(setOf(RegistrationField.NAME)), env.registration.register(form))
        env.backend.on("auth/register", 401, error("unauthorized"))
        assertEquals(RegistrationResult.PhoneNotConfirmed, env.registration.register(form))
    }

    @Test fun registerIsNeverRetriedAutomatically() = runBlocking {
        val env = RemoteEnv()
        env.confirmed()
        env.backend.offline = true
        assertEquals(RegistrationResult.Unavailable, env.registration.register(form))
        assertEquals(1, env.backend.count("auth/register"))
    }

    @Test fun pinResetMapping() = runBlocking {
        val env = RemoteEnv()
        env.tokens.saveRefreshToken(UserId("u-1"), "old")
        env.backend.on("auth/request-otp", 200, ok("null"))
        env.registration.requestCode("70000001", CodePurpose.PIN_RESET)
        assertTrue(env.backend.requests.last().body.contains("\"purpose\":\"PIN_RESET\""))
        assertEquals(PinResetResult.PhoneNotConfirmed, env.registration.resetPin("70000001", "5678")) // no proof
        env.confirmed(CodePurpose.PIN_RESET)
        assertEquals(PinResetResult.InvalidPin, env.registration.resetPin("70000001", "12a4"))
        env.backend.on("auth/pin/reset", 200, tokens(5))
        assertEquals(PinResetResult.Success, env.registration.resetPin("70000001", "5678"))
        val call = env.backend.requests.last()
        assertEquals("Bearer phone-token", call.auth)
        assertTrue(call.body.contains("\"newPin\":\"5678\""))

        env.confirmed(CodePurpose.PIN_RESET)
        env.backend.on("auth/pin/reset", 401, error("unauthorized"))
        assertEquals(PinResetResult.PhoneNotConfirmed, env.registration.resetPin("70000001", "5678"))
        env.confirmed(CodePurpose.PIN_RESET)
        env.backend.offline = true
        assertEquals(PinResetResult.Unavailable, env.registration.resetPin("70000001", "5678"))
    }

    @Test fun registerProofDoesNotServePinReset() = runBlocking {
        val env = RemoteEnv()
        env.confirmed(CodePurpose.REGISTER)
        assertEquals(PinResetResult.PhoneNotConfirmed, env.registration.resetPin("70000001", "5678"))
        assertNull(env.backend.requests.firstOrNull { it.path == "auth/pin/reset" })
    }
}
