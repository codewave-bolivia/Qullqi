---

description: "Task list for Local Data Layer — Identity and Verification Model"
---

# Tasks: Local Data Layer — Identity and Verification Model

**Input**: Design documents from `/specs/002-local-data-layer/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. The constitution (Principle VII) requires unit tests for domain logic, and
quickstart.md §1 defines the test classes. Tests run with `./gradlew :shared:testAndroidHostTest`.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5 from spec.md)
- Include exact file paths in descriptions

## Path Conventions

- `K` = `shared/src/commonMain/kotlin/com/codewave/qullqi`
- `SQ` = `shared/src/commonMain/sqldelight/com/codewave/qullqi/db`
- `A` = `shared/src/androidMain/kotlin/com/codewave/qullqi`
- `T` = `shared/src/androidHostTest/kotlin/com/codewave/qullqi/data`
- Never create or edit files under `shared/src/iosMain`, `iosTest`, `jvmMain`, `jvmTest`,
  `iosApp/` or `desktopApp/` (Builder-owned). Never add `expect` declarations.
- Domain types and repository interfaces are copied **verbatim** from
  `contracts/current-user.md` and `contracts/repositories.md`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add SQLDelight 2.4.1 to the build (high-risk shared files — approval at integration).

- [X] T001 Add to `gradle/libs.versions.toml`: version `sqldelight = "2.4.1"`; libraries `sqldelight-androidDriver` (`app.cash.sqldelight:android-driver`), `sqldelight-sqliteDriver` (`app.cash.sqldelight:sqlite-driver`), `sqldelight-coroutines` (`app.cash.sqldelight:coroutines-extensions`), all `version.ref = "sqldelight"`; plugin `sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }`
- [X] T002 Add `alias(libs.plugins.sqldelight) apply false` to the root `build.gradle.kts` plugins block
- [X] T003 In `shared/build.gradle.kts`: apply `alias(libs.plugins.sqldelight)`; `commonMain` += `libs.sqldelight.coroutines`; `androidMain` += `libs.sqldelight.androidDriver`; add `getByName("androidHostTest").dependencies { implementation(libs.sqldelight.sqliteDriver); implementation(libs.kotlin.test) }`; add `sqldelight { databases { create("QullqiDatabase") { packageName.set("com.codewave.qullqi.db") } } }`
- [X] T004 Run `./gradlew :shared:compileAndroidMain :shared:compileKotlinJvm` and confirm success (no schema yet is fine)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain types, injected collaborators, full schema with integrity rules, seed, database factory, test harness.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 [P] Create `K/domain/model/User.kt` with `UserId` (value class), `VerificationStatus { NOT_VERIFIED, UNDER_REVIEW, VERIFIED }`, `CurrentUser`, `fun VerificationStatus.canOperateMoney()` and `StoredUser`, exactly as in contracts/current-user.md and contracts/repositories.md
- [X] T006 [P] Create `K/domain/model/Kyc.kt` with `DocumentType { CI, PASSPORT }`, `AttemptState { DRAFT, SUBMITTED, APPROVED, REVIEW, REJECTED }`, `KycAttempt` (all fields of data-model.md `kyc_attempt`), `KycDraftChange` (sealed: `DocumentNumber`, `FrontImage`, `BackImage`, `Selfie`, `ExtractedData(fullName, documentNumber, birthDate, expiryDate)`, `DeclarationAccepted`), and image reference constants `placeholder:ci-front`, `placeholder:ci-back`, `placeholder:passport`, `placeholder:selfie`
- [X] T007 [P] Create `K/domain/system/System.kt` with `fun interface Clock { fun nowMillis(): Long }`, `fun interface IdGenerator { fun newId(): String }` (default object using `kotlin.uuid.Uuid.random().toString()` with `@OptIn(ExperimentalUuidApi::class)` if required), `fun interface OtpCodeGenerator { fun newCode(): String }` and `object SampleOtpCodeGenerator : OtpCodeGenerator` returning `"123456"`
- [X] T008 [P] Create `K/domain/security/PinHasher.kt`: `interface PinHasher { fun hash(pin: String): PinHash; fun verify(pin: String, stored: PinHash): Boolean }` and `data class PinHash(val hashB64: String, val saltB64: String, val iterations: Int)`
- [X] T009 [P] Create `SQ/User.sq` with tables from data-model.md: `user` (`id TEXT NOT NULL PRIMARY KEY`, `full_name TEXT NOT NULL CHECK (length(trim(full_name)) > 0)`, `phone TEXT NOT NULL UNIQUE CHECK (phone GLOB '+591[67][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')`, `email TEXT NOT NULL`, `verification TEXT NOT NULL DEFAULT 'NOT_VERIFIED' CHECK (verification IN ('NOT_VERIFIED','UNDER_REVIEW','VERIFIED'))`, `is_new INTEGER NOT NULL DEFAULT 1 CHECK (is_new IN (0,1))`, `pin_attempts_left INTEGER NOT NULL DEFAULT 3 CHECK (pin_attempts_left BETWEEN 0 AND 3)`, `created_at INTEGER NOT NULL`); `credential` (`user_id TEXT NOT NULL PRIMARY KEY REFERENCES user(id) ON DELETE CASCADE`, `pin_hash TEXT NOT NULL`, `pin_salt TEXT NOT NULL`, `iterations INTEGER NOT NULL CHECK (iterations >= 100000)`, `updated_at INTEGER NOT NULL`); `current_session` (`slot INTEGER NOT NULL PRIMARY KEY CHECK (slot = 1)`, `user_id TEXT NOT NULL REFERENCES user(id) ON DELETE CASCADE`, `opened_at INTEGER NOT NULL`); `terms_acceptance` (`user_id TEXT NOT NULL REFERENCES user(id) ON DELETE CASCADE`, `terms_version TEXT NOT NULL`, `accepted_at INTEGER NOT NULL`, `PRIMARY KEY (user_id, terms_version)`) — tables only, queries come in story phases
- [X] T010 [P] Create `SQ/PhoneConfirmation.sq` with table `phone_confirmation` (`id TEXT NOT NULL PRIMARY KEY`, `phone TEXT NOT NULL CHECK (phone GLOB '+591[67][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')`, `code TEXT NOT NULL CHECK (length(code) = 6 AND code NOT GLOB '*[^0-9]*')`, `issued_at INTEGER NOT NULL`, `expires_at INTEGER NOT NULL`, `tries_left INTEGER NOT NULL DEFAULT 3 CHECK (tries_left BETWEEN 0 AND 3)`, `confirmed_at INTEGER`, `user_id TEXT REFERENCES user(id) ON DELETE CASCADE`) and `CREATE INDEX phone_confirmation_phone ON phone_confirmation(phone, issued_at)`
- [X] T011 [P] Create `SQ/KycAttempt.sq` with table `kyc_attempt` (all columns and CHECKs of data-model.md, including `CHECK (front_ref IS NULL OR front_ref LIKE 'placeholder:%')` for front/back/selfie, `CHECK (document_type = 'CI' OR back_ref IS NULL)`, `CHECK (state <> 'REJECTED' OR rejection_reason IS NOT NULL)`), `CREATE UNIQUE INDEX kyc_attempt_one_open ON kyc_attempt(user_id) WHERE state IN ('DRAFT','SUBMITTED')`, `CREATE INDEX kyc_attempt_history ON kyc_attempt(user_id, created_at)`, and trigger `kyc_attempt_final_sets_user_status` `AFTER UPDATE OF state ON kyc_attempt WHEN new.state IN ('APPROVED','REVIEW','REJECTED')` mapping `APPROVED→VERIFIED`, `REVIEW→UNDER_REVIEW`, `REJECTED→NOT_VERIFIED`
- [X] T012 Generate seed PIN hashes with a one-off command (not committed): Python `hashlib.pbkdf2_hmac('sha256', b'1234', salt, 120000, 32)` for six fixed 16-byte salts (one per sample user), base64-encoded; record salt/hash pairs for T013
- [X] T013 Create `SQ/Seed.sq` with the 6 sample users of data-model.md "Sample data" (ids `usr-verified`, `usr-unverified`, `usr-review`, `usr-rejected`, `usr-locked`, `usr-new`; names, phones `+5917000000N`, emails `<id>@example.com`, statuses, `pin_attempts_left`, `is_new`), one `credential` each (T012 values, `iterations = 120000`), one `terms_acceptance` `2026-01` each, and attempts `att-verified-1` (CI, APPROVED), `att-review-1` (PASSPORT, REVIEW), `att-rejected-1` (CI, REJECTED, reason `Documento ilegible`), `att-locked-1` (CI, APPROVED), all with `placeholder:` refs and fixed timestamps; no `current_session` row
- [X] T014 Create `K/data/db/DatabaseFactory.kt`: `object DatabaseFactory { fun create(driver: SqlDriver): QullqiDatabase }` executing `PRAGMA foreign_keys = ON` before returning; caller creates the schema
- [X] T015 [P] Create `A/data/Pbkdf2PinHasher.kt` implementing `PinHasher` with `PBKDF2WithHmacSHA256`, 16-byte `SecureRandom` salt, 120 000 iterations, 256-bit key, base64 (`java.util.Base64`), constant-time compare (`MessageDigest.isEqual`); add `// ponytail: 4-digit PIN is brute-forceable offline (10k combos); real protection is the 3-attempt lock. Upgrade: Android Keystore-wrapped key when real data exists.`
- [X] T016 [P] Create `A/data/AndroidDatabaseFactory.kt`: builds `AndroidSqliteDriver(QullqiDatabase.Schema, context, "qullqi.db", callback)` whose callback enables foreign keys in `onConfigure` (`db.setForeignKeyConstraintsEnabled(true)`), returns `DatabaseFactory.create(driver)`
- [X] T017 Create `T/TestDatabase.kt`: `fun testDatabase(withSamples: Boolean = true): QullqiDatabase` using `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)`, `QullqiDatabase.Schema.create`, `DatabaseFactory.create`; when `withSamples = false` delete all rows from every table; plus `class FakeClock(var now: Long) : Clock` and a sequential `FakeIdGenerator`
- [X] T018 Create `T/SchemaIntegrityTest.kt` asserting each is refused: duplicate phone; phone `+59150000001`; `kyc_attempt` for unknown user; `current_session` with `slot = 2`; second DRAFT attempt for `usr-unverified`; `front_ref = 'file:///photo.jpg'`; `back_ref` on PASSPORT; REJECTED without reason; and that deleting `usr-unverified` cascades its credential and terms rows
- [X] T019 Run `./gradlew :shared:testAndroidHostTest` — T018 passes; `Pbkdf2PinHasher().verify("1234", <seed hash of usr-verified>)` asserted true in `T/PinHasherTest.kt` (create it) so seed hashes match the Android hasher

