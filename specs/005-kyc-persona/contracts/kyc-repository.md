# Contract: KycRepository (replaced — shared area, coordinate)

Package `com.codewave.qullqi.domain.repository`. Replaces the spec 002 draft/attempt contract.

```kotlin
interface KycRepository {
    /** Latest verification of the logged-in user; null = never started (FR-002). */
    suspend fun current(): KycRead
    /** Creates a verification; never retried automatically (FR-008). */
    /** No document data: the provider reads it (FR-003). */
    suspend fun start(): KycStart
}

sealed interface KycRead {
    data class Found(val verification: Verification?) : KycRead
    data object Unavailable : KycRead
}

sealed interface KycStart {
    data class Started(val pageUrl: String) : KycStart
    data object AlreadyVerified : KycStart
    data object InProgress : KycStart          // re-read with current() and resume
    data object Refused : KycStart             // attempts exhausted → "Contacta a soporte"
    data object Unavailable : KycStart
}

```

Platform services (`domain/system`):

```kotlin
interface ProviderPage {                                    // Android: CAMERA permission + KycWebViewActivity
    suspend fun open(url: String): PageOpen
    fun openCameraSettings()
}
sealed interface PageOpen { data object Opened : PageOpen; data class CameraDenied(val permanent: Boolean) : PageOpen }
```

`AppDependencies` gains `kyc: KycRepository` (remote), `providerPage: ProviderPage`
(AuthActivity injects `AndroidProviderPage` with `copy(...)`, like biometrics).
`AuthFlow` signature unchanged; its default `kycEntry` becomes `{ onDone -> KycFlow(deps, onDone) }`.
