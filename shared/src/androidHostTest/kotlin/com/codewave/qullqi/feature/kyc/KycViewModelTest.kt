package com.codewave.qullqi.feature.kyc

import com.codewave.qullqi.domain.model.DeclineReason
import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.KycStatus
import com.codewave.qullqi.domain.model.SimpleDate
import com.codewave.qullqi.domain.model.Verification
import com.codewave.qullqi.domain.repository.KycField
import com.codewave.qullqi.domain.repository.KycRead
import com.codewave.qullqi.domain.repository.KycStart
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.system.PageOpen
import com.codewave.qullqi.domain.system.ProviderPage
import com.codewave.qullqi.feature.FakeKycRepository
import com.codewave.qullqi.feature.MainDispatcherTest
import com.codewave.qullqi.feature.TEST_TODAY
import com.codewave.qullqi.feature.auth.AuthMessages
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec 005: routing, validation, provider hand-off, polling and results. */
class KycViewModelTest : MainDispatcherTest() {

    /** Records opened URLs; `results` scripts the camera permission outcome (last one repeats). */
    private class Opener(val results: MutableList<PageOpen> = mutableListOf(PageOpen.Opened)) : ProviderPage {
        val urls = mutableListOf<String>()
        var settingsOpened = 0
        override suspend fun open(url: String): PageOpen {
            urls += url
            return if (results.size > 1) results.removeAt(0) else results.first()
        }
        override fun openCameraSettings() { settingsOpened++ }
    }

    private class Setup(val vm: KycViewModel, val kyc: FakeKycRepository, val opener: Opener)

    private fun found(status: KycStatus, url: String? = null, reason: DeclineReason? = null) =
        KycRead.Found(Verification(status, url, reason))

    private fun TestScope.setup(
        reads: MutableList<KycRead> = mutableListOf(KycRead.Found(null)),
        start: KycStart = KycStart.Started("https://provider.test/inq_1"),
        session: SessionRepository? = null,
        pages: MutableList<PageOpen> = mutableListOf(PageOpen.Opened),
    ): Setup {
        val env = testEnv(dispatcher)
        val kyc = FakeKycRepository(reads, start)
        val opener = Opener(pages)
        val vm = KycViewModel(kyc, session ?: env.deps.session, opener, TEST_TODAY)
        runCurrent()
        return Setup(vm, kyc, opener)
    }

    private fun KycViewModel.fill(number: String = "1234567", birth: String = "15041992") {
        onEvent(KycEvent.AcceptPrivacy(true))
        onEvent(KycEvent.Continue)
        onEvent(KycEvent.Number(number))
        onEvent(KycEvent.BirthDigits(birth))
    }

    @Test fun privacyGatesTheIntro() = runTest(dispatcher) {
        val s = setup()
        assertEquals(KycScreen.Intro, s.vm.state.value.screen)
        s.vm.onEvent(KycEvent.Continue)
        assertEquals(KycScreen.Intro, s.vm.state.value.screen)
        s.vm.onEvent(KycEvent.AcceptPrivacy(true))
        s.vm.onEvent(KycEvent.Continue)
        assertEquals(KycScreen.Document, s.vm.state.value.screen)
    }

    @Test fun invalidDataNeverReachesTheBackend() = runTest(dispatcher) {
        val s = setup()
        s.vm.fill(number = "12a")
        s.vm.onEvent(KycEvent.Submit)
        assertEquals(KycViewModel.NUMBER_INVALID, s.vm.state.value.numberError)
        s.vm.fill(birth = "31022000")
        s.vm.onEvent(KycEvent.Submit)
        assertEquals(KycViewModel.DATE_INVALID, s.vm.state.value.birthError)
        s.vm.fill(birth = "18112008") // 17 on 2026-11-17
        s.vm.onEvent(KycEvent.Submit)
        assertEquals(KycViewModel.UNDER_AGE, s.vm.state.value.birthError)
        runCurrent()
        assertTrue(s.kyc.starts.isEmpty())
    }

    @Test fun submitOpensTheProviderOnceThenPollsAfterReturn() = runTest(dispatcher) {
        val s = setup(reads = mutableListOf(KycRead.Found(null), found(KycStatus.PENDING), found(KycStatus.APPROVED)))
        s.vm.fill()
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        assertEquals(listOf(Triple(DocumentType.CI, "1234567", SimpleDate(1992, 4, 15))), s.kyc.starts)
        assertEquals(listOf("https://provider.test/inq_1"), s.opener.urls)
        assertEquals(KycScreen.Waiting, s.vm.state.value.screen)
        assertEquals(WaitPhase.InProvider, s.vm.state.value.phase)
        val readsBefore = s.kyc.currentCalls
        advanceTimeBy(30_000) // user in the provider: no polling yet
        assertEquals(readsBefore, s.kyc.currentCalls)
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        assertEquals(KycOutcome.Approved(already = false), s.vm.state.value.outcome)
    }

    @Test fun declinedShowsReasonAndRetryClearsFields() = runTest(dispatcher) {
        val s = setup(reads = mutableListOf(KycRead.Found(null), found(KycStatus.DECLINED, reason = DeclineReason.DATA_MISMATCH)))
        s.vm.fill()
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        assertEquals(KycOutcome.Rejected("Los datos no coinciden con tu documento."), s.vm.state.value.outcome)
        s.vm.onEvent(KycEvent.Retry)
        assertEquals(KycScreen.Document, s.vm.state.value.screen)
        assertEquals("", s.vm.state.value.documentNumber)
        assertEquals("", s.vm.state.value.birthDigits)
    }