**Checkpoint**: Schema, seed and integrity rules verified — user stories can start.

---

## Phase 3: User Story 1 - Returning user is recognized and can log in (Priority: P1) 🎯 MVP

**Goal**: Stored users are listed; PIN login with 3 attempts and lock; one open session; logout; switch account.

**Independent Test**: `SessionRepositoryTest` against the sample data.

### Tests for User Story 1

- [ ] T020 [US1] Create `T/SessionRepositoryTest.kt`: `storedUsers()` returns 6 users with `locked = true` only for `usr-locked`; `login(usr-verified, "1234")` → `Success` and `currentUser` emits `usr-verified`; wrong PIN → `WrongPin(2)`, `WrongPin(1)`, then `Locked`; correct PIN after lock → `Locked`; correct PIN after 1 wrong → `Success` and attempts back to 3; `login(UserId("nope"), …)` → `UnknownUser`; `logout()` → `currentUser` emits `null` and user still listed; login as another user replaces the session (switch account)

### Implementation for User Story 1

- [ ] T021 [US1] Add named queries to `SQ/User.sq`: `storedUsers` (id, full_name, phone, pin_attempts_left ordered by full_name), `credentialFor(user_id)`, `attemptsLeft(id)`, `decrementAttempts(id)` (`MAX(pin_attempts_left - 1, 0)`), `resetAttempts(id)`, `openSession(user_id, opened_at)` (`INSERT OR REPLACE INTO current_session(slot, …) VALUES (1, …)`), `closeSession`, `currentUser` (join `current_session` → `user`: id, full_name, phone, email, verification), `markNotNew(id)`
- [ ] T022 [US1] Create `K/domain/repository/SessionRepository.kt` with `CurrentUserSource`, `SessionRepository`, `LoginResult` exactly as in contracts
- [ ] T023 [US1] Create `K/data/SqlSessionRepository.kt` implementing `SessionRepository` with constructor (`db: QullqiDatabase`, `hasher: PinHasher`, `clock: Clock`, `io: CoroutineDispatcher`): `currentUser` via `currentUser.asFlow().mapToOneOrNull(io)` mapped to `CurrentUser`; `login` in a transaction (unknown → `UnknownUser`; attempts 0 → `Locked`; verify hash; wrong → decrement and return `WrongPin(left)` or `Locked` at 0; right → reset attempts, `openSession`, `markNotNew`); `logout` → `closeSession`

