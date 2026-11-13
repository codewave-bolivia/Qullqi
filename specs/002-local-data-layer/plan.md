# Implementation Plan: Local Data Layer — Identity and Verification Model

**Branch**: `feature/002-local-data-layer` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-local-data-layer/spec.md`

## Summary

Add SQLDelight as the local data source in `:shared` and model only what M2 (access and
registration) and M3 (KYC) need: users, credentials, the single open session, phone
confirmations, terms acceptances and verification attempts, with deterministic synthetic seed
data. Expose a minimal, stable contract (`CurrentUserSource`) for the M4+ developer.

Key decisions (see [research.md](research.md)):

- **SQLDelight 2.4.1**, verified in a throw-away spike against this exact toolchain (Kotlin
  2.4.21, AGP 9.1.1 KMP library plugin, Gradle 9.5.1).
- **Integrity in the schema**, not in screens: foreign keys with `ON DELETE CASCADE`, `CHECK`
  constraints, a partial unique index for "one non-final attempt", a single-row table for "one open
  session", and a trigger that keeps the user's verification status in sync with final attempts.
- **No `expect`/`actual`**: the `SqlDriver` and the PIN hasher are injected. Only Android
  implementations are added (`androidMain`); iOS and Desktop implementations are Builder-owned and
  not required to compile (Principle IV).
- **Tests in `androidHostTest`** with an in-memory JDBC SQLite driver (no protected test source sets).

## Technical Context

**Language/Version**: Kotlin 2.4.21 (Multiplatform), SQL (SQLite dialect via SQLDelight)

**Primary Dependencies**: SQLDelight 2.4.1 — Gradle plugin, `coroutines-extensions` (commonMain),
`android-driver` (androidMain), `sqlite-driver` (androidHostTest only). kotlinx-coroutines (already
on the classpath through Compose; declared explicitly via coroutines-extensions).

**Storage**: SQLite on device through SQLDelight; database `QullqiDatabase`, package
`com.codewave.qullqi.db`

**Testing**: `kotlin.test` in `shared/src/androidHostTest` with `JdbcSqliteDriver(IN_MEMORY)`;
run with `./gradlew :shared:testAndroidHostTest`

**Target Platform**: Android (implemented); iOS and Desktop compile but receive their driver and
hasher from the Builder later

**Project Type**: KMP mobile/desktop app — shared data layer, no UI in this feature

**Performance Goals**: queries for M2/M3 screens < 50 ms on a mid-range device; PIN verification
< 400 ms (hash cost)

**Constraints**: no network; synthetic data only; no real camera captures stored (FR-019); PIN never
stored readable (FR-004); no Builder-owned paths modified

**Scale/Scope**: ≤ 10 users per device, ≤ 20 attempts per user; 6 tables, 1 trigger

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Frontend-only, replaceable data | Repository interfaces in `domain/repository`; SQLDelight types stay in `data/`; seed is synthetic `INSERT`s in `.sq`; no network | PASS |
| II. MVVM | No ViewModels/UI here; repositories expose `Flow` for future ViewModels | PASS |
| III. KMP portability | All schema, queries, domain and repositories in `commonMain`; only driver factory and PBKDF2 hasher in `androidMain`; no `android.*` in common | PASS |
| IV. Platform boundaries | No `expect` declarations → nothing required in `iosMain`/`jvmMain`. iOS/Desktop driver + hasher deferred to Builder (documented) | PASS |
| V. Collaborative integrity | Touches high-risk shared files: `gradle/libs.versions.toml`, root and `shared` `build.gradle.kts`, `**/sqldelight/**`, `**/domain/model/**`, `**/domain/repository/**` → other developer's approval at integration (FR-021 of spec 001) | PASS with action |
| VI. Responsive UI | No UI | N/A |
| VII. Spec-driven | Spec 002 clarified, owner krypton612, checklist passing | PASS |
| SQLDelight rules (CLAUDE.md §14) | PKs everywhere, explicit FKs + `ON DELETE`, `PRAGMA foreign_keys = ON` per connection, named queries only, timestamps as epoch ms, seed deterministic, reset allowed pre-release | PASS (see note) |

Note on dates: instants (created, issued, expires, accepted, finished) are epoch milliseconds in
every table; calendar dates without time (birth date, document expiry) are ISO-8601 `YYYY-MM-DD`
text in every table. Each kind is consistent across tables, as §14.2 requires. No money is
stored in this feature.

Plan Constitution Check items (constitution "Development Workflow"):

- Portable: everything under `shared/src/commonMain`. Android-specific: `AndroidDatabaseFactory`,
  `Pbkdf2PinHasher` in `shared/src/androidMain`.
- Builder-owned paths: none touched.
- Repository interfaces: `CurrentUserSource`, `SessionRepository`, `RegistrationRepository`,
  `KycRepository`. Tables: `user`, `credential`, `current_session`, `phone_confirmation`,
  `terms_acceptance`, `kyc_attempt`. Seed: 6 sample users. Migration: none (pre-release reset).
- Responsive strategy: N/A.

**Post-design re-check**: unchanged, all PASS. The design adds no `expect` declarations and no
Builder-owned files.

## Project Structure

### Documentation (this feature)

```text
specs/002-local-data-layer/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── current-user.md       # Contract for M4+ (spec "Contract" section, in Kotlin terms)
│   └── repositories.md       # M2/M3 repository interfaces
├── checklists/requirements.md
└── tasks.md                  # /speckit-tasks
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                       # MODIFY: sqldelight version, 3 libraries, plugin
build.gradle.kts                                # MODIFY: sqldelight plugin apply false
shared/build.gradle.kts                         # MODIFY: plugin, deps, sqldelight { databases }
shared/src/commonMain/sqldelight/com/codewave/qullqi/db/
├── User.sq                 # user, credential, current_session, terms_acceptance + queries
├── PhoneConfirmation.sq    # phone_confirmation + queries
├── KycAttempt.sq           # kyc_attempt, partial index, status trigger + queries
└── Seed.sq                 # deterministic sample INSERTs (runs on Schema.create)
shared/src/commonMain/kotlin/com/codewave/qullqi/
├── domain/model/           # UserId, VerificationStatus, CurrentUser, KycAttempt, DocumentType, …
├── domain/repository/      # CurrentUserSource, SessionRepository, RegistrationRepository, KycRepository
├── domain/security/        # PinHasher (interface)
└── data/
    ├── db/DatabaseFactory.kt       # create QullqiDatabase from an injected SqlDriver, FK pragma
    ├── SqlSessionRepository.kt
    ├── SqlRegistrationRepository.kt
    ├── SqlKycRepository.kt
    ├── MockKycProcessor.kt         # deterministic outcome rule (FR-018)
    └── LocalDataReset.kt           # reset with or without samples
shared/src/androidMain/kotlin/com/codewave/qullqi/data/
├── AndroidDatabaseFactory.kt       # AndroidSqliteDriver + foreign keys on configure
└── Pbkdf2PinHasher.kt              # javax.crypto PBKDF2WithHmacSHA256 + SecureRandom salt
shared/src/androidHostTest/kotlin/com/codewave/qullqi/data/
├── TestDatabase.kt                 # JdbcSqliteDriver in-memory + FK pragma
├── SchemaIntegrityTest.kt
├── SessionRepositoryTest.kt
├── RegistrationRepositoryTest.kt
├── KycRepositoryTest.kt
└── SeedDataTest.kt
```

**Structure Decision**: layered packages inside `:shared` per the constitution
(`ui → presentation → domain ← data`). No new Gradle module. `.sq` files under `sqldelight/`
(CLAUDE.md §14.1).

## Complexity Tracking

| Choice | Why needed | Simpler alternative rejected because |
|--------|-----------|--------------------------------------|
| DB trigger for user verification status | FR-016/SC-003: status and final attempt must never disagree, enforced by the store (FR-021) | Updating both in repository code only: one forgotten call path breaks SC-003 |
| `PinHasher` interface + Android PBKDF2 | FR-004; no crypto in Kotlin common stdlib | New crypto dependency (needs approval, install scripts review); plain SHA-256 (weaker) |
| Injected `Clock` and `IdGenerator` | Deterministic tests for 30 s resend, 5 min expiry, attempt ids | Real time in tests makes expiry/resend tests slow or flaky |

Deliberately skipped: biometric preference (M2 decides), migrations (pre-release reset), iOS/Desktop
drivers (Builder), DI library (constructor injection only).