    @Test fun pendingForSixtySecondsIsInReview() = runTest(dispatcher) {
        val s = setup(reads = mutableListOf(KycRead.Found(null), found(KycStatus.PENDING)))
        s.vm.fill()
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        val before = s.kyc.currentCalls
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        assertEquals(13, s.kyc.currentCalls - before) // t = 0, 5, …, 60 s
        assertEquals(KycOutcome.InReview, s.vm.state.value.outcome)
    }

    @Test fun startOfflineKeepsDataAndIsNotRetried() = runTest(dispatcher) {
        val s = setup(start = KycStart.Unavailable)
        s.vm.fill()
        s.vm.onEvent(KycEvent.Submit)
        advanceUntilIdle()
        assertEquals(1, s.kyc.starts.size)
        assertEquals(AuthMessages.UNAVAILABLE, s.vm.state.value.message)
        assertEquals("1234567", s.vm.state.value.documentNumber)
        assertFalse(s.vm.state.value.busy)
    }

    @Test fun backendFieldErrorsAndRefusals() = runTest(dispatcher) {
        val s = setup(start = KycStart.Invalid(setOf(KycField.BIRTH_DATE)))
        s.vm.fill()
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        assertEquals(KycViewModel.DATE_REFUSED, s.vm.state.value.birthError)
        assertEquals(KycScreen.Document, s.vm.state.value.screen)
        s.kyc.startResult = KycStart.DocumentTaken
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        assertEquals(KycViewModel.DOCUMENT_TAKEN, s.vm.state.value.numberError)
        s.kyc.startResult = KycStart.Refused
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        assertEquals(KycOutcome.Refused, s.vm.state.value.outcome)
    }

    @Test fun resumeUsesTheLatestPageAddress() = runTest(dispatcher) {
        val s = setup(
            reads = mutableListOf(
                found(KycStatus.CREATED, url = "https://provider.test/old"),
                found(KycStatus.CREATED, url = "https://provider.test/fresh"),
                found(KycStatus.CREATED, url = "https://provider.test/fresh"),
            ),
        )
        assertTrue(s.vm.state.value.pendingResume)
        s.vm.onEvent(KycEvent.AcceptPrivacy(true))
        s.vm.onEvent(KycEvent.Continue)
        runCurrent()
        assertEquals(listOf("https://provider.test/fresh"), s.opener.urls)
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        assertEquals(WaitPhase.NotFinished, s.vm.state.value.phase) // closed the tab early
    }

    @Test fun inProgressOnSubmitResumes() = runTest(dispatcher) {
        val s = setup(
            reads = mutableListOf(KycRead.Found(null), found(KycStatus.CREATED, url = "https://provider.test/open")),
            start = KycStart.InProgress,
        )
        s.vm.fill()
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        assertEquals(listOf("https://provider.test/open"), s.opener.urls)
    }

    @Test fun entryRoutesByStatus() = runTest(dispatcher) {
        assertEquals(KycOutcome.InReview, setup(reads = mutableListOf(found(KycStatus.PENDING))).vm.state.value.outcome)
        assertEquals(KycOutcome.Approved(already = true), setup(reads = mutableListOf(found(KycStatus.APPROVED))).vm.state.value.outcome)
        val declined = setup(reads = mutableListOf(found(KycStatus.DECLINED)))
        assertEquals(KycScreen.Intro, declined.vm.state.value.screen)
        assertNull(declined.vm.state.value.outcome)
    }

    @Test fun finishRefreshesTheUserBeforeLeaving() = runTest(dispatcher) {
        val env = testEnv(dispatcher)
        val order = mutableListOf<String>()
        val session = object : SessionRepository by env.deps.session {
            override suspend fun refreshCurrentUser() { order += "refresh" }
        }
        val s = setup(reads = mutableListOf(found(KycStatus.APPROVED)), session = session)
        s.vm.onEvent(KycEvent.Finish)
        runCurrent()
        // Go(Result) was emitted on entry; Done must come after the refresh.
        val effects = mutableListOf<KycEffect>()
        while (effects.lastOrNull() != KycEffect.Done) effects += s.vm.effects.first()
        assertEquals(listOf("refresh"), order)
    }

    @Test fun cameraDeniedKeepsTheUserAndRetriesWithTheSameUrl() = runTest(dispatcher) {
        val s = setup(pages = mutableListOf(PageOpen.CameraDenied(permanent = false), PageOpen.Opened))
        s.vm.fill()
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        assertEquals(KycScreen.Document, s.vm.state.value.screen)
        assertEquals(CameraDenied.Ask, s.vm.state.value.cameraDenied)
        s.vm.onEvent(KycEvent.AllowCamera)
        runCurrent()
        assertEquals(listOf("https://provider.test/inq_1", "https://provider.test/inq_1"), s.opener.urls)
        assertEquals(KycScreen.Waiting, s.vm.state.value.screen)
        assertNull(s.vm.state.value.cameraDenied)
        assertEquals(1, s.kyc.starts.size) // the verification is not created twice
    }

    @Test fun cameraDeniedForeverSendsToSettings() = runTest(dispatcher) {
        val s = setup(pages = mutableListOf(PageOpen.CameraDenied(permanent = true)))
        s.vm.fill()
        s.vm.onEvent(KycEvent.Submit)
        runCurrent()
        assertEquals(CameraDenied.Settings, s.vm.state.value.cameraDenied)
        s.vm.onEvent(KycEvent.AllowCamera)
        assertEquals(1, s.opener.settingsOpened)
        assertEquals(1, s.opener.urls.size)
    }
}
