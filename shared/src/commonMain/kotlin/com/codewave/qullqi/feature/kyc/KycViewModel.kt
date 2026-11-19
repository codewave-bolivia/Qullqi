package com.codewave.qullqi.feature.kyc

import androidx.lifecycle.viewModelScope
import com.codewave.qullqi.domain.model.DeclineReason
import com.codewave.qullqi.domain.model.KycStatus
import com.codewave.qullqi.domain.model.Verification
import com.codewave.qullqi.domain.repository.KycRead
import com.codewave.qullqi.domain.repository.KycRepository
import com.codewave.qullqi.domain.repository.KycStart
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.system.PageOpen
import com.codewave.qullqi.domain.system.ProviderPage
import com.codewave.qullqi.feature.FlowViewModel
import com.codewave.qullqi.feature.auth.AuthMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class KycScreen { Loading, Intro, Waiting, Result }

enum class WaitPhase { InProvider, Checking, NotFinished, Offline }

/** Camera permission denied (FR-005b): ask again, or only the system settings can grant it. */
enum class CameraDenied { Ask, Settings }

sealed interface KycOutcome {
    data class Approved(val already: Boolean) : KycOutcome
    data object InReview : KycOutcome
    data class Rejected(val reason: String) : KycOutcome
    data object Refused : KycOutcome
}

data class KycState(
    val screen: KycScreen = KycScreen.Loading,
    /** A verification was started and not finished in the provider (spec 005 US2). */
    val pendingResume: Boolean = false,
    val privacyAccepted: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val phase: WaitPhase = WaitPhase.InProvider,
    val outcome: KycOutcome? = null,
    val cameraDenied: CameraDenied? = null,
)

sealed interface KycEvent {
    data class AcceptPrivacy(val accepted: Boolean) : KycEvent
    data object Continue : KycEvent
    /** AuthActivity resumed: the user is back from the provider page (research R2). */
    data object Resumed : KycEvent
    data object CheckNow : KycEvent
    data object ReopenProvider : KycEvent
    data object Retry : KycEvent
    data object Finish : KycEvent
    /** "Permitir cámara" after a denial. */
    data object AllowCamera : KycEvent
}

sealed interface KycEffect {
    /** [reset] clears the flow's back stack (results cannot go back to the waiting screen). */
    data class Go(val screen: KycScreen, val reset: Boolean = false) : KycEffect
    data object Done : KycEffect
}

/**
 * M3 identity verification (spec 005): intro → document → provider page → waiting → result.
 * The provider (Persona) captures the document; the backend owns the status.
 */
