# Contract: Repository changes (shared area — coordinate)

Package `com.codewave.qullqi.domain.repository`. Only additive changes, except
`RegistrationForm.fullName` (split). `CurrentUserSource` / `CurrentUser` (M4 contract) unchanged.

## SessionRepository

```kotlin
interface SessionRepository : CurrentUserSource {
    fun storedUsers(): Flow<List<StoredUser>>                 // accounts cached on this device
    suspend fun login(userId: UserId, pin: String): LoginResult
    suspend fun loginWithPhone(phone: String, pin: String): LoginResult   // NEW (FR-014)
    suspend fun loginWithBiometrics(userId: UserId): LoginResult           // now: refresh
    suspend fun refreshCurrentUser()                                       // NEW: GET /me, silent on Unavailable
    suspend fun logout()                                                   // remote logout + local clear
    val sessionExpired: Flow<UserId>                                       // NEW: emits only when a renewal is rejected (FR-016a); never on logout()
}

sealed interface LoginResult {
    data object Success : LoginResult
    data class WrongPin(val attemptsLeft: Int) : LoginResult
    data object Locked : LoginResult
    data object UnknownUser : LoginResult
    data object SessionExpired : LoginResult   // NEW: biometric refresh rejected → PIN required
    data object Unavailable : LoginResult      // NEW: no connection / server error, nothing consumed
}
```

## RegistrationRepository

```kotlin
data class RegistrationForm(
    val phone: String,
    val firstName: String,              // was fullName
    val lastNamePaternal: String,       // NEW
    val lastNameMaternal: String?,      // NEW, null/blank = none
    val email: String,
    val termsVersion: String,
    val pin: String,
    val pinConfirmation: String,
)

sealed interface CodeRequestResult { Sent; TooSoon(waitMillis); InvalidPhone; Unavailable /*NEW*/ }
sealed interface CodeConfirmResult { Confirmed; Wrong(triesLeft); Expired; NoCode; Unavailable /*NEW*/ }
sealed interface RegistrationResult { Created(userId); PhoneNotConfirmed; PhoneAlreadyRegistered;
                                      Invalid(fields); Unavailable /*NEW*/ }
enum class RegistrationField { FIRST_NAME, LAST_NAME_PATERNAL, LAST_NAME_MATERNAL, EMAIL, TERMS, PIN, PIN_MISMATCH }  // NAME split
enum class PinResetResult { Success, PhoneNotConfirmed, UnknownPhone, InvalidPin, Unavailable /*NEW*/ }
```

`requestCode` / `confirmCode` gain an optional `purpose: CodePurpose = REGISTER`
(`enum class CodePurpose { REGISTER, PIN_RESET }`); recovery passes `PIN_RESET`.

## TokenStore (new, `data/remote`, not a domain contract)

```kotlin
interface TokenStore {
    fun refreshToken(userId: UserId): String?
    fun saveRefreshToken(userId: UserId, token: String)
    fun clear(userId: UserId)
    fun clearAll()
}
```

Android: `KeystoreTokenStore(context)` in `shared/src/androidMain`. Tests: in-memory fake.

## AppDependencies

```kotlin
AppDependencies.create(
    db, clock, ids, io, debug, biometrics,
    httpEngine: HttpClientEngine,        // NEW (OkHttp from androidApp, MockEngine in tests)
    baseUrl: String,                     // NEW (BuildConfig.API_BASE_URL)
    tokens: TokenStore,                  // NEW
)
```

`hasher` and `otp` parameters are removed from the app wiring (remote does not hash or generate
codes); the SQL repositories keep them for tests.

## Routes (`feature/auth/AuthFlow.kt`)

```kotlin
data class LoginPhone(val prefill: String? = null) : AuthRoute     // NEW
data class Login(val userId: UserId? = null, val phone: String? = null) : AuthRoute
data class RecoverPin(val phone: String) : AuthRoute                // was userId
```

`AuthStart` and activity extras are unchanged.
