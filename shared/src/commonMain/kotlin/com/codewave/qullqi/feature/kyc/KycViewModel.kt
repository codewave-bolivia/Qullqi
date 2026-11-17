package com.codewave.qullqi.feature.kyc

import androidx.lifecycle.viewModelScope
import com.codewave.qullqi.domain.model.DeclineReason
import com.codewave.qullqi.domain.model.DocumentType
import com.codewave.qullqi.domain.model.KycStatus
import com.codewave.qullqi.domain.model.SimpleDate
import com.codewave.qullqi.domain.model.Verification
import com.codewave.qullqi.domain.model.documentNumberValid
import com.codewave.qullqi.domain.repository.KycField
import com.codewave.qullqi.domain.repository.KycRead
import com.codewave.qullqi.domain.repository.KycRepository
import com.codewave.qullqi.domain.repository.KycStart
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.system.ExternalPageOpener
import com.codewave.qullqi.domain.system.Today
import com.codewave.qullqi.feature.FlowViewModel
import com.codewave.qullqi.feature.auth.AuthMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class KycScreen { Loading, Intro, Document, Waiting, Result }

enum class WaitPhase { InProvider, Checking, NotFinished, Offline }

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
    val documentType: DocumentType = DocumentType.CI,
    val documentNumber: String = "",
    /** 0–8 digits, shown as DD/MM/AAAA. */
    val birthDigits: String = "",
    val numberError: String? = null,
    val birthError: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val phase: WaitPhase = WaitPhase.InProvider,
    val outcome: KycOutcome? = null,
) {
    val documentReady: Boolean get() =
        documentNumberValid(documentNumber) && birthDigits.length == KycViewModel.BIRTH_DIGITS && !busy
}

sealed interface KycEvent {
    data class AcceptPrivacy(val accepted: Boolean) : KycEvent
    data object Continue : KycEvent
    data class Type(val type: DocumentType) : KycEvent
    data class Number(val value: String) : KycEvent
    data class BirthDigits(val value: String) : KycEvent
    data object Submit : KycEvent
    /** AuthActivity resumed: the user is back from the provider page (research R2). */
    data object Resumed : KycEvent
    data object CheckNow : KycEvent
    data object ReopenProvider : KycEvent
    data object Retry : KycEvent
    data object Finish : KycEvent
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
    private val opener: ExternalPageOpener,
    private val today: Today,
    private val pollEveryMillis: Long = POLL_EVERY_MS,
    private val pollForMillis: Long = POLL_FOR_MS,
) : FlowViewModel<KycState, KycEffect>(KycState()) {

    private var polling: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: KycEvent) {
        when (event) {
            is KycEvent.AcceptPrivacy -> setState { copy(privacyAccepted = event.accepted) }
            KycEvent.Continue -> if (current.privacyAccepted) {
                if (current.pendingResume) reopen() else go(KycScreen.Document)
            }
            is KycEvent.Type -> setState { copy(documentType = event.type, numberError = null) }
            is KycEvent.Number -> setState {
                copy(documentNumber = event.value.filter(Char::isLetterOrDigit).take(NUMBER_MAX), numberError = null, message = null)
            }
            is KycEvent.BirthDigits -> setState {
                copy(birthDigits = event.value.filter(Char::isDigit).take(BIRTH_DIGITS), birthError = null, message = null)
            }
            KycEvent.Submit -> submit()
            KycEvent.Resumed -> if (current.screen == KycScreen.Waiting && current.phase == WaitPhase.InProvider) poll()
            KycEvent.CheckNow -> poll()
            KycEvent.ReopenProvider -> reopen()
            KycEvent.Retry -> {
                setState { copy(documentNumber = "", birthDigits = "", numberError = null, birthError = null, message = null) }
                go(KycScreen.Document)
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

    private fun submit() {
        val s = current
        if (s.busy) return
        val birth = SimpleDate.parseDigits(s.birthDigits)
        val numberError = if (documentNumberValid(s.documentNumber)) null else NUMBER_INVALID
        val birthError = when {
            birth == null -> DATE_INVALID
            !birth.isAdultOn(today.date()) -> UNDER_AGE
            else -> null
        }
        if (numberError != null || birthError != null || birth == null) {
            setState { copy(numberError = numberError, birthError = birthError) }
            return
        }
        setState { copy(busy = true, message = null) }
        viewModelScope.launch {
            // Never retried automatically (FR-008).
            val r = kyc.start(s.documentType, s.documentNumber, birth)
            setState { copy(busy = false) }
            when (r) {
                is KycStart.Started -> openProvider(r.pageUrl)
                KycStart.AlreadyVerified -> result(KycOutcome.Approved(already = true))
                KycStart.InProgress -> reopen()
                KycStart.DocumentTaken -> setState { copy(numberError = DOCUMENT_TAKEN) }
                is KycStart.Invalid -> setState {
                    copy(
                        numberError = if (KycField.NUMBER in r.fields || KycField.TYPE in r.fields) NUMBER_INVALID else null,
                        birthError = if (KycField.BIRTH_DATE in r.fields) DATE_REFUSED else null,
                    )
                }
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

    private fun openProvider(url: String) {
        polling?.cancel()
        setState { copy(screen = KycScreen.Waiting, phase = WaitPhase.InProvider, message = null) }
        emit(KycEffect.Go(KycScreen.Waiting))
        opener.open(url)
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
        const val BIRTH_DIGITS = 8
        const val NUMBER_MAX = 15
        const val POLL_EVERY_MS = 5_000L
        const val POLL_FOR_MS = 60_000L

        const val NUMBER_INVALID = "Revisa el número de documento."
        const val DATE_INVALID = "Revisa la fecha."
        const val UNDER_AGE = "Debes ser mayor de 18 años para verificar tu identidad."
        const val DATE_REFUSED = "Revisa tu fecha de nacimiento."
        const val DOCUMENT_TAKEN = "Este documento ya está asociado a otra cuenta."

        /** FR-010 / research R7. */
        fun reasonText(reason: DeclineReason?): String = when (reason) {
            DeclineReason.DATA_MISMATCH -> "Los datos no coinciden con tu documento."
            DeclineReason.DOCUMENT -> "No pudimos leer tu documento o está vencido."
            DeclineReason.IDENTITY -> "No pudimos confirmar que eres tú."
            DeclineReason.OTHER, null -> "No pudimos verificar tu identidad."
        }
    }
}