class KycViewModel(
    private val kyc: KycRepository,
    private val session: SessionRepository,
    private val providerPage: ProviderPage,
    private val pollEveryMillis: Long = POLL_EVERY_MS,
    private val pollForMillis: Long = POLL_FOR_MS,
) : FlowViewModel<KycState, KycEffect>(KycState()) {

    private var polling: Job? = null

    /** Provider page waiting for the camera permission; memory only, never stored or logged. */
    private var pendingUrl: String? = null

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: KycEvent) {
        when (event) {
            is KycEvent.AcceptPrivacy -> setState { copy(privacyAccepted = event.accepted) }
            KycEvent.Continue -> if (current.privacyAccepted) {
                if (current.pendingResume) reopen() else start()
            }
            KycEvent.Resumed -> if (current.screen == KycScreen.Waiting && current.phase == WaitPhase.InProvider) poll()
            KycEvent.CheckNow -> poll()
            KycEvent.ReopenProvider -> reopen()
            // A new verification; the provider reads the document again.
            KycEvent.Retry -> {
                setState { copy(screen = KycScreen.Intro, pendingResume = false, outcome = null, message = null) }
                emit(KycEffect.Go(KycScreen.Intro, reset = true))
            }
            KycEvent.AllowCamera -> when (current.cameraDenied) {
                CameraDenied.Settings -> providerPage.openCameraSettings()
                CameraDenied.Ask -> pendingUrl?.let(::openProvider)
                null -> Unit
            }
            KycEvent.Finish -> viewModelScope.launch {
                setState { copy(busy = true) }
                session.refreshCurrentUser()
                emit(KycEffect.Done)
            }
        }
    }

    /** FR-002: route by the backend's current verification. */
    private suspend fun load() {
        when (val r = kyc.current()) {
            is KycRead.Found -> route(r.verification)
            KycRead.Unavailable -> {
                setState { copy(screen = KycScreen.Intro, message = AuthMessages.UNAVAILABLE) }
                emit(KycEffect.Go(KycScreen.Intro, reset = true))
            }
        }
    }

    private fun route(v: Verification?) {
        when (v?.status) {
            null, KycStatus.DECLINED -> {
                setState { copy(screen = KycScreen.Intro, pendingResume = false) }
                emit(KycEffect.Go(KycScreen.Intro, reset = true))
            }
            KycStatus.CREATED -> {
                setState { copy(screen = KycScreen.Intro, pendingResume = true) }
                emit(KycEffect.Go(KycScreen.Intro, reset = true))
            }
            KycStatus.PENDING -> result(KycOutcome.InReview)
            KycStatus.APPROVED -> result(KycOutcome.Approved(already = true))
        }
    }

    /** FR-003/FR-005: no document data; the provider reads it. Never retried automatically (FR-008). */
    private fun start() {
        if (current.busy) return
        setState { copy(busy = true, message = null) }
        viewModelScope.launch {
            val r = kyc.start()
            setState { copy(busy = false) }
            when (r) {
                is KycStart.Started -> openProvider(r.pageUrl)
                KycStart.AlreadyVerified -> result(KycOutcome.Approved(already = true))
                KycStart.InProgress -> reopen()
                KycStart.Refused -> result(KycOutcome.Refused)
                KycStart.Unavailable -> setState { copy(message = AuthMessages.UNAVAILABLE) }
            }
        }
    }

    /** Resume: always the latest page address from the backend (research R6, backend H1). */
    private fun reopen() {
        if (current.busy) return
        setState { copy(busy = true, message = null) }
        viewModelScope.launch {
            val r = kyc.current()
            setState { copy(busy = false) }
            val v = (r as? KycRead.Found)?.verification
            when {
                r is KycRead.Unavailable -> setState { copy(message = AuthMessages.UNAVAILABLE) }
                v?.status == KycStatus.CREATED && v.pageUrl != null -> openProvider(v.pageUrl)
                else -> route(v)
            }
        }
    }

    /** FR-005b: the page opens only with the camera allowed; otherwise stay and explain. */
    private fun openProvider(url: String) {
        polling?.cancel()
        pendingUrl = url
        viewModelScope.launch {
            when (val r = providerPage.open(url)) {
                PageOpen.Opened -> {
                    pendingUrl = null
                    setState { copy(screen = KycScreen.Waiting, phase = WaitPhase.InProvider, message = null, cameraDenied = null) }
                    emit(KycEffect.Go(KycScreen.Waiting))
                }
                is PageOpen.CameraDenied -> setState {
                    copy(cameraDenied = if (r.permanent) CameraDenied.Settings else CameraDenied.Ask)
                }
            }
        }
    }

    /** Research R3: read now, then every 5 s for up to 60 s; pending at the end = in review. */
    private fun poll() {
        polling?.cancel()
        setState { copy(phase = WaitPhase.Checking, message = null) }
        polling = viewModelScope.launch {
            var elapsed = 0L
            var reached = false
            while (true) {
                val r = kyc.current()
                if (r is KycRead.Found) {
                    reached = true
                    when (r.verification?.status) {
                        KycStatus.APPROVED -> return@launch result(KycOutcome.Approved(already = false))
                        KycStatus.DECLINED -> return@launch result(KycOutcome.Rejected(reasonText(r.verification.declineReason)))
                        KycStatus.CREATED -> return@launch setState { copy(phase = WaitPhase.NotFinished) }
                        KycStatus.PENDING, null -> Unit
                    }
                }
                if (elapsed >= pollForMillis) break
                delay(pollEveryMillis)
                elapsed += pollEveryMillis
            }
            if (reached) result(KycOutcome.InReview) else setState { copy(phase = WaitPhase.Offline, message = AuthMessages.UNAVAILABLE) }
        }
    }

    private fun result(outcome: KycOutcome) {
        polling?.cancel()
        setState { copy(screen = KycScreen.Result, outcome = outcome, busy = false) }
        emit(KycEffect.Go(KycScreen.Result, reset = true))
    }

    private fun go(screen: KycScreen) {
        setState { copy(screen = screen) }
        emit(KycEffect.Go(screen))
    }

    companion object {
        const val POLL_EVERY_MS = 5_000L
        const val POLL_FOR_MS = 60_000L

        /** FR-010 / research R7. */
        fun reasonText(reason: DeclineReason?): String = when (reason) {
            DeclineReason.DATA_MISMATCH -> "Los datos no coinciden con tu documento."
            DeclineReason.DOCUMENT -> "No pudimos leer tu documento o está vencido."
            DeclineReason.IDENTITY -> "No pudimos confirmar que eres tú."
            DeclineReason.UNDERAGE -> "Debes ser mayor de 18 años para verificar tu identidad."
            DeclineReason.DOCUMENT_IN_USE -> "Este documento ya está asociado a otra cuenta."
            DeclineReason.OTHER, null -> "No pudimos verificar tu identidad."
        }
    }
}
