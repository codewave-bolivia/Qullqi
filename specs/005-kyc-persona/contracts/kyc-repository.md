# Contract: KycRepository (replaced — shared area, coordinate)

Package `com.codewave.qullqi.domain.repository`. Replaces the spec 002 draft/attempt contract.

```kotlin
interface KycRepository {
    /** Latest verification of the logged-in user; null = never started (FR-002). */
    suspend fun current(): KycRead
    /** Creates a verification; never retried automatically (FR-008). */
    suspend fun start(type: DocumentType, number: String, birthDate: SimpleDate): KycStart
}

sealed interface KycRead {
    data class Found(val verification: Verification?) : KycRead
    data object Unavailable : KycRead
}

sealed interface KycStart {
    data class Started(val pageUrl: String) : KycStart
    data object AlreadyVerified : KycStart
    data object InProgress : KycStart          // re-read with current() and resume
    data object DocumentTaken : KycStart
    data class Invalid(val fields: Set<KycField>) : KycStart
    data object Refused : KycStart             // attempts exhausted → "Contacta a soporte"
    data object Unavailable : KycStart
}

enum class KycField { NUMBER, BIRTH_DATE, TYPE }
```

Platform services (`domain/system`):

```kotlin
interface ProviderPage {                                    // Android: CAMERA permission + KycWebViewActivity
    suspend fun open(url: String): PageOpen
    fun openCameraSettings()
}
sealed interface PageOpen { data object Opened : PageOpen; data class CameraDenied(val permanent: Boolean) : PageOpen }
fun interface Today { fun date(): SimpleDate }                 // Android: LocalDate.now()
```

`AppDependencies` gains `kyc: KycRepository` (remote), `providerPage: ProviderPage`
(AuthActivity injects `AndroidProviderPage` with `copy(...)`, like biometrics) and `today: Today`.
`AuthFlow` signature unchanged; its default `kycEntry` becomes `{ onDone -> KycFlow(deps, onDone) }`.
