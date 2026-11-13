# Contract: M2/M3 Repositories (internal to this owner's milestones)

Package `com.codewave.qullqi.domain.repository`. Implementations in `data/` (SQLDelight).
Errors are returned as typed results, never thrown for expected business outcomes.

```kotlin
interface SessionRepository : CurrentUserSource {
    fun storedUsers(): Flow<List<StoredUser>>                 // for login / switch account (FR-008)
    suspend fun login(userId: UserId, pin: String): LoginResult
    suspend fun loginWithBiometrics(userId: UserId): LoginResult   // added by spec 003; never changes attempts
    suspend fun logout()
}
sealed interface LoginResult {
    data object Success : LoginResult
    data class WrongPin(val attemptsLeft: Int) : LoginResult  // 2, 1
    data object Locked : LoginResult                          // attemptsLeft reached 0 or already 0
    data object UnknownUser : LoginResult
}
data class StoredUser(val id: UserId, val displayName: String, val phone: String, val locked: Boolean)

interface RegistrationRepository {
    suspend fun requestCode(phone: String): CodeRequestResult
    suspend fun confirmCode(phone: String, code: String): CodeConfirmResult
    suspend fun register(form: RegistrationForm): RegistrationResult   // opens the session
    suspend fun resetPin(phone: String, newPin: String): PinResetResult // needs a confirmed code; unlocks
}
data class RegistrationForm(
    val phone: String, val fullName: String, val email: String,
    val termsVersion: String, val pin: String, val pinConfirmation: String,
)
sealed interface CodeRequestResult {
    data object Sent : CodeRequestResult
    data class TooSoon(val waitMillis: Long) : CodeRequestResult       // < 30 s
    data object InvalidPhone : CodeRequestResult
}
sealed interface CodeConfirmResult {
    data object Confirmed : CodeConfirmResult
    data class Wrong(val triesLeft: Int) : CodeConfirmResult
    data object Expired : CodeConfirmResult                            // > 5 min or tries exhausted
    data object NoCode : CodeConfirmResult
}
sealed interface RegistrationResult {
    data class Created(val userId: UserId) : RegistrationResult
    data object PhoneNotConfirmed : RegistrationResult
    data object PhoneAlreadyRegistered : RegistrationResult
    data class Invalid(val fields: Set<Field>) : RegistrationResult   // NAME, EMAIL, TERMS, PIN, PIN_MISMATCH
}

interface KycRepository {
    fun attempts(userId: UserId): Flow<List<KycAttempt>>               // newest first (FR-017)
    suspend fun start(userId: UserId, type: DocumentType): StartResult // AlreadyVerified | AttemptOpen | Started(id)
    suspend fun update(attemptId: String, change: KycDraftChange)       // document number, image refs, extracted data, declaration
    suspend fun submit(attemptId: String): SubmitResult                 // Submitted | Missing(items)
    suspend fun process(attemptId: String): KycOutcome                  // Approved | UnderReview | Rejected(reason)
    suspend fun discard(attemptId: String)                              // drafts only
}
```

Shared types (`domain/model`): `DocumentType { CI, PASSPORT }`,
`AttemptState { DRAFT, SUBMITTED, APPROVED, REVIEW, REJECTED }`, `KycAttempt`, `KycDraftChange`.

Dependencies injected by constructor: `QullqiDatabase`, `PinHasher`, `Clock`, `IdGenerator`,
`OtpCodeGenerator`, `MockKycProcessor`, and a `CoroutineDispatcher` for IO.

Utility (data): `LocalDataReset.reset(withSamples: Boolean)` drops and recreates the schema;
`withSamples = false` gives the "empty device" state.
