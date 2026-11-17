package com.codewave.qullqi.data.remote

import com.codewave.qullqi.data.remote.MockBackend.Companion.error
import com.codewave.qullqi.data.remote.MockBackend.Companion.me
import com.codewave.qullqi.data.remote.MockBackend.Companion.ok
import com.codewave.qullqi.data.remote.MockBackend.Companion.tokens
import com.codewave.qullqi.domain.model.DeclineReason
import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.KycStatus
import com.codewave.qullqi.domain.model.SimpleDate
import com.codewave.qullqi.domain.model.Verification
import com.codewave.qullqi.domain.repository.KycField
import com.codewave.qullqi.domain.repository.KycRead
import com.codewave.qullqi.domain.repository.KycStart
import com.codewave.qullqi.domain.repository.LoginResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** contracts/api-mapping.md of spec 005: GET/POST my/kyc. */
class RemoteKycRepositoryTest {
    private val birth = SimpleDate(1992, 4, 15)

    private fun loggedIn() = RemoteEnv().apply {
        backend.on("auth/login", 200, tokens(1))
        backend.on("me", 200, me())
        runBlocking { assertEquals(LoginResult.Success, session.loginWithPhone("70000001", "1234")) }
    }

    private val RemoteEnv.kyc get() = RemoteKycRepository(api)

    private fun kycData(status: String, url: String? = null, reason: String? = null) =
        ok("""{"inquiryId":null,"kycStatus":"$status","hostedUrl":${url?.let { "\"$it\"" } ?: "null"},"declineReason":${reason?.let { "\"$it\"" } ?: "null"}}""")

    @Test fun currentMapsStatusAndReasons() = runBlocking {
        val env = loggedIn()
        env.backend.on("my/kyc", 200, ok("null"))
        assertEquals(KycRead.Found(null), env.kyc.current())
        env.backend.on("my/kyc", 200, kycData("CREATED", url = "https://p.test/1"))
        assertEquals(KycRead.Found(Verification(KycStatus.CREATED, "https://p.test/1", null)), env.kyc.current())
        env.backend.on("my/kyc", 200, kycData("DECLINED", reason = "data_mismatch"))
        assertEquals(DeclineReason.DATA_MISMATCH, ((env.kyc.current() as KycRead.Found).verification)!!.declineReason)
        env.backend.on("my/kyc", 200, kycData("DECLINED", reason = "document_expired"))
        assertEquals(DeclineReason.DOCUMENT, ((env.kyc.current() as KycRead.Found).verification)!!.declineReason)
        env.backend.on("my/kyc", 200, kycData("DECLINED", reason = "something_new"))
        assertEquals(DeclineReason.OTHER, ((env.kyc.current() as KycRead.Found).verification)!!.declineReason)
        env.backend.on("my/kyc", 200, kycData("WEIRD"))
        assertEquals(KycStatus.PENDING, ((env.kyc.current() as KycRead.Found).verification)!!.status)
        env.backend.offline = true
        assertEquals(KycRead.Unavailable, env.kyc.current())
    }

    @Test fun startSendsDocumentDataWithSession() = runBlocking {
        val env = loggedIn()
        env.backend.on("my/kyc", 200, kycData("PENDING", url = "https://p.test/2"))
        assertEquals(KycStart.Started("https://p.test/2"), env.kyc.start(DocumentType.CI, " 1234567 ", birth))
        val call = env.backend.requests.last()
        assertEquals("Bearer access-1", call.auth)
        assertTrue(call.body.contains("\"ciType\":\"CI\"") && call.body.contains("\"ciNumber\":\"1234567\""))
        assertTrue(call.body.contains("\"birthDate\":\"1992-04-15\""))
    }

    @Test fun startRefusals() = runBlocking {
        val env = loggedIn()
        fun on(status: Int, code: String, details: String? = null) = env.backend.on("my/kyc", status, error(code, details))
        on(409, "kyc_already_verified"); assertEquals(KycStart.AlreadyVerified, env.kyc.start(DocumentType.CI, "1234567", birth))
        on(409, "kyc_in_progress"); assertEquals(KycStart.InProgress, env.kyc.start(DocumentType.CI, "1234567", birth))
        on(409, "ci_taken"); assertEquals(KycStart.DocumentTaken, env.kyc.start(DocumentType.CI, "1234567", birth))
        on(403, "kyc_rejected"); assertEquals(KycStart.Refused, env.kyc.start(DocumentType.CI, "1234567", birth))
        on(422, "validation_failed", """{"fields":{"ciNumber":"bad"}}""")
        assertEquals(KycStart.Invalid(setOf(KycField.NUMBER)), env.kyc.start(DocumentType.CI, "1234567", birth))
        on(422, "validation_failed")
        assertEquals(KycStart.Invalid(setOf(KycField.BIRTH_DATE)), env.kyc.start(DocumentType.CI, "1234567", birth))
        env.backend.on("my/kyc", 200, kycData("PENDING"))
        assertEquals(KycStart.Unavailable, env.kyc.start(DocumentType.CI, "1234567", birth)) // no page
    }

    @Test fun startIsNeverRetried() = runBlocking {
        val env = loggedIn()
        env.backend.offline = true
        assertEquals(KycStart.Unavailable, env.kyc.start(DocumentType.PASSPORT, "P123456", birth))
        assertEquals(1, env.backend.count("my/kyc"))
    }
}
