# Implementation Plan: Access and Registration

**Branch**: `feature/003-access-registration` | **Date**: 2026-11-13 (rev. 2: activities, launch
screen, re-auth) | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-access-registration/spec.md`

## Summary

Build the M2 access experience on top of the spec 002 data layer, structured as **two Android
activities** (ARCHITECTURE.md §2):

- **AuthActivity** (launcher): system launch screen → animated splash → welcome / login /
  registration / PIN recovery / M3 entry (`kycIntro` placeholder).
- **MainActivity**: M4 main area (placeholder until the other developer replaces it) with logout,
  "Verificar" (opens AuthActivity at `kycIntro`) and the **5-minute re-auth lock**.

Screens are shared Compose Multiplatform UI; each activity hosts one shared **flow root**
(`AuthFlow`, `MainFlow`) with its own short in-activity navigation. Switching activities uses the
**veil transition**. A minimal **Nocturne design system** (tokens, theme, M2 components) is shared and
reusable by M4.

Key decisions (see [research.md](research.md)):

- **Flow roots, not one global stack**: `AuthFlow(start, …)` and `MainFlow(…)` in `commonMain`;
  activity transitions are callbacks (`onEnterMain`, `onLogout`, `onVerify`, `onKycDone`) implemented
  by `androidApp` with `Intent`s; iOS/Desktop get `QullqiRoot` that swaps the two roots (FR-034).
- **Launch screen**: `androidx.core:core-splashscreen:1.2.0` in AuthActivity; theme background
  `#161826` = in-app splash background → no jump.
- **Re-auth**: `AppLock` (in-memory, shared): unlocked when AuthActivity hands off; MainActivity
  records `onStop` time and on `onStart` locks after > 5 min; process death ⇒ locked by default.
  Locked MainActivity shows the shared `UnlockScreen` overlay (login UI for the current account)
  above its preserved content; content is not composed while locked.
- **Biometrics**: `androidx.biometric:biometric:1.1.0` (latest stable) in `androidApp`, behind a
  `BiometricAuthenticator` interface; both activities extend `FragmentActivity`.
- **Data**: new `account_preference` table + `SessionRepository.loginWithBiometrics()`.
- **Assets**: Inter 400/500 (OFL) and ~11 Phosphor icons (MIT), plus a Qullqi launcher icon derived
  from the Inter "q" glyph + accent dot — **one approved download** needed.

## Technical Context

**Language/Version**: Kotlin 2.4.21, Compose Multiplatform 1.12.2

**Primary Dependencies**: existing compose, lifecycle-viewmodel-compose 2.11.0, SQLDelight 2.4.1.
**New**: `androidx.biometric:biometric:1.1.0`, `androidx.core:core-splashscreen:1.2.0` (both
`androidApp` only); `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0` (`androidHostTest` only)

**Storage**: spec 002 `QullqiDatabase` + `account_preference`

**Testing**: ViewModel/AppLock unit tests in `shared/src/androidHostTest`; manual validation on a
physical Android device ([quickstart.md](quickstart.md))

**Target Platform**: Android API 24+ (verified on a physical device); iOS/Desktop compile and show
"no disponible todavía"

**Project Type**: KMP mobile/desktop app — shared UI + presentation, Android activity hosts

**Performance Goals**: 60 fps transitions; cold start to login ≤ 2.5 s (incl. 1.9 s splash);
PBKDF2 login < 400 ms on device

**Constraints**: no runtime network; tokens only (no literal colors/sizes in screens); no
Builder-owned files modified; PIN never logged/retained; MainActivity content never composed while
locked

**Scale/Scope**: 2 activities, 13 routes, 2 sheets, 5 ViewModels, ~10 components

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Data via repositories | Screens → ViewModels → repository interfaces (002 + `AccountPreferenceRepository`) | PASS |
| II. MVVM | One ViewModel per flow, `StateFlow<UiState>` + `Effect`s; stateless screens; activity switching via effects/callbacks | PASS |
| III. KMP portability | UI, ViewModels, navigation, design system, `AppLock` in `commonMain`; Android-only pieces (activities, biometric, launch screen) in `androidApp`; no `expect` | PASS |
| IV. Platform boundaries | `desktopApp/main.kt` and iOS keep calling `App()` (new params have defaults); `QullqiRoot` offered for the Builder | PASS |
| V. Collaborative integrity | Shared areas: `designsystem/`, `navigation/`, `sqldelight/`, `domain/repository`, Gradle catalog, `CLAUDE.md` → other developer's approval. MainActivity is the M4 host: M2 owns the activity shell, M4 owns `MainFlow` content | PASS with action |
| VI. Responsive UI | Centered column (max 480 dp) on wide windows; scroll on short heights; keyboard input for PIN/code | PASS |
| VII. Spec-driven | Spec 003 clarified (5 Q); owner krypton612 | PASS |
| Dependencies | 3 new libraries (2 stable AndroidX, 1 test-only) + asset download — need approval | PASS with action |