**Checkpoint**: US1 tests green.

---

## Phase 4: User Story 2 - New person registers an account (Priority: P1)

**Goal**: Phone code request/confirm with 30 s resend, 5 min validity, 3 tries; registration creates a NOT_VERIFIED user with terms and session; PIN reset unlocks.

**Independent Test**: `RegistrationRepositoryTest` on an empty database (`testDatabase(withSamples = false)`) plus one duplicate-phone case on samples.

### Tests for User Story 2

- [ ] T024 [US2] Create `T/RegistrationRepositoryTest.kt` with `FakeClock`: `requestCode("12345678")` → `InvalidPhone`; `requestCode("71234567")` → `Sent`; again at +10 s → `TooSoon(20_000)`; at +31 s → `Sent`; `confirmCode` wrong ×3 → `Wrong(2)`, `Wrong(1)`, then `Expired`; correct after +6 min → `Expired`; correct within time → `Confirmed`; `register` without confirmation → `PhoneNotConfirmed`; mismatched PIN → `Invalid(PIN_MISMATCH)`; bad email → `Invalid(EMAIL)`; terms blank → `Invalid(TERMS)`; success → `Created`, user `NOT_VERIFIED`, `is_new = 1`, email lowercased/trimmed, phone stored as `+59171234567`, terms row present, `currentUser` is the new user, `credential.pin_hash` ≠ `"1234"` and not containing it; on samples `register` with `70000001` → `PhoneAlreadyRegistered`; `resetPin` for `usr-locked` after confirmed code → attempts back to 3 and new PIN logs in

