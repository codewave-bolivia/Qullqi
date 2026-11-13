# Implementation Plan: Access and Registration

**Branch**: `feature/003-access-registration` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-access-registration/spec.md`

## Summary

Build the M2 access screens (splash, welcome, PIN login with lock and account switch, 4-step
registration, PIN recovery, biometric login, hand-off placeholders) in shared Compose
Multiplatform UI, on top of the spec 002 data layer. Includes a minimal **Nocturne design system**
(tokens, theme, M2 components) and a tiny **navigation** layer, both shared and reusable by M4.

Key decisions (see [research.md](research.md)):

- **Compose Multiplatform UI in `commonMain`** (the project already renders `App()` on all targets);
  Android is the verified platform; iOS/Desktop show "no disponible todavía" because they have no
  data driver yet.
- **MVVM**: one ViewModel per flow (`StartViewModel`, `LoginViewModel`, `RegistrationViewModel`,
  `RecoverPinViewModel`, `MainPlaceholderViewModel`) exposing immutable `StateFlow<UiState>` and
  one-off `Effect`s; screens are stateless.
- **Own minimal navigator** (back stack of a sealed `Route`, `AnimatedContent` transitions per
  ARCHITECTURE.md §6) — no navigation dependency for 12 routes.
- **Real biometrics**: `androidx.biometric:biometric:1.1.0` (latest stable) implemented in
  `androidApp` behind a `BiometricAuthenticator` interface; `MainActivity` becomes a
  `FragmentActivity` (required by `BiometricPrompt`).
- **Data extension**: new table `account_preference` (biometrics enabled/prompted, last login) +
  `SessionRepository.loginWithBiometrics()`; no change to the `user` table or the M4 contract.
- **Assets**: Inter 400/500 (OFL) and ~10 Phosphor icons (MIT) bundled as resources — require
  **one approved download** (CLAUDE.md: external sources).

## Technical Context

**Language/Version**: Kotlin 2.4.21, Compose Multiplatform 1.12.2, Material3 1.12.0-alpha03 (used
only as primitives; theme is our own)

**Primary Dependencies**: existing — compose runtime/foundation/ui/material3/resources,
lifecycle-viewmodel-compose 2.11.0, SQLDelight 2.4.1. **New**: `androidx.biometric:biometric:1.1.0`
(androidApp only); `org.jetbrains.kotlinx:kotlinx-coroutines-test` (androidHostTest only, version
`kotlinx-coroutines` = 1.11.0)

**Storage**: spec 002 `QullqiDatabase` + new `account_preference` table

**Testing**: ViewModel unit tests in `shared/src/androidHostTest` (in-memory DB, fake biometric,
`Dispatchers.setMain`); manual validation on a physical Android device (quickstart.md)

**Target Platform**: Android (physical device, API 24+; biometrics need API 23+ class 2/3 sensor);
iOS/Desktop compile and show an "unavailable" screen

**Project Type**: KMP mobile/desktop app — shared UI + presentation

**Performance Goals**: 60 fps transitions; login (PBKDF2 120k) < 400 ms on device; app start to
login ≤ 2.5 s including the 1.9 s splash

**Constraints**: no network at runtime (fonts/icons bundled); no hardcoded colors/sizes in screens;
no Builder-owned files modified; PIN never logged or retained

**Scale/Scope**: 12 routes, 2 sheets, 5 ViewModels, ~10 components

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Data via repositories | Screens → ViewModels → repository interfaces (002 + `AccountPreferenceRepository`); no SQL in UI | PASS |
| II. MVVM unidirectional | One ViewModel per flow, `StateFlow<UiState>`, `Effect` channel for navigation/toasts; stateless screens | PASS |
| III. KMP portability | UI, ViewModels, navigation, design system in `commonMain`; biometrics behind interface, Android impl in `androidApp`; no `expect` | PASS |
| IV. Platform boundaries | `desktopApp/main.kt` and iOS `MainViewController` keep calling `App()` unchanged (new parameter has a default) → no protected file touched | PASS |
| V. Collaborative integrity | Shared areas touched: `designsystem/`, `navigation/`, `sqldelight/`, `domain/repository`, Gradle catalog → other developer's approval | PASS with action |
| VI. Responsive UI | Content constrained to a centered column (max 480 dp) on wide windows; keypad/boxes scale within; scroll on short heights; keyboard input on PIN/code | PASS |
| VII. Spec-driven | Spec 003 clarified; owner krypton612 | PASS |
| Dependencies | 2 new libraries (biometric stable, coroutines-test test-only) — justified below; asset download needs approval | PASS with action |

Portable vs Android-specific: everything in `shared/src/commonMain` except
`AndroidBiometricAuthenticator` and the dependency wiring in `androidApp`.
SQLDelight: new table `account_preference` (FK → `user` ON DELETE CASCADE), no migration
(pre-release; no device holds a database yet because no released build instantiated it).

**Post-design re-check**: unchanged — all PASS.

## Project Structure

### Documentation (this feature)

```text
specs/003-access-registration/
├── plan.md · research.md · data-model.md · quickstart.md
├── contracts/
│   ├── navigation.md        # routes + hand-off entry points for M3/M4
│   ├── design-system.md     # tokens and components M4 may reuse
│   └── viewmodels.md        # UiState/Event/Effect per flow
└── tasks.md                 # /speckit-tasks
```

### Source Code

```text
gradle/libs.versions.toml                               # + biometric 1.1.0, coroutines-test
shared/build.gradle.kts                                 # androidHostTest += coroutines-test
shared/src/commonMain/composeResources/font/            # inter_regular.ttf, inter_medium.ttf (+ OFL.txt in docs)
shared/src/commonMain/composeResources/drawable/        # ph_*.xml Phosphor icons
shared/src/commonMain/sqldelight/com/codewave/qullqi/db/AccountPreference.sq
shared/src/commonMain/kotlin/com/codewave/qullqi/
├── App.kt                                  # MODIFY: App(dependencies: AppDependencies? = null)
├── AppDependencies.kt                      # repositories + BiometricAuthenticator
├── designsystem/                           # [shared] tokens, theme, components
│   ├── Tokens.kt  Theme.kt  Type.kt  Motion.kt
│   └── components/ Buttons.kt Inputs.kt PinBoxes.kt Keypad.kt FlowBar.kt Sheet.kt Toast.kt Shake.kt Wordmark.kt
├── navigation/                             # [shared] Route, Navigator, NavHost
├── domain/security/BiometricAuthenticator.kt
├── domain/repository/AccountPreferenceRepository.kt
├── data/SqlAccountPreferenceRepository.kt
└── feature/auth/
    ├── start/ (SplashScreen, WelcomeScreen, StartViewModel)
    ├── login/ (LoginScreen, AccountsSheet, LoginViewModel, EnableBiometricsSheet)
    ├── register/ (PhoneStep, CodeStep, InfoStep, PinStep, DoneScreen, RegistrationViewModel)
    ├── recover/ (RecoverPinScreen, RecoverPinViewModel)
    └── handoff/ (MainPlaceholderScreen, KycPlaceholderScreen, MainPlaceholderViewModel, UnavailableScreen)