Portable vs Android-specific: all screens/flows/ViewModels in `shared/src/commonMain`;
`AuthActivity`, `MainActivity`, `AndroidBiometricAuthenticator`, themes, manifest in `androidApp`.
SQLDelight: `account_preference` (FK → `user` CASCADE); no migration (no released build has opened
the database yet — see research R6).

**Post-design re-check**: unchanged — all PASS.

## Project Structure

### Documentation (this feature)

```text
specs/003-access-registration/
├── plan.md · research.md · data-model.md · quickstart.md
├── contracts/
│   ├── navigation.md        # activities, flow roots, in-activity routes, hand-off for M3/M4
│   ├── design-system.md     # tokens + components M4 may reuse
│   └── viewmodels.md        # UiState/Event/Effect per flow, AppLock
└── tasks.md                 # /speckit-tasks
```

### Source Code

```text
gradle/libs.versions.toml                     # + biometric 1.1.0, core-splashscreen 1.2.0, coroutines-test
shared/build.gradle.kts                       # androidHostTest += coroutines-test
shared/src/commonMain/composeResources/font/  # inter_regular.ttf, inter_medium.ttf
shared/src/commonMain/composeResources/drawable/ # ph_*.xml icons
shared/src/commonMain/sqldelight/com/codewave/qullqi/db/AccountPreference.sq
shared/src/commonMain/kotlin/com/codewave/qullqi/
├── App.kt                    # MODIFY: App(dependencies = null) → QullqiRoot or UnavailableScreen
├── AppDependencies.kt
├── designsystem/             # [shared] Tokens, Theme, Type, Motion, Veil, components/*
├── navigation/               # [shared] AuthRoute, MainRoute, FlowNavigator, FlowHost
├── domain/security/BiometricAuthenticator.kt
├── domain/session/AppLock.kt             # 5-min background lock policy
├── domain/repository/AccountPreferenceRepository.kt
├── data/SqlAccountPreferenceRepository.kt
├── feature/auth/             # AuthFlow root + start/ login/ register/ recover/ kyc/(placeholder)
├── feature/main/             # MainFlow root (placeholder content for M4) + UnlockScreen overlay
└── QullqiRoot.kt             # single-window host for iOS/Desktop (swaps AuthFlow/MainFlow)
shared/src/androidHostTest/kotlin/com/codewave/qullqi/feature/  # ViewModel + AppLock tests
androidApp/
├── build.gradle.kts          # + biometric, core-splashscreen
├── src/main/AndroidManifest.xml               # AuthActivity launcher; MainActivity not exported
├── src/main/res/values/themes.xml             # Theme.Qullqi(.Starting) — #161826 backgrounds
├── src/main/res/drawable/ic_launcher_*.xml    # Qullqi icon (q + accent dot)
└── src/main/kotlin/com/codewave/qullqi/
    ├── AuthActivity.kt        # installSplashScreen, hosts AuthFlow, intents to MainActivity
    ├── MainActivity.kt        # MODIFY: hosts MainFlow, AppLock on onStop/onStart, recents privacy
    ├── QullqiApplication.kt   # builds AppDependencies once (database, hasher, …)
    └── AndroidBiometricAuthenticator.kt
```

**Structure Decision**: feature packages `feature/auth` (AuthActivity content) and `feature/main`
(MainActivity shell); `designsystem/` and `navigation/` shared at the root package for M4.

## Complexity Tracking

| Choice | Why needed | Simpler alternative rejected because |
|--------|-----------|--------------------------------------|
| Two activities + flow roots | Clarified (ARCHITECTURE.md §2) | Single activity with one stack — rejected by the owner |
| `QullqiApplication` holding dependencies | Both activities share one database/repositories | Building per activity → two database connections |
| `androidx.biometric` | Real biometrics (clarified), API 24+ | Platform `BiometricPrompt` needs API 28+ |
| `core-splashscreen` | Branded launch screen with back-compat (clarified) | Theme-only `windowBackground` misses the Android 12+ icon animation API |
| `kotlinx-coroutines-test` (test) | `viewModelScope` needs a test Main dispatcher | Injecting scopes into every ViewModel |
| Own `FlowNavigator` (~80 lines) | ≤ 9 routes per activity, custom motion | navigation-compose: extra dependency for short linear flows; revisit for M4 tabs |

Deliberately skipped: Electric Lime, density, theme toggle UI (M9), reduce-motion setting UI
(system setting respected).