### Implementation for User Story 2

- [ ] T025 [US2] Add named queries to `SQ/PhoneConfirmation.sq`: `latestForPhone(phone)`, `insertCode(id, phone, code, issued_at, expires_at)`, `decrementTries(id)`, `markConfirmed(id, confirmed_at)`, `linkUser(user_id, id)`; and to `SQ/User.sq`: `insertUser`, `insertCredential`, `updateCredential`, `insertTerms`, `userIdByPhone(phone)`
- [ ] T026 [US2] Create `K/domain/repository/RegistrationRepository.kt` with `RegistrationRepository`, `RegistrationForm`, `CodeRequestResult`, `CodeConfirmResult`, `RegistrationResult` (with `enum class Field { NAME, EMAIL, TERMS, PIN, PIN_MISMATCH }`), `PinResetResult { Success, PhoneNotConfirmed, UnknownPhone, InvalidPin }`
- [ ] T027 [US2] Create `K/data/SqlRegistrationRepository.kt` (constructor: `db`, `hasher`, `clock`, `ids: IdGenerator`, `otp: OtpCodeGenerator`, `io`): normalize phone (`[67]\d{7}` → `+591…`; otherwise `InvalidPhone`); resend window 30 000 ms, expiry 300 000 ms, 3 tries; "confirmed" = latest code for phone has `confirmed_at` within the last 15 minutes; `register` validates name non-blank, email regex `^[^@\s]+@[^@\s]+\.[^@\s]+$` after trim+lowercase, terms version non-blank, PIN `^\d{4}$`, confirmation equal; inserts user, credential, terms, links confirmation and opens the session in one transaction; `resetPin` requires a confirmed code, updates credential and resets attempts to 3

