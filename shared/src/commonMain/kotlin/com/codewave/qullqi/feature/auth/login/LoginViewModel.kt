package com.codewave.qullqi.feature.auth.login

import com.codewave.qullqi.feature.auth.AuthMessages
import androidx.lifecycle.viewModelScope
import com.codewave.qullqi.designsystem.QMotion
import com.codewave.qullqi.domain.model.StoredUser
import com.codewave.qullqi.domain.model.UserId
import com.codewave.qullqi.domain.repository.AccountPreferenceRepository
import com.codewave.qullqi.domain.repository.LoginResult
import com.codewave.qullqi.domain.repository.SessionRepository
import com.codewave.qullqi.domain.security.BiometricAuthenticator
import com.codewave.qullqi.domain.security.BiometricResult
import com.codewave.qullqi.domain.session.AppLock
import com.codewave.qullqi.feature.FlowViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** [Login] = AuthActivity login; [Unlock] = MainActivity re-authentication after 5 minutes (FR-002b). */
enum class LoginMode { Login, Unlock }

sealed interface LoginStatus {
    data object Idle : LoginStatus
    data object Checking : LoginStatus
    data object Entering : LoginStatus
    data class Wrong(val attemptsLeft: Int) : LoginStatus
    data object Locked : LoginStatus
}

data class AccountRow(val id: UserId, val name: String, val initials: String, val maskedPhone: String, val locked: Boolean, val phone: String = "")

data class LoginState(
    val account: AccountRow? = null,
    /** Login for an account not stored on this device: "+591XXXXXXXX" (spec 004, FR-014). */
    val phone: String? = null,
    val firstName: String = "",
    /** How many PIN digits are entered. The digits themselves never enter the state (FR-025). */
    val filled: Int = 0,
    val status: LoginStatus = LoginStatus.Idle,
    val message: String? = null,
    val shakeCount: Int = 0,
    val accounts: List<AccountRow> = emptyList(),
    val showAccounts: Boolean = false,
    val biometricKeyVisible: Boolean = false,
    val showEnableBiometrics: Boolean = false,
)

sealed interface LoginEvent {
    data class Digit(val digit: Int) : LoginEvent
    data object Delete : LoginEvent
    data object Fingerprint : LoginEvent
    data object SwitchAccount : LoginEvent
    data object DismissAccounts : LoginEvent
    data class PickAccount(val userId: UserId) : LoginEvent
    data object AddAccount : LoginEvent
    /** Accounts sheet → "Volver al inicio": back to Welcome (Crear cuenta / Ya tengo cuenta). */
    data object GoToWelcome : LoginEvent
    data object Recover : LoginEvent
    data class EnableBiometrics(val enable: Boolean) : LoginEvent
}

sealed interface LoginEffect {
    data object EnterMain : LoginEffect
    data object Unlocked : LoginEffect
    data object ToRegistration : LoginEffect
    data object ToWelcome : LoginEffect
    data object ToLoginPhone : LoginEffect
    data class ToRecover(val phone: String) : LoginEffect
}