shared/src/androidHostTest/kotlin/com/codewave/qullqi/feature/auth/   # ViewModel tests
androidApp/src/main/kotlin/com/codewave/qullqi/
├── MainActivity.kt                         # MODIFY: FragmentActivity, builds AppDependencies
└── AndroidBiometricAuthenticator.kt
androidApp/build.gradle.kts                 # + biometric
```

**Structure Decision**: feature-first package under `feature/auth/` for M2 screens; cross-feature
`designsystem/` and `navigation/` at the root package so M4 can reuse them. Spec 002 repositories
gain one method (`loginWithBiometrics`) and one new repository.

## Complexity Tracking

| Choice | Why needed | Simpler alternative rejected because |
|--------|-----------|--------------------------------------|
| `androidx.biometric` dependency | FR-016–FR-018 real biometrics (clarified) | Platform `BiometricPrompt` (API 28+) — would drop API 24–27 and duplicate fallback logic |
| `kotlinx-coroutines-test` (test only) | ViewModels use `viewModelScope` (Main dispatcher) | Injecting scopes into every ViewModel just for tests |
| `MainActivity` → `FragmentActivity` | `BiometricPrompt` requires it | None: library requirement |
| Own navigator (~80 lines) | 12 routes, custom transitions per ARCHITECTURE.md §6 | navigation-compose: new dependency, transitions harder to match; revisit when M4 needs tabs/deep links |

Deliberately skipped: Electric Lime accent, density setting, reduce-motion setting UI (respected
via system setting only), theme toggle UI (M9).