**Checkpoint**: US2 tests green.

---

## Phase 5: User Story 3 - User verifies their identity (Priority: P1)

**Goal**: KYC attempts with completeness rules, deterministic outcomes, status sync and history.

**Independent Test**: `KycRepositoryTest` with `usr-unverified`, `usr-rejected`, `usr-verified`.

### Tests for User Story 3

- [ ] T028 [US3] Create `T/KycRepositoryTest.kt`: `start(usr-verified, CI)` → `AlreadyVerified`; `start(usr-unverified, CI)` → `Started`; second `start` → `AttemptOpen`; CI submit missing back+selfie → `Missing({BACK_IMAGE, SELFIE})`; passport submit without back → `Submitted`; `update` with a non-`placeholder:` ref is rejected; document numbers ending `1`, `5`, `0` → `Approved`/`UnderReview`/`Rejected("Documento ilegible")` and user status `VERIFIED`/`UNDER_REVIEW`/`NOT_VERIFIED`; retry for `usr-rejected` creates a new attempt and `attempts()` lists 2 newest first; `discard` deletes a draft and is a no-op for a final attempt

### Implementation for User Story 3

- [ ] T029 [US3] Add named queries to `SQ/KycAttempt.sq`: `insertDraft`, `attemptById`, `attemptsForUser` (`ORDER BY created_at DESC`), `openAttemptForUser`, `setDocumentNumber`, `setFront`, `setBack`, `setSelfie`, `setExtracted`, `setDeclaration`, `setState(state, finished_at, rejection_reason, id)`, `deleteDraft(id)` (`WHERE state = 'DRAFT'`); and `userVerification(id)` in `SQ/User.sq`
- [ ] T030 [P] [US3] Create `K/data/MockKycProcessor.kt`: `class MockKycProcessor(private val stepDelayMillis: Long = 700)` with `suspend fun decide(documentNumber: String): KycOutcome` — 4 × `delay(stepDelayMillis)`; last digit `0` → `Rejected("Documento ilegible")`, `5` → `UnderReview`, else `Approved`
- [ ] T031 [US3] Create `K/domain/repository/KycRepository.kt` with `KycRepository`, `StartResult { AlreadyVerified, AttemptOpen, Started(id) }`, `SubmitResult { Submitted, Missing(items: Set<MissingItem>) }`, `enum MissingItem { DOCUMENT_NUMBER, FRONT_IMAGE, BACK_IMAGE, SELFIE, EXTRACTED_DATA, DECLARATION }`, `KycOutcome { Approved, UnderReview, Rejected(reason) }`
- [ ] T032 [US3] Create `K/data/SqlKycRepository.kt` (constructor: `db`, `clock`, `ids`, `processor: MockKycProcessor`, `io`): `start` checks `userVerification` and open attempt; `update` rejects refs not starting with `placeholder:` (`IllegalArgumentException`, a programming error); `submit` computes missing items per document type (CI requires back) and sets `SUBMITTED`; `process` calls `processor.decide` then `setState` to `APPROVED`/`REVIEW`/`REJECTED` with `finished_at` (trigger updates the user); `discard` → `deleteDraft`

**Checkpoint**: US1–US3 green — M2 and M3 can be built on this.

---

## Phase 6: User Story 4 - Sample data covers every M2/M3 state (Priority: P2)

**Goal**: Deterministic, resettable synthetic data; empty-device mode.

**Independent Test**: `SeedDataTest`.

### Tests for User Story 4

- [ ] T033 [US4] Create `T/SeedDataTest.kt`: exactly the 6 ids of data-model.md; each user's `verification` equals the mapping of its latest final attempt (none → `NOT_VERIFIED`); 10 consecutive `LocalDataReset.reset(withSamples = true)` produce identical row dumps of every table; `reset(withSamples = false)` → 0 users; every phone matches `+59170000\d{3}` and every email ends with `@example.com`; every image ref starts with `placeholder:`

### Implementation for User Story 4

