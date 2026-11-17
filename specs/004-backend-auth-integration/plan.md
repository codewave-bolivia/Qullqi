# Implementation Plan: Backend Integration for Access and Registration (M2)

**Branch**: `feature/004-backend-auth-integration` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/004-backend-auth-integration/spec.md`

## Summary

Replace the local session/registration sources of the M2 flows with remote implementations that
call the Qullqi backend (constitution v2.0.0, Principle I scope: M2 auth + `GET /me`), keeping the
existing repository interfaces, ViewModels and screens. Add: an HTTP client (Ktor 3.x), secure
storage for the renewal credential (Android Keystore), a device identifier and separate name
fields in the local database (migration `1.sqm`), registration step 3 with three name fields,
login by phone for accounts not on the device, and connection-error results. The local SQL
repositories stay only as the in-memory implementation used by ViewModel tests; the app no longer
ships sample accounts (clarification Q1).

## Technical Context

**Language/Version**: Kotlin 2.4.21, Compose Multiplatform 1.12.2
**Primary Dependencies**: existing stack + **Ktor Client 3.x** (`core`, `content-negotiation`,
`auth`, `serialization-kotlinx-json`; engine `okhttp` in `androidApp`; `mock` for tests) +
**kotlinx-serialization-json** and the Kotlin serialization compiler plugin (approved by
constitution v2.0.0; exact versions in research R1)
**Storage**: SQLDelight 2.4.1 (device-only data + current-user cache), schema v2 via `1.sqm`;
Android Keystore + SharedPreferences (ciphertext only) for renewal credentials
**Testing**: `androidHostTest` (JVM): remote repositories against Ktor `MockEngine`; migration
verified by SQLDelight; ViewModel tests keep the in-memory SQL repositories
**Target Platform**: Android (minSdk 24, target 37). iOS/Desktop: no driver, no engine, no secure
storage (Builder) — they keep showing "no disponible"
**Project Type**: mobile app (KMP shared + androidApp) consuming a REST backend
**Performance Goals**: login → main area < 3 s on a normal connection (SC-001); 15 s request timeout
**Constraints**: no PIN/OTP/credential in DB, logs or plain prefs (FR-005); HTTPS only in release
(FR-002); no automatic retry of `register`, `pin/reset`; single-flight renewal (FR-007)
**Scale/Scope**: 7 auth endpoints + `GET /me`; 1 new screen (login by phone); 2 changed screens
(registration step 3, code step link); 1 schema migration

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Backend-authoritative | Endpoints used: `request-otp`, `verify-otp`, `register`, `login`, `refresh`, `logout`, `pin/reset`, `GET /me` — all inside the approved scope. No KYC, balance or transaction calls. Backend decides PIN/lock/status; device only displays. Tokens: access in memory, renewal credential encrypted with a Keystore key; PIN/OTP never stored or logged. Release HTTPS only; cleartext only in the debug manifest. Base URL from build config. DTOs and error mapping stay in `data/remote/`. Error mapping: [contracts/api-mapping.md](contracts/api-mapping.md). | ✅ |
| II. MVVM | ViewModels unchanged in shape; they receive new result cases (`Unavailable`, `SessionExpired`) and new events (three name fields, login by phone). No repository calls from Composables. | ✅ |
| III. KMP portability | Remote repositories, DTOs, client setup and `TokenStore` interface in `commonMain`; engine injected from `androidApp`; Keystore implementation in `shared/androidMain`. No Android types in `commonMain`. | ✅ |
| IV. Builder boundaries | No changes in `iosApp/`, `desktopApp/`, `iosMain`, `jvmMain`, `iosTest`, `jvmTest`. iOS/Desktop keep `App()` → "no disponible". Darwin/Java engines and iOS/Desktop `TokenStore` deferred to the Builder. | ✅ |
| V. Collaborative integrity | Shared files touched (coordinate, other dev approves): `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `**/sqldelight/**` (`User.sq`, new `Device.sq`, `1.sqm`), `domain/model/User.kt` (none: `CurrentUser` shape kept), `domain/repository/{Session,Registration}Repository.kt`, `AuthFlow.kt` routes. `CurrentUserSource` (M4 contract) unchanged. | ✅ coordinate |
| VI. Responsive | New login-by-phone screen reuses the registration phone step layout (`QScreen`, tokens). Step 3 adds two fields in the same scroll column. | ✅ |
| VII. Spec-driven | Spec 004 with owner; tasks will cover tests for remote repositories, migration and changed ViewModels. | ✅ |
| Tech: SQLDelight | Tables: `user` (+`first_name`, `last_name_paternal`, `last_name_maternal`), new `device`. Seed removed from app install (tests only). **Migration needed**: `1.sqm` (installed spec 003 builds exist). | ✅ |

