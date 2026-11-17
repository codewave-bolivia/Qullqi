# Implementation Plan: Identity Verification with Persona (M3)

**Branch**: `feature/005-kyc-persona` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/005-kyc-persona/spec.md`

> Implementation starts after spec 004 is integrated into `develop` (spec header: `waiting`); then
> `git merge develop` into this branch.

## Summary

Replace the `kycIntro` placeholder (the `kycEntry` slot of `AuthFlow`) with a 4-screen flow —
intro + privacy, document data, waiting, result — that calls `POST /my/kyc` and `GET /my/kyc`
and opens the Persona page returned by the backend in a **Custom Tab**. Return to the app is
detected when AuthActivity resumes; the `qullqi://kyc/result` link only closes the tab (trampoline
activity). The spec 002 local KYC model (attempts table, mock processor, placeholder images) is
removed with a schema migration, since capture now happens in Persona.

## Technical Context

**Language/Version**: Kotlin 2.4.21, Compose Multiplatform 1.12.2
**Primary Dependencies**: existing (spec 004: Ktor 3.6.1, kotlinx-serialization) + **`androidx.browser`**
(Custom Tabs, latest stable at T001, `androidApp` only; approved by the owner, spec Q1)
**Storage**: none new. Schema v3 drops the unused `kyc_attempt` table (`2.sqm`)
**Testing**: `androidHostTest`: `RemoteKycRepository` on `MockEngine`; `KycViewModel` with
coroutines-test virtual time (polling); migration verify (`2.db` snapshot)
**Target Platform**: Android (minSdk 24). iOS/Desktop: no Custom Tab opener (Builder)
**Project Type**: mobile app (KMP shared + androidApp)
**Performance Goals**: result or in-review shown ≤ 60 s after return (SC-002); polling every 5 s
**Constraints**: no images in the app (SC-004); no automatic retry of `POST /my/kyc` (FR-008);
polling only after the user returns from the tab; portrait guard covers the new trampoline activity
**Scale/Scope**: 4 screens, 2 endpoints, 1 trampoline activity, 1 migration

## Constitution Check

| Principle | Check | Result |
|---|---|---|
| I. Backend-authoritative | Endpoints `POST /my/kyc`, `GET /my/kyc` and opening the provider page: inside the approved M3 scope. Status, reasons and legal names come only from the backend. No provider keys in the app; the page address is opened, never stored. HTTP rules of spec 004 apply. Error mapping: [contracts/api-mapping.md](contracts/api-mapping.md). | ✅ |
| II. MVVM | One `KycViewModel` (shared) for the 4 screens of the flow (same pattern as registration); screens stateless. | ✅ |
| III. KMP | Repository, DTOs, ViewModel, screens in `commonMain`. Opening the page behind an injected `ExternalPageOpener`; Custom Tab implementation in `androidApp`. Today's date injected (`Today` provider) — no new date library in `commonMain`. | ✅ |
| IV. Builder boundaries | No protected paths. iOS/Desktop openers deferred to the Builder. | ✅ |
| V. Collaborative integrity | Shared files (coordinate): `domain/repository/KycRepository.kt`, `domain/model/Kyc.kt` (contract replaced; only used by M2/M3 code and tests), `**/sqldelight/**` (`KycAttempt.sq` removed, `2.sqm`, `Seed.sq`), version catalog + `androidApp/build.gradle.kts` (browser), `AndroidManifest.xml`, `AppDependencies.kt`. M4 contract (`CurrentUserSource`) unchanged. | ✅ coordinate |
| VI. Responsive | Screens reuse the registration step scaffold (`QScreen`, tokens, scroll). | ✅ |
| VII. Spec-driven | Tests for repository mapping, ViewModel routing/polling and migration. | ✅ |
| Tech: SQLDelight | Migration `2.sqm` (drop `kyc_attempt`, its index and trigger); verified against a `2.db` snapshot generated before the change. | ✅ |
| New dependency | `androidx.browser` approved (spec 005 Q1), Android-only. | ✅ |

## Project Structure

```text
specs/005-kyc-persona/
├── plan.md · research.md · data-model.md · quickstart.md
└── contracts/
    ├── kyc-repository.md    # new KycRepository contract (shared area)
    └── api-mapping.md       # POST/GET /my/kyc → domain results
```

```text
gradle/libs.versions.toml                      # + androidx-browser
androidApp/build.gradle.kts                    # + androidx.browser
androidApp/src/main/AndroidManifest.xml        # + KycReturnActivity (exported, qullqi://kyc/result, portrait)
androidApp/src/main/kotlin/com/codewave/qullqi/
├── KycReturnActivity.kt                       # trampoline: closes the Custom Tab, back to AuthActivity
├── CustomTabPageOpener.kt                     # ExternalPageOpener via CustomTabsIntent
└── AuthActivity.kt                            # inject opener + today; onNewIntent ignores (resume does the work)
shared/src/commonMain/
├── sqldelight/com/codewave/qullqi/db/
│   ├── KycAttempt.sq                          # deleted
│   ├── 2.sqm                                  # drop kyc_attempt
│   └── Seed.sq                                # deleteAll without kyc_attempt
├── sqldelight/databases/2.db                  # v2 snapshot (generated before 2.sqm)
└── kotlin/com/codewave/qullqi/
    ├── domain/model/Kyc.kt                    # replaced: KycStatus, Verification, DocumentType, DeclineReason, SimpleDate
    ├── domain/repository/KycRepository.kt     # replaced (contracts/kyc-repository.md)
    ├── domain/system/ExternalPageOpener.kt    # interface + Today provider
    ├── data/remote/RemoteKycRepository.kt     # new
    ├── data/remote/Dto.kt                     # + KycStartBody, KycDto
    ├── data/SqlKycRepository.kt, MockKycProcessor.kt   # deleted
    ├── AppDependencies.kt                     # kyc = RemoteKycRepository; opener, today
    └── feature/kyc/
        ├── KycFlow.kt                         # entry used by AuthFlow(kycEntry); own FlowNavigator
        ├── KycViewModel.kt                    # routing, validation, polling
        └── KycIntroScreen.kt · KycDocumentScreen.kt · KycWaitingScreen.kt · KycResultScreen.kt
shared/src/androidHostTest/…                  # RemoteKycRepositoryTest, KycViewModelTest, MigrationTest; KycRepositoryTest deleted
```

**Structure Decision**: new `feature/kyc/` package (M3 owns it); `AuthFlow` default `kycEntry`
switches from the placeholder to `KycFlow`; `KycPlaceholderScreen` deleted.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| Trampoline `KycReturnActivity` | The provider's redirect must close the Custom Tab, which runs in our task above AuthActivity | Deep link on AuthActivity creates a second instance (standard launch mode); `singleTask` changes M2's task behavior |
| Migration dropping `kyc_attempt` | The local capture model is dead code with a trigger that writes `user.verification` | Keeping it leaves a second, wrong source of verification status |
