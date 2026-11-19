package com.codewave.qullqi.feature.kyc

import com.codewave.qullqi.domain.model.DeclineReason
import com.codewave.qullqi.domain.model.KycStatus
import com.codewave.qullqi.domain.model.Verification
import com.codewave.qullqi.domain.repository.KycRead
import com.codewave.qullqi.domain.repository.KycStart
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.system.PageOpen
import com.codewave.qullqi.domain.system.ProviderPage
import com.codewave.qullqi.feature.FakeKycRepository
import com.codewave.qullqi.feature.MainDispatcherTest
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

/** Spec 005: routing, provider hand-off (no document form), polling and results. */
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
        /** Set to simulate the provider's return link ("Hecho"). */
        var finished = false
        override fun consumeFinished() = finished.also { finished = false }
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
        val vm = KycViewModel(kyc, session ?: env.deps.session, opener)
        runCurrent()
        return Setup(vm, kyc, opener)
    }

    private fun TestScope.begin(vm: KycViewModel) {
        vm.onEvent(KycEvent.AcceptPrivacy(true))
        vm.onEvent(KycEvent.Continue)
        runCurrent()
    }

    @Test fun privacyGatesTheStart() = runTest(dispatcher) {
        val s = setup()
        assertEquals(KycScreen.Intro, s.vm.state.value.screen)
        s.vm.onEvent(KycEvent.Continue)
        runCurrent()
        assertEquals(0, s.kyc.starts)
        begin(s.vm)
        assertEquals(1, s.kyc.starts) // no document form: continue creates the verification
    }

    @Test fun continueOpensTheProviderOnceThenPollsAfterReturn() = runTest(dispatcher) {
        val s = setup(reads = mutableListOf(KycRead.Found(null), found(KycStatus.PENDING), found(KycStatus.APPROVED)))
        begin(s.vm)
        assertEquals(listOf("https://provider.test/inq_1"), s.opener.urls)
        // Nothing navigates behind the opening provider page (T053).
        assertEquals(KycScreen.Intro, s.vm.state.value.screen)
        assertEquals(WaitPhase.InProvider, s.vm.state.value.phase)
        val readsBefore = s.kyc.currentCalls
        advanceTimeBy(30_000) // user in the provider: no polling yet
        assertEquals(readsBefore, s.kyc.currentCalls)
        s.vm.onEvent(KycEvent.Resumed)
        assertEquals(KycScreen.Waiting, s.vm.state.value.screen) // shown on return
        advanceUntilIdle()
        assertEquals(KycOutcome.Approved(already = false), s.vm.state.value.outcome)
    }

    @Test fun finishedInProviderKeepsCheckingWhileTheWebhookIsPending() = runTest(dispatcher) {
        // "Hecho" in Persona: the backend still says CREATED until the webhook arrives.
        val s = setup(
            reads = mutableListOf(
                KycRead.Found(null),
                found(KycStatus.CREATED, url = "https://provider.test/inq_1"),
                found(KycStatus.CREATED, url = "https://provider.test/inq_1"),
                found(KycStatus.APPROVED),
            ),
        )
        begin(s.vm)
        s.opener.finished = true
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        assertEquals(KycOutcome.Approved(already = false), s.vm.state.value.outcome)
    }

    @Test fun finishedInProviderWithoutWebhookEndsInReview() = runTest(dispatcher) {
        val s = setup(reads = mutableListOf(KycRead.Found(null), found(KycStatus.CREATED, url = "https://provider.test/inq_1")))
        begin(s.vm)
        s.opener.finished = true
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        assertEquals(KycOutcome.InReview, s.vm.state.value.outcome)
    }

    @Test fun declineReasonsFromTheDocument() = runTest(dispatcher) {
        val cases = mapOf(
            DeclineReason.DATA_MISMATCH to "Los datos no coinciden con tu documento.",
            DeclineReason.UNDERAGE to "Debes ser mayor de 18 años para verificar tu identidad.",
            DeclineReason.DOCUMENT_IN_USE to "Este documento ya está asociado a otra cuenta.",
        )
        cases.forEach { (reason, text) ->
            val s = setup(reads = mutableListOf(KycRead.Found(null), found(KycStatus.DECLINED, reason = reason)))
            begin(s.vm)
            s.vm.onEvent(KycEvent.Resumed)
            advanceUntilIdle()
            assertEquals(KycOutcome.Rejected(text), s.vm.state.value.outcome)
        }
    }

    @Test fun retryStartsANewVerificationFromTheIntro() = runTest(dispatcher) {
        val s = setup(reads = mutableListOf(KycRead.Found(null), found(KycStatus.DECLINED, reason = DeclineReason.DOCUMENT)))
        begin(s.vm)
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        s.vm.onEvent(KycEvent.Retry)
        assertEquals(KycScreen.Intro, s.vm.state.value.screen)
        assertNull(s.vm.state.value.outcome)
        s.vm.onEvent(KycEvent.Continue)
        runCurrent()
        assertEquals(2, s.kyc.starts)
    }

    @Test fun pendingForSixtySecondsIsInReview() = runTest(dispatcher) {
        val s = setup(reads = mutableListOf(KycRead.Found(null), found(KycStatus.PENDING)))
        begin(s.vm)
        val before = s.kyc.currentCalls
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        assertEquals(13, s.kyc.currentCalls - before) // t = 0, 5, …, 60 s
        assertEquals(KycOutcome.InReview, s.vm.state.value.outcome)
    }

    @Test fun startOfflineShowsMessageAndIsNotRetried() = runTest(dispatcher) {
        val s = setup(start = KycStart.Unavailable)
        begin(s.vm)
        advanceUntilIdle()
        assertEquals(1, s.kyc.starts)
        assertEquals(AuthMessages.UNAVAILABLE, s.vm.state.value.message)
        assertEquals(KycScreen.Intro, s.vm.state.value.screen)
        assertFalse(s.vm.state.value.busy)
    }

    @Test fun refusedAndAlreadyVerified() = runTest(dispatcher) {
        val refused = setup(start = KycStart.Refused)
        begin(refused.vm)
        assertEquals(KycOutcome.Refused, refused.vm.state.value.outcome)
        val verified = setup(start = KycStart.AlreadyVerified)
        begin(verified.vm)
        assertEquals(KycOutcome.Approved(already = true), verified.vm.state.value.outcome)
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
        begin(s.vm)
        assertEquals(listOf("https://provider.test/fresh"), s.opener.urls)
        assertEquals(0, s.kyc.starts) // resuming never creates a new verification
        s.vm.onEvent(KycEvent.Resumed)
        advanceUntilIdle()
        assertEquals(WaitPhase.NotFinished, s.vm.state.value.phase) // closed the WebView early
    }

    @Test fun inProgressOnStartResumes() = runTest(dispatcher) {
        val s = setup(
            reads = mutableListOf(KycRead.Found(null), found(KycStatus.CREATED, url = "https://provider.test/open")),
            start = KycStart.InProgress,
        )
        begin(s.vm)
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
        val effects = mutableListOf<KycEffect>()
        while (effects.lastOrNull() != KycEffect.Done) effects += s.vm.effects.first()
        assertEquals(listOf("refresh"), order)
    }

    @Test fun cameraDeniedKeepsTheUserAndRetriesWithTheSameUrl() = runTest(dispatcher) {
        val s = setup(pages = mutableListOf(PageOpen.CameraDenied(permanent = false), PageOpen.Opened))
        begin(s.vm)
        assertEquals(KycScreen.Intro, s.vm.state.value.screen)
        assertEquals(CameraDenied.Ask, s.vm.state.value.cameraDenied)
        s.vm.onEvent(KycEvent.AllowCamera)
        runCurrent()
        assertEquals(listOf("https://provider.test/inq_1", "https://provider.test/inq_1"), s.opener.urls)
        assertEquals(KycScreen.Intro, s.vm.state.value.screen)
        assertNull(s.vm.state.value.cameraDenied)
        assertEquals(1, s.kyc.starts) // the verification is not created twice
    }

    @Test fun cameraDeniedForeverSendsToSettings() = runTest(dispatcher) {
        val s = setup(pages = mutableListOf(PageOpen.CameraDenied(permanent = true)))
        begin(s.vm)
        assertEquals(CameraDenied.Settings, s.vm.state.value.cameraDenied)
        s.vm.onEvent(KycEvent.AllowCamera)
        assertEquals(1, s.opener.settingsOpened)
        assertEquals(1, s.opener.urls.size)
    }
}