- [ ] T034 [US4] Create `K/data/LocalDataReset.kt`: `class LocalDataReset(private val db: QullqiDatabase)` with `fun reset(withSamples: Boolean)` running, in one transaction, deletes of all tables (children first) and, when `withSamples`, the seed statements — expose the seed as a named query group in `SQ/Seed.sq` (e.g. `insertSamples:` multi-statement query) so `Schema.create` and reset share the same rows; remove `current_session`

**Checkpoint**: US4 green.

---

## Phase 7: User Story 5 - Other milestones can rely on a stable contract (Priority: P2)

**Goal**: `CurrentUserSource` behaves as documented for M4+.

**Independent Test**: `CurrentUserContractTest` uses only `CurrentUserSource` and contract types.

- [ ] T035 [US5] Create `T/CurrentUserContractTest.kt` typed against `CurrentUserSource` only: emits `null` with no session; emits current value immediately on collection; emits again on login, logout, switch account, and when a KYC attempt for the logged-in user is processed (status change); `canOperateMoney()` true only for `VERIFIED`
- [ ] T036 [US5] Verify `specs/002-local-data-layer/contracts/current-user.md` matches the code in `K/domain/model/User.kt` and `K/domain/repository/SessionRepository.kt` (names, fields, enum values); fix the code, not the contract, on mismatch

**Checkpoint**: Contract ready for the other developer's review (spec header stays `**Contract**: Draft` until they approve).

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T037 Run the PR gate locally: `./gradlew clean :shared:compileAndroidMain :shared:compileKotlinJvm :androidApp:compileDebugKotlin :desktopApp:compileKotlin` — must pass (proves no `expect` broke jvm/Desktop)
- [ ] T038 Run `./gradlew :shared:testAndroidHostTest` — all tests green; record counts
- [ ] T039 Update `CLAUDE.md` §2 tree: add `shared/src/commonMain/sqldelight/com/codewave/qullqi/db/` (User.sq, PhoneConfirmation.sq, KycAttempt.sq, Seed.sq) and packages `domain/{model,repository,security,system}`, `data/`, `data/db/`; androidMain `data/`; androidHostTest `data/`; refresh "Last verified"
- [ ] T040 Add to `CLAUDE.md` §14 a "Sample credentials" note: PIN `1234`, SMS code `123456`, document number last digit `0`/`5`/other → rejected/under review/approved; and that iOS/Desktop `SqlDriver` + `PinHasher` implementations are pending from the Builder
- [ ] T041 Set `**Status**: Ready` in `specs/002-local-data-layer/spec.md`, commit, run `scripts/branches.sh check feature/002-local-data-layer` and produce the CLAUDE.md §11 report (shared-area approvals required: Gradle files, sqldelight, domain/model, domain/repository, CLAUDE.md)

---

## Dependencies & Execution Order

- Setup (T001–T004) → Foundational (T005–T019) → US1 → US2 → US3 → US4 → US5 → Polish.
- US1, US2, US3 depend only on Foundational and touch different repositories, but all three add
  queries to `SQ/User.sq` (T021, T025, T029) — run those query tasks sequentially.
- US4 (T034) moves the seed into a reusable query; do it after US1–US3 so their tests confirm
  nothing changed. US5 needs US1 (session) and US3 (status change).

### Parallel Opportunities

- T005–T011 (different files) in parallel; T015 ∥ T016.
- Within US3: T030 (processor) ∥ T029 (queries).
- Test-writing tasks T020, T024, T028 can be drafted in parallel once Foundational is done.

## Parallel Example: Foundational

```text
Agent A: T005, T006, T007, T008   (domain types)
Agent B: T009, T010, T011         (schema)
Agent C: T015, T016               (Android hasher + driver)
Then: T012 → T013 → T014 → T017 → T018 → T019
```

## Implementation Strategy

### MVP (US1)

T001–T023: schema + seed + login. M2's login screens can start.

### M2 + M3 ready

Add US2 (registration) and US3 (KYC): everything the owner's milestones need.

### Hand-off to M4+

US4 (reset/samples) and US5 (contract test), then ask the other developer to approve the Contract.