class LoginViewModel(
    private val requestedUser: UserId?,
    private val mode: LoginMode,
    private val session: SessionRepository,
    private val preferences: AccountPreferenceRepository,
    private val biometrics: BiometricAuthenticator,
    private val appLock: AppLock,
    private val enteringMillis: Long = QMotion.ENTERING_MS.toLong(),
    private val requestedPhone: String? = null,
) : FlowViewModel<LoginState, LoginEffect>(LoginState()) {

    private val pin = StringBuilder()

    /** Hidden after a rejected fingerprint renewal until the next PIN login (FR-008). */
    private var biometricsExpired = false

    init {
        viewModelScope.launch {
            val cached = requestedPhone?.let { p -> session.storedUsers().first().firstOrNull { it.phone == p }?.id }
            when {
                requestedPhone != null && cached == null -> setState {
                    copy(account = null, phone = requestedPhone, firstName = "", status = LoginStatus.Idle, message = null)
                }
                else -> load(cached ?: requestedUser ?: preferences.mostRecentUser())
            }
        }
    }

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.Digit -> digit(event.digit)
            LoginEvent.Delete -> if (current.status !is LoginStatus.Checking && pin.isNotEmpty()) {
                pin.deleteAt(pin.lastIndex)
                setState { copy(filled = pin.length) }
            }
            LoginEvent.Fingerprint -> fingerprint()
            LoginEvent.SwitchAccount -> viewModelScope.launch {
                val rows = session.storedUsers().first().map(::row)
                setState { copy(accounts = rows, showAccounts = true) }
            }
            LoginEvent.DismissAccounts -> setState { copy(showAccounts = false) }
            is LoginEvent.PickAccount -> viewModelScope.launch {
                setState { copy(showAccounts = false) }
                load(event.userId)
            }
            // "Agregar cuenta": an existing account from another device logs in by phone (spec 004).
            LoginEvent.AddAccount -> {
                setState { copy(showAccounts = false) }
                emit(LoginEffect.ToLoginPhone)
            }
            LoginEvent.GoToWelcome -> {
                setState { copy(showAccounts = false) }
                emit(LoginEffect.ToWelcome)
            }
            LoginEvent.Recover -> (current.account?.phone ?: current.phone)?.let { emit(LoginEffect.ToRecover(it)) }
            is LoginEvent.EnableBiometrics -> enableBiometrics(event.enable)
        }
    }

    private suspend fun load(userId: UserId?) {
        clearPin()
        val users = session.storedUsers().first()
        val user = users.firstOrNull { it.id == userId } ?: users.firstOrNull() ?: return
        val locked = user.locked
        val bioKey = !locked && !biometricsExpired && biometrics.isAvailable() && preferences.preference(user.id).biometricsEnabled
        setState {
            copy(
                account = row(user),
                firstName = user.displayName.substringBefore(' '),
                filled = 0,
                status = if (locked) LoginStatus.Locked else LoginStatus.Idle,
                message = if (locked) LOCKED else null,
                biometricKeyVisible = bioKey,
            )
        }
    }

    private fun digit(d: Int) {
        val s = current
        if (s.status is LoginStatus.Checking || s.status is LoginStatus.Entering || s.status is LoginStatus.Locked) return
        if (pin.length >= PIN_LENGTH) return
        pin.append(d)
        setState { copy(filled = pin.length, message = if (status is LoginStatus.Wrong) message else null) }
        if (pin.length == PIN_LENGTH) check()
    }

    private fun check() {
        val id = current.account?.id
        val phone = current.phone
        if (id == null && phone == null) return
        val entered = pin.toString()
        clearPin()
        setState { copy(status = LoginStatus.Checking) }
        viewModelScope.launch {
            val result = if (id != null) session.login(id, entered) else session.loginWithPhone(phone!!, entered)
            when (val r = result) {
                LoginResult.Success -> {
                    biometricsExpired = false
                    val userId = id ?: session.currentUser.first()?.id
                    if (userId == null) setState { copy(filled = 0, status = LoginStatus.Idle, message = AuthMessages.UNAVAILABLE) }
                    else succeed(userId)
                }
                is LoginResult.WrongPin -> setState {
                    copy(
                        filled = 0,
                        status = LoginStatus.Wrong(r.attemptsLeft),
                        message = "Te quedan ${r.attemptsLeft} intento${if (r.attemptsLeft == 1) "" else "s"}.",
                        shakeCount = shakeCount + 1,
                    )
                }
                LoginResult.Locked -> setState {
                    copy(filled = 0, status = LoginStatus.Locked, message = LOCKED, shakeCount = shakeCount + 1, biometricKeyVisible = false)
                }
                LoginResult.UnknownUser -> setState { copy(filled = 0, status = LoginStatus.Idle, message = UNKNOWN, shakeCount = shakeCount + 1) }
                LoginResult.Unavailable, LoginResult.SessionExpired ->
                    setState { copy(filled = 0, status = LoginStatus.Idle, message = AuthMessages.UNAVAILABLE) }
            }
        }
    }

    /** Device biometrics (US5): never consumes PIN attempts and never unlocks a locked account (FR-017). */
    private fun fingerprint() {
        val s = current
        val id = s.account?.id ?: return
        if (!s.biometricKeyVisible || s.status !is LoginStatus.Idle && s.status !is LoginStatus.Wrong) return
        viewModelScope.launch {
            when (biometrics.authenticate("Ingresa con tu huella", "Toca el sensor")) {
                BiometricResult.Success -> when (session.loginWithBiometrics(id)) {
                    LoginResult.Success -> succeed(id, offerBiometrics = false)
                    LoginResult.Locked -> setState { copy(status = LoginStatus.Locked, message = LOCKED, biometricKeyVisible = false) }
                    LoginResult.SessionExpired -> {
                        biometricsExpired = true
                        setState { copy(message = AuthMessages.PIN_REQUIRED, biometricKeyVisible = false) }
                    }
                    LoginResult.Unavailable -> setState { copy(message = AuthMessages.UNAVAILABLE) }
                    else -> Unit
                }
                BiometricResult.Failed -> setState { copy(message = "No reconocimos tu huella. Usa tu PIN.") }
                BiometricResult.Cancelled, BiometricResult.Unavailable -> Unit
            }
        }
    }

    private fun enableBiometrics(enable: Boolean) {
        val id = current.account?.id ?: return
        setState { copy(showEnableBiometrics = false) }
        viewModelScope.launch {
            if (enable) preferences.setBiometrics(id, true) else preferences.markBiometricsPrompted(id)
            enter()
        }
    }

    private suspend fun succeed(id: UserId, offerBiometrics: Boolean = true) {
        appLock.unlock()
        if (mode == LoginMode.Unlock) {
            setState { copy(filled = 0, status = LoginStatus.Idle, message = null) }
            emit(LoginEffect.Unlocked)
            return
        }
        preferences.recordLogin(id)
        // "Activar huella" is offered once per account, only where the device supports it (FR-018).
        val pref = preferences.preference(id)
        if (offerBiometrics && biometrics.isAvailable() && !pref.biometricsPrompted && !pref.biometricsEnabled) {
            setState { copy(filled = 0, status = LoginStatus.Entering, message = null, showEnableBiometrics = true) }
            return
        }
        enter()
    }

    private suspend fun enter() {
        setState { copy(filled = 0, status = LoginStatus.Entering, message = ENTERING) }
        delay(enteringMillis)
        emit(LoginEffect.EnterMain)
    }

    private fun clearPin() {
        pin.setLength(0)
    }

    override fun onCleared() {
        clearPin()
    }

    companion object {
        const val PIN_LENGTH = 4
        const val LOCKED = "Bloqueamos el acceso por seguridad. Recupera tu PIN."
        const val ENTERING = "Ingresando…"
        const val UNKNOWN = "Número o PIN incorrecto."

        fun row(u: StoredUser) = AccountRow(
            id = u.id,
            name = u.displayName,
            initials = u.displayName.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
            maskedPhone = maskPhone(u.phone),
            locked = u.locked,
            phone = u.phone,
        )

        /** "+59170000001" → "+591 7••• ••01" */
        fun maskPhone(phone: String): String {
            val local = phone.removePrefix("+591")
            return if (local.length == 8) "+591 ${local.first()}••• ••${local.takeLast(2)}" else phone
        }
    }
}