Post-design re-check: ✅ no new violations (research R3 documents the fingerprint gate ceiling).

## Project Structure

### Documentation (this feature)

```text
specs/004-backend-auth-integration/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── repositories.md     # interface changes (shared area)
│   └── api-mapping.md      # endpoint → repository method → domain result
└── checklists/requirements.md
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                       # + ktor, kotlinx-serialization, serialization plugin
shared/build.gradle.kts                         # + serialization plugin, ktor deps, sqldelight migration verify
shared/src/commonMain/
├── sqldelight/com/codewave/qullqi/db/
│   ├── User.sq                                 # + name columns, upsert/cache queries
│   ├── Device.sq                               # new: device(slot=1, device_id)
│   └── 1.sqm                                   # v1 → v2 migration
├── sqldelight/databases/1.db                   # generated v1 schema snapshot (migration verify)
└── kotlin/com/codewave/qullqi/
    ├── AppDependencies.kt                      # create(remote) wiring
    ├── domain/repository/                      # Session/Registration results + loginWithPhone, refreshCurrentUser
    ├── data/
    │   ├── db/DatabaseFactory.kt               # SeededSchema → test-only use
    │   ├── LocalDataReset.kt                   # clears device data + tokens, no samples
    │   ├── Sql*Repository.kt                   # kept for ViewModel tests (name fields)
    │   └── remote/
    │       ├── QullqiApi.kt                    # HttpClient setup, envelope, error parsing
    │       ├── Dto.kt                          # wire DTOs (@Serializable)
    │       ├── TokenStore.kt                   # interface + in-memory access token holder
    │       ├── RemoteSessionRepository.kt
    │       └── RemoteRegistrationRepository.kt
    └── feature/auth/
        ├── AuthFlow.kt                         # routes: LoginPhone, Login(userId|phone), RecoverPin(phone)
        ├── login/LoginPhoneScreen.kt           # new (reuses phone step UI)
        ├── login/LoginViewModel.kt             # phone target, Unavailable, SessionExpired
        ├── register/InfoStep.kt, RegistrationViewModel.kt, CodeStep.kt
        ├── recover/RecoverPinViewModel.kt      # by phone
        └── start/StartViewModel.kt             # "Ya tengo cuenta" → LoginPhone when no accounts
shared/src/androidMain/kotlin/com/codewave/qullqi/data/
├── AndroidDatabaseFactory.kt                   # plain Schema (no seed) + migrations
└── KeystoreTokenStore.kt                       # new
shared/src/androidHostTest/kotlin/com/codewave/qullqi/data/remote/   # MockEngine tests
androidApp/
├── build.gradle.kts                            # buildConfig API_BASE_URL, ktor-client-okhttp
├── src/main/AndroidManifest.xml                # INTERNET permission
├── src/debug/AndroidManifest.xml               # usesCleartextTraffic=true (debug only)
└── src/main/kotlin/com/codewave/qullqi/{QullqiApplication,MainActivity}.kt
```

**Structure Decision**: existing KMP layout; remote code under `data/remote/` (commonMain), one
Android-only class for the Keystore. No new modules.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| Keep `Sql{Session,Registration}Repository` in `commonMain` | ViewModel tests (spec 003) run on them | Rewriting ~40 ViewModel tests on `MockEngine` now doubles the work; `ponytail:` comment marks them for deletion when those tests move |
| Ktor `auth` plugin | Single-flight token refresh on 401 (FR-007) for free | Hand-written mutex + retry is more code and easy to get wrong |
