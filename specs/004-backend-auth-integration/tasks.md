# Tasks: Backend Integration for Access and Registration (M2)

**Input**: `specs/004-backend-auth-integration/` (spec, plan, research, data-model, contracts, quickstart)
**Tests**: required by constitution VII (ViewModels/domain) and the plan (remote repositories on
`MockEngine`, migration verification). Host tests run with `./gradlew :shared:testAndroidHostTest`.

Paths: `S` = `shared/src/commonMain/kotlin/com/codewave/qullqi`, `SQ` =
`shared/src/commonMain/sqldelight/com/codewave/qullqi/db`, `HT` =
`shared/src/androidHostTest/kotlin/com/codewave/qullqi`, `A` =
`androidApp/src/main/kotlin/com/codewave/qullqi`.

## Phase 1: Setup (shared build files — coordinate, §9)

- [X] T001 Look up on Maven Central the latest **stable** Ktor 3.x and kotlinx-serialization-json 1.x compatible with Kotlin 2.4.21 (no alpha/beta/RC); add to `gradle/libs.versions.toml`: versions `ktor`, `kotlinx-serialization`; libraries `ktor-client-core`, `ktor-client-contentNegotiation`, `ktor-client-auth`, `ktor-serialization-kotlinxJson`, `ktor-client-okhttp`, `ktor-client-mock`, `kotlinx-serialization-json`; plugin `kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`; also `kotlinSerialization` alias in root `build.gradle.kts` with `apply false`
- [X] T002 In `shared/build.gradle.kts`: apply `kotlinSerialization`; `commonMain` deps `ktor-client-core`, `ktor-client-contentNegotiation`, `ktor-client-auth`, `ktor-serialization-kotlinxJson`, `kotlinx-serialization-json`; `androidHostTest` dep `ktor-client-mock`; sqldelight block: `schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))`
- [X] T003 Generate the v1 schema snapshot **before any `.sq` edit**: `./gradlew :shared:generateCommonMainQullqiDatabaseSchema`; confirm `shared/src/commonMain/sqldelight/databases/1.db` exists and commit it; then add `verifyMigrations.set(true)` to the sqldelight block in `shared/build.gradle.kts`
- [X] T004 In `androidApp/build.gradle.kts`: dependency `implementation(libs.ktor.client.okhttp)`; `buildFeatures { buildConfig = true }`; in `buildTypes` `debug { buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("qullqi.api.url.debug").getOrElse("http://10.0.2.2:8080/api/v1/")}\"") }` and `release { buildConfigField(... "qullqi.api.url.release" default "https://api.qullqi.invalid/api/v1/") }`
- [X] T005 [P] In `androidApp/src/main/AndroidManifest.xml`: add `<uses-permission android:name="android.permission.INTERNET" />` and `android:usesCleartextTraffic="true"` on `<application>` with the XML comment `<!-- TEMPORARY (constitution v2.1.0): set to false + https release URL before the first master release (T055) -->` (applies to debug and release, FR-002)
- [X] T006 Run `./gradlew :shared:compileAndroidMain :androidApp:compileDebugKotlin` and `./gradlew :shared:testAndroidHostTest` — must stay green (baseline before code changes)

## Phase 2: Foundational (blocks every story)

**Schema v2 (data-model.md)**

- [X] T007 In `SQ/User.sq`: append nullable columns to `CREATE TABLE user` (after `created_at`, same order as the migration): `first_name TEXT`, `last_name_paternal TEXT`, `last_name_maternal TEXT`; add queries `upsertUser` (`INSERT INTO user (id, full_name, first_name, last_name_paternal, last_name_maternal, phone, email, verification, created_at) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET full_name=excluded.full_name, first_name=excluded.first_name, last_name_paternal=excluded.last_name_paternal, last_name_maternal=excluded.last_name_maternal, phone=excluded.phone, email=excluded.email, verification=excluded.verification`), `setAttemptsLeft` (`UPDATE user SET pin_attempts_left = ? WHERE id = ?`), `userByPhone` (`SELECT id FROM user WHERE phone = ?`); update `insertUser` to take the three name columns
- [X] T008 [P] Create `SQ/Device.sq`: `CREATE TABLE device (slot INTEGER NOT NULL PRIMARY KEY CHECK (slot = 1), device_id TEXT NOT NULL);` queries `ensureDevice: INSERT OR IGNORE INTO device (slot, device_id) VALUES (1, ?);` and `deviceId: SELECT device_id FROM device WHERE slot = 1;`; add `DELETE FROM device;` to `deleteAll` in `SQ/Seed.sq`
- [X] T009 Create `SQ/1.sqm` exactly as data-model.md "Migration 1.sqm" (3 `ALTER TABLE user ADD COLUMN`, `CREATE TABLE device`, `DELETE FROM phone_confirmation;`, `DELETE FROM user;`); run `./gradlew :shared:verifyCommonMainQullqiDatabaseMigration` until it passes
- [X] T010 In `shared/src/androidMain/kotlin/com/codewave/qullqi/data/AndroidDatabaseFactory.kt`: use `QullqiDatabase.Schema` (no seed, FR-021) for driver and callback; keep foreign keys on `onConfigure`. Keep `SeededSchema` in `S/data/db/DatabaseFactory.kt` with KDoc "tests only"
- [X] T011 [P] Add schema v2 test `HT/data/MigrationTest.kt` (the v1 → v2 path is verified by T009's Gradle task): on a fresh `QullqiDatabase.Schema` assert that `device` exists, `user` accepts null name columns, `upsertUser` updates on same id, and `ensureDevice` keeps the first id
- [X] T012 Update `HT/data/SchemaIntegrityTest.kt` and `HT/data/SeedDataTest.kt` for the new columns/table (seed rows keep null name columns)

**Domain contracts (contracts/repositories.md — shared area)**

- [X] T013 In `S/domain/repository/SessionRepository.kt`: add `loginWithPhone(phone: String, pin: String): LoginResult`, `refreshCurrentUser()`, and `LoginResult.SessionExpired`, `LoginResult.Unavailable`
- [X] T014 In `S/domain/repository/RegistrationRepository.kt`: `RegistrationForm` fields `firstName`, `lastNamePaternal`, `lastNameMaternal: String?` (replace `fullName`); `enum class CodePurpose { REGISTER, PIN_RESET }` and `purpose: CodePurpose = CodePurpose.REGISTER` on `requestCode`/`confirmCode`; `Unavailable` in `CodeRequestResult`, `CodeConfirmResult`, `RegistrationResult`, `PinResetResult`; `RegistrationField` = `FIRST_NAME, LAST_NAME_PATERNAL, LAST_NAME_MATERNAL, EMAIL, TERMS, PIN, PIN_MISMATCH`
- [X] T015 Adapt the in-memory implementations to compile with T013/T014: `S/data/SqlRegistrationRepository.kt` (validate first name 1–60 required, paternal 1–60 required, maternal ≤ 60 optional; `full_name = "$firstName $lastNamePaternal"`; insert name columns; ignore `purpose`), `S/data/SqlSessionRepository.kt` (`loginWithPhone` via `userByPhone`, `refreshCurrentUser()` no-op); add `// ponytail: in-memory implementation kept for ViewModel tests; delete when they move to MockEngine` on both classes
- [X] T016 Update `HT/data/RegistrationRepositoryTest.kt` and `HT/data/SessionRepositoryTest.kt` for the split names and `loginWithPhone`

**Remote infrastructure (research R1, R2, R7)**

- [X] T017 [P] Create `S/data/remote/TokenStore.kt`: `interface TokenStore { fun refreshToken(userId: UserId): String?; fun saveRefreshToken(userId: UserId, token: String); fun clear(userId: UserId); fun clearAll() }`
- [X] T018 [P] Create `S/data/remote/Dto.kt` (`@Serializable`): `Envelope<T>(success, message, status, data: T?)`, `ErrorBody(error: ErrorDetail(code, message, details: JsonObject?))`, `RequestOtpBody(phoneNumber, purpose)`, `VerifyOtpBody(phoneNumber, code, purpose)`, `PhoneTokenDto(phoneVerifiedToken)`, `RegisterBody(firstName, lastNamePaternal, lastNameMaternal?, email, pin, deviceId, termsVersion)`, `LoginBody(phoneNumber, pin, deviceId)`, `RefreshBody(refreshToken, deviceId)`, `LogoutBody(deviceId)`, `PinResetBody(newPin, deviceId)`, `TokensDto(token, refreshToken, expiresIn)`, `MeDto(userId, phoneNumber, email, firstName, lastNamePaternal, lastNameMaternal?, verificationStatus, accountStatus)`; `Json { ignoreUnknownKeys = true; explicitNulls = false }`
- [X] T019 Create `S/data/remote/QullqiApi.kt`: builds `HttpClient(engine)` with `ContentNegotiation(json)`, `defaultRequest { url(baseUrl); contentType(Json) }`, timeouts (`HttpTimeout` connect 10 s, request 15 s), **no** retry plugin, `expectSuccess = false`; `Auth { bearer { loadTokens { holder }; refreshTokens { session refresh callback }; sendWithoutRequest { false } } }` where the refresh call uses `markAsRefreshTokenRequest()`; typed suspend functions per endpoint returning `ApiResult<T>` = `Ok(T) | Fail(code: String, status: Int, details: JsonObject?) | Unavailable` (I/O, timeout, 5xx, unparseable body); in debug only `Logging { level = LogLevel.INFO }` (URL + status, never headers/bodies, research R7); expose `clearBearer()`
- [X] T020 [P] Create `HT/data/remote/FakeTokenStore.kt` (map-backed) and `HT/data/remote/MockBackend.kt` helper: `MockEngine` routing by path returning envelope JSON, recording requests (path, headers, body) for assertions
- [X] T021 [P] Create `shared/src/androidMain/kotlin/com/codewave/qullqi/data/KeystoreTokenStore.kt` (research R2): AES/GCM/NoPadding 256-bit key in `AndroidKeyStore` alias `qullqi_tokens` (create if missing, no user-auth requirement); value = Base64(iv) + ":" + Base64(ciphertext) in SharedPreferences `qullqi_secure`, key `rt_<userId>`; `clearAll()` clears the prefs file; add the `ponytail:` ceiling comment from research R3 (not biometric-bound; upgrade path CryptoObject)
- [X] T022 Create `S/data/remote/Device.kt`: `fun QullqiDatabase.deviceId(ids: IdGenerator): String` = `ensureDevice(ids.newId())` then `deviceId()` (FR-006)
- [X] T023 In `S/AppDependencies.kt`: `create(db, clock, ids, io, debug, biometrics, httpEngine: HttpClientEngine, baseUrl: String, tokens: TokenStore)` wiring `RemoteSessionRepository`/`RemoteRegistrationRepository` (Phase 3+), `SqlAccountPreferenceRepository`, `SqlKycRepository` (unchanged for M3), `debugReset`; only if `HT/feature/TestSupport.kt` builds dependencies through `AppDependencies`, add `createInMemory(db, hasher, clock, ids, otp, io)` for tests (otherwise tests keep constructing the Sql repositories directly); remove `hasher`/`otp` from `create`
- [X] T024 In `A/QullqiApplication.kt`: pass `httpEngine = OkHttp.create()`, `baseUrl = BuildConfig.API_BASE_URL`, `tokens = KeystoreTokenStore(this)`; drop `Pbkdf2PinHasher`/`SampleOtpCodeGenerator` from app wiring
- [X] T025 In `S/data/LocalDataReset.kt`: `reset()` with no samples parameter: `seedQueries.deleteAll()` + `tokens.clearAll()`; update the debug splash sheet in `S/feature/auth/start/SplashScreen.kt` (and its caller) to a single "Reiniciar datos" action (FR-021)

**Checkpoint**: builds, host tests green, migration verified; app not yet wired to remote for each flow.

## Phase 3: User Story 1 — Log in with PIN against the backend (P1) 🎯 MVP

**Goal**: PIN login (stored account or by phone) validated by the backend; main area with backend name/status.
**Independent test**: quickstart scenarios 1–4.

- [X] T026 [P] [US1] Tests `HT/data/remote/RemoteSessionRepositoryTest.kt`: login 200 → tokens saved, `GET me` cached, `currentUser` emits name "Lucía Flores" + `VERIFIED`, attempts 3; 401 `invalid_pin {attempts_left: 2}` → `WrongPin(2)` and cache 2; 423 → `Locked`, cache 0; 422 → `UnknownUser`; I/O error → `Unavailable` and cache unchanged; request body has `phoneNumber`, `pin`, `deviceId`; no token/PIN written to the database
- [X] T027 [US1] Create `S/data/remote/RemoteSessionRepository.kt` (contracts/api-mapping.md rows `login`, `loginWithPhone`, `refreshCurrentUser`): `storedUsers()` and `currentUser` from the SQL cache (unchanged queries); on success save refresh token, set bearer tokens, `GET me` → `upsertUser` (`full_name = "$firstName $lastNamePaternal"`, email lowercased, unknown status → `NOT_VERIFIED`) + `openSession` + `setAttemptsLeft(3)` + `recordLogin` via preferences; `loginWithBiometrics`/`logout` temporarily `TODO` until US3/US4 tasks
- [X] T028 [US1] Routes in `S/feature/auth/AuthFlow.kt`: `LoginPhone(prefill: String? = null)`, `Login(userId: UserId? = null, phone: String? = null)`; render `LoginPhone` with the new screen; update all `Login(...)` call sites
- [X] T029 [US1] Create `S/feature/auth/login/LoginPhoneScreen.kt` + small `LoginPhoneViewModel` in the same file: reuse the registration phone input layout (`PhoneStep` composable pieces, `+591` prefix, `^[67]\d{7}$`), title "Ingresa tu número", CTA "Continuar" → `Login(phone = "+591…")`; tokens only (constitution VI)
- [X] T030 [US1] `S/feature/auth/login/LoginViewModel.kt`: target `Account(userId) | Phone(phone)`; Phone target calls `loginWithPhone`; handle `Unavailable` (toast "Sin conexión. Intenta de nuevo.", digits cleared, attempts unchanged); `AddAccount` → `LoginPhone`; locked + "Recuperar PIN" passes the phone Also (FR-003, FR-003a): state `busy` true while a call runs → CTA/keypad/code boxes disabled and progress shown; `Unavailable` → "Sin conexión. Intenta de nuevo."; unknown/server error → "Algo salió mal. Intenta de nuevo."; test: a second submit while `busy` sends no request.
- [X] T031 [US1] `S/feature/auth/start/StartViewModel.kt`: `HaveAccount` with no stored account → navigate `LoginPhone` (replaces the "No hay cuentas…" toast); update `HT/feature/auth/StartViewModelTest.kt`
- [X] T032 [US1] Update `HT/feature/auth/LoginViewModelTest.kt`: phone target, `Unavailable` keeps attempts, `AddAccount` → `LoginPhone`
- [X] T033 [US1] `A/MainActivity.kt` `onStart`: after `appLock.onForeground()`, `lifecycleScope.launch { deps.session.refreshCurrentUser() }` (FR-017)

**Checkpoint**: MVP — existing backend users log in and see their data.

## Phase 4: User Story 2 — Register with separate name fields (P1)

**Goal**: 4-step registration against the backend with first/paternal/maternal names.
**Independent test**: quickstart scenarios 6–7.

- [X] T034 [P] [US2] Tests `HT/data/remote/RemoteRegistrationRepositoryTest.kt`: request-otp 200 → `Sent`, 429 `{retry_after_seconds: 12}` → `TooSoon(12000)`, 422 → `InvalidPhone`; verify 200 keeps token in memory → `Confirmed`, 400 `{tries_left: 1}` → `Wrong(1)`, 410 → `Expired`, 404 → `NoCode`; register sends `Authorization: Bearer <phoneVerifiedToken>` and body with `firstName`, `lastNamePaternal`, `lastNameMaternal` (omitted when blank), `deviceId`, `termsVersion`; 201 → session open + `Created(id)`; 401 → `PhoneNotConfirmed`; 409 → `PhoneAlreadyRegistered`; 422 `fields.firstName` → `Invalid({FIRST_NAME})`; no automatic retry on I/O error (exactly 1 request recorded) → `Unavailable`
- [X] T035 [US2] Create `S/data/remote/RemoteRegistrationRepository.kt` (api-mapping rows `requestCode`, `confirmCode`, `register`): phone normalized to `+591XXXXXXXX`; `phoneVerifiedToken` kept in memory keyed by (phone, purpose), dropped after use; on `register` 201 reuse RemoteSessionRepository's post-login step (tokens, `GET me`, cache, session)
- [X] T036 [US2] `S/feature/auth/register/RegistrationViewModel.kt`: state `firstName`, `lastNamePaternal`, `lastNameMaternal` (remove `fullName`), events `FirstName/LastNamePaternal/LastNameMaternal`; `infoValid = firstName.trim() in 1..60 chars ∧ lastNamePaternal.trim() in 1..60 ∧ lastNameMaternal.trim().length ≤ 60 ∧ email valid ∧ terms`; `register → PhoneNotConfirmed` ⇒ step CODE keeping the draft (FR-012); `Unavailable` on every call → toast, data kept; `createdName = firstName`; set `TERMS_VERSION = "2026-11"` Also (FR-003, FR-003a): state `busy` true while a call runs → CTA/keypad/code boxes disabled and progress shown; `Unavailable` → "Sin conexión. Intenta de nuevo."; unknown/server error → "Algo salió mal. Intenta de nuevo."; test: a second submit while `busy` sends no request.
- [X] T037 [US2] `S/feature/auth/register/InfoStep.kt`: three `QTextField`s "Nombre", "Apellido paterno", "Apellido materno (opcional)" above email/terms, same validation style as today, scrollable column (constitution VI)
- [X] T038 [US2] `S/feature/auth/register/CodeStep.kt`: add `QLinkButton` "¿Ya tienes cuenta? Inicia sesión" under the resend line → `LoginPhone(prefill = phone)` (FR-011)
- [X] T039 [US2] Update `HT/feature/auth/RegistrationViewModelTest.kt`: three-field validation (maternal optional, 61 chars rejected), `PhoneNotConfirmed` returns to CODE with data kept, `Unavailable` keeps data

**Checkpoint**: new users register end to end.

## Phase 5: User Story 3 — Stay logged in safely and use the fingerprint (P2)

**Goal**: silent renewal; fingerprint = refresh; credentials only in secure storage.
**Independent test**: quickstart scenarios 8, 9, 13.

- [X] T040 [P] [US3] Tests in `HT/data/remote/RemoteSessionRepositoryTest.kt`: request with expired access → one `auth/refresh` then retried request; two concurrent requests → exactly one refresh; refresh 200 rotates stored token; refresh 401 → token cleared, session closed, `currentUser` emits null; `loginWithBiometrics` with stored token → `Success`, without token or refresh 401/423 → `SessionExpired`
- [X] T041 [US3] In `QullqiApi`/`RemoteSessionRepository`: bearer `refreshTokens` reads `tokens.refreshToken(currentUserId)`, calls `auth/refresh {refreshToken, deviceId}`, saves the rotated token; on failure clears token + `closeSession()` (US3-3); implement `loginWithBiometrics(userId)` per api-mapping row
- [X] T042 [US3] `S/feature/auth/login/LoginViewModel.kt`: `SessionExpired` → message "Ingresa tu PIN para continuar", fingerprint key hidden until next PIN success; update `HT/feature/auth/LoginViewModelTest.kt`
- [X] T043 [US3] Add `val sessionExpired: Flow<UserId>` to `S/domain/repository/SessionRepository.kt` (Sql impl: `emptyFlow()`); `RemoteSessionRepository` emits the user id (`MutableSharedFlow(extraBufferCapacity = 1)`) **only** when a renewal is rejected, never from `logout()`; `S/feature/main/MainFlow.kt` collects it once → toast "Tu sesión expiró. Ingresa tu PIN." + `onLogout(id)` (FR-016a); test in `HT/data/remote/RemoteSessionRepositoryTest.kt`: rejected refresh emits once, `logout()` emits nothing

## Phase 6: User Story 4 — Log out (P2)

**Independent test**: quickstart scenario 10.

- [X] T044 [P] [US4] Tests in `HT/data/remote/RemoteSessionRepositoryTest.kt`: logout sends `POST auth/logout {deviceId}` with bearer; local token, bearer cache and `current_session` cleared on 204 **and** on `Unavailable`
- [X] T045 [US4] Implement `RemoteSessionRepository.logout()` per api-mapping (always clear locally, FR-016); check `S/feature/main/MainPlaceholderViewModel.kt` still emits `LoggedOut(id)` and `HT/feature/main/MainPlaceholderViewModelTest.kt` passes

## Phase 7: User Story 5 — Recover the PIN (P2)

**Independent test**: quickstart scenario 5. ⚠️ Must not reach a release until backend S2 is fixed.

- [X] T046 [P] [US5] Tests in `HT/data/remote/RemoteRegistrationRepositoryTest.kt`: `requestCode/confirmCode` with `PIN_RESET` send `purpose: "PIN_RESET"`; `resetPin` sends bearer PIN_RESET token + `{newPin, deviceId}`; 200 → `Success`, cached attempts 3, user's stored refresh token cleared; 401 → `PhoneNotConfirmed`; 422 → `InvalidPin`; I/O → `Unavailable`
- [X] T047 [US5] Implement `RemoteRegistrationRepository.resetPin` per api-mapping
- [X] T048 [US5] `S/feature/auth/recover/RecoverPinViewModel.kt` + route `RecoverPin(phone: String)` in `S/feature/auth/AuthFlow.kt`: take the phone instead of `userId`; use `CodePurpose.PIN_RESET`; handle `Unavailable`; after success navigate to `Login(phone = phone)` (or `Login(userId)` when cached); update `HT/feature/auth/RecoverPinViewModelTest.kt` Also (FR-003, FR-003a): state `busy` true while a call runs → CTA/keypad/code boxes disabled and progress shown; `Unavailable` → "Sin conexión. Intenta de nuevo."; unknown/server error → "Algo salió mal. Intenta de nuevo."; test: a second submit while `busy` sends no request.

## Phase 8: User Story 6 — Current user for the main area (P3)

**Independent test**: change a dev user's status in the backend, return to the app.

- [X] T049 [P] [US6] Tests in `HT/data/remote/RemoteSessionRepositoryTest.kt`: `refreshCurrentUser()` 200 with changed `verificationStatus` → `currentUser` emits the new status; `Unavailable` → last value kept; `lastNameMaternal` absent → display name "first paternal"; `HT/data/CurrentUserContractTest.kt` still passes (M4 contract unchanged)

## Phase 9: Polish & cross-cutting

- [X] T050 [P] Grep guard: no `Log.`/`println` of tokens, PIN or code in `S/data/remote/**`, `shared/src/androidMain/**`, `A/**`; Ktor `Logging` only when `debug` (SC-004)
- [X] T051 [P] Update `CLAUDE.md` §2 tree (`data/remote/`, `Device.sq`, `1.sqm`, `databases/1.db`, `KeystoreTokenStore`, `src/debug/AndroidManifest.xml`) and §14 status note (seed only in tests, schema v2)
- [X] T052 Full gates: `./gradlew :shared:verifyCommonMainQullqiDatabaseMigration :shared:testAndroidHostTest`, CI command from CLAUDE.md §16.1, `./gradlew :androidApp:assembleDebug assembleRelease` (portrait guard both)
- [ ] T053 Device walkthrough: quickstart scenarios 1–13 against the local dev backend on the physical device; in scenario 2 measure time from 4th digit to main area (SC-001 < 3 s); record results in `specs/004-backend-auth-integration/quickstart.md` (owner)
- [ ] T054 Spec header `**Status**: Ready`; `scripts/branches.sh check feature/004-backend-auth-integration` prints OK

## Phase 10: Backend contract mismatches found on device (2026-11-17)

**Cause**: the backend code differs from its own `docs/integration-android/endpoints.md`:
- `GET /me` returns the id as **`id`** (`MeResponse(UUID id, …)`), the doc says `userId` → login fails with
  `JsonConvertException: Field 'userId' is required … at path: $.data`, shown as "No pudimos conectarnos".
- `invalid_pin` details use **`attemptsLeft`** (`PinException`: `Map.of("attemptsLeft", …)`), the doc says
  `attempts_left` → the app would read 0 and show a wrong PIN as **Locked**.
- Checked and matching: `tries_left`, `retry_after_seconds`, `fields`, request bodies, `TokenResponse`,
  `VerificationStatus`/`UserStatus` enums.

**Goal**: the app accepts both spellings (tolerant reader) and the backend is asked to align code and doc.
**Independent test**: login on device with `+59170000001` / `1234` reaches the main area; a wrong PIN shows
"Te quedan 2 intentos." (quickstart scenarios 2–3).

- [X] T056 [US1] In `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/Dto.kt`: annotate `MeDto.userId` with `@JsonNames("userId", "id")` (`@OptIn(ExperimentalSerializationApi::class)`) so `/me` decodes with the backend's `id` and the documented `userId`
- [X] T057 [US1] In `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/Dto.kt`: change `JsonObject?.int(key)` to `int(vararg keys)` returning the first present key; in `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/RemoteSessionRepository.kt` read `r.details.int("attempts_left", "attemptsLeft")`; in `RemoteRegistrationRepository.kt` read `int("tries_left", "triesLeft")` and `int("retry_after_seconds", "retryAfterSeconds")`
- [X] T058 [US1] Tests in `shared/src/androidHostTest/kotlin/com/codewave/qullqi/data/remote/RemoteSessionRepositoryTest.kt`: `MockBackend.me` variant with `"id"` instead of `"userId"` logs in with `Success`; `invalid_pin` with `{"attemptsLeft":2}` → `WrongPin(2)` (not `Locked`); add the `idKey` parameter to `me()` in `MockBackend.kt`
- [X] T059 Add both mismatches to `docs/backend-requirements/endpoints-change-request.md` (branch `docs/003-backend-requirements`) as "C0 · Contract drift": ask the backend to return `userId` in `MeResponse` (or fix the doc) and to use `attempts_left` like the other `details` keys (or fix the doc); the app tolerates both meanwhile
- [ ] T060 Re-run `./gradlew :shared:testAndroidHostTest` and quickstart scenarios 2–3 on the device

## Phase 11: Crash on wrong PIN — backend values written unchecked into the local cache (2026-11-17)

**Cause (app)**: `invalid_pin` sends `attemptsLeft: 4` (the backend allows 5 attempts); the app wrote it
into `user.pin_attempts_left` (`CHECK BETWEEN 0 AND 3`) on the main thread → `SQLiteConstraintException`
crash. Same class of bug as the previous FK crash: backend data reaching CHECK/FK-constrained columns or
throwing APIs without validation.
**Independent test**: wrong PIN on device shows "Te quedan N intentos." with the backend's N, no crash.

- [X] T061 [US1] `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/RemoteSessionRepository.kt`: `setAttempts` clamps to the cache range `coerceIn(0, 3)` (UI keeps the backend number from `WrongPin(n)`); `attemptsLeft <= 0` → `Locked`
- [X] T062 [US1] Same file: every local write (`setAttempts`, cache user + open session, `closeSession`, logout) goes through `local { }` (IO dispatcher, exceptions → null); `establish` caches inside one transaction **before** saving the refresh token and returns null → `Unavailable` instead of crashing
- [X] T063 [US1] Same file: `/me` phone normalized to `+591[67]XXXXXXX`; if it cannot be stored, the login/registration phone (`phoneHint`) is used; otherwise the operation is `Unavailable`
- [X] T064 [US3] `shared/src/androidMain/kotlin/com/codewave/qullqi/data/KeystoreTokenStore.kt`: `saveRefreshToken` catches Keystore/cipher failures (credential dropped, PIN required next time) instead of crashing
- [X] T065 [US2] `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/RemoteRegistrationRepository.kt`: `wrong_code` with `tries_left <= 0` → `Expired` (never "Te quedan 0 intentos")
- [X] T066 Tests in `shared/src/androidHostTest/kotlin/com/codewave/qullqi/data/remote/RemoteSessionRepositoryTest.kt`: `attemptsLeft: 4` → `WrongPin(4)` with cache 3; `-1` → `Locked`; `/me` with a non-Bolivian phone uses the login phone, and without a hint is `Unavailable` with no session

## Phase 12: Registration sends one display name; legal names come from KYC (2026-11-17)

**Decision (owner)**: step 3 "Cuéntanos sobre ti" asks only for a **display name**; first name and
surnames are removed from the app, because Persona (KYC, M3) fills the legal names through the
backend webhook.
**Backend (already changed, `BackendQullpi` `f102c1d`/`830814e`)**: `POST /auth/register` requires
`displayName` (1–100 chars); `firstName`, `lastNamePaternal`, `lastNameMaternal` optional.
`GET /me` adds `displayName` (always present) and returns the legal names as **null** until KYC is
approved.
**Risk now**: `MeDto.firstName`/`lastNamePaternal` are non-null in the app → every `/me` of a user
without KYC fails to decode → login shows "No pudimos conectarnos". T072 fixes it.
**Independent test**: register a new number with "Persona Prueba" as name → "Cuenta creada", main
area greets "Persona"; login with `+59170000001` still works.

- [ ] T067 [US2] Spec docs: in `specs/004-backend-auth-integration/spec.md` replace FR-009 with "Registration step 3 MUST ask for a display name (trimmed 1–100 characters, required), email and terms; legal first name and surnames are NOT asked: they come from the identity verification (Persona → backend webhook, M3)"; add a Clarifications bullet (2026-11-17) with the owner's decision; update the "Registration draft" and "Current user" key entities; in `specs/004-backend-auth-integration/data-model.md` replace the three registration fields with `displayName` and note that the `user` name columns hold the legal names from `/me` (nullable until KYC); in `specs/004-backend-auth-integration/contracts/api-mapping.md` change the `register` body to `{displayName, email, pin, deviceId, termsVersion}` and the `/me` mapping to `full_name = displayName`
- [ ] T068 [US2] Shared contract `shared/src/commonMain/kotlin/com/codewave/qullqi/domain/repository/RegistrationRepository.kt`: `RegistrationForm` fields `firstName`, `lastNamePaternal`, `lastNameMaternal` → one `displayName: String`; `RegistrationField` `FIRST_NAME, LAST_NAME_PATERNAL, LAST_NAME_MATERNAL` → `NAME` (keep `EMAIL, TERMS, PIN, PIN_MISMATCH`)
- [ ] T069 [US2] `shared/src/commonMain/kotlin/com/codewave/qullqi/data/SqlRegistrationRepository.kt`: `nameErrors` → `NAME` when `displayName.trim().length !in 1..100` (`NAME_MAX = 100`); insert `full_name = displayName.trim()` and null name columns
- [ ] T070 [US2] `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/Dto.kt` + `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/RemoteRegistrationRepository.kt`: `RegisterBody(displayName, email, pin, deviceId, termsVersion)` (drop the three name fields); 422 `fields.displayName` → `NAME`, remove the old `FIELD_MAP` entries for the split names
- [ ] T071 [US2] `shared/src/commonMain/kotlin/com/codewave/qullqi/feature/auth/register/RegistrationViewModel.kt` + `shared/src/commonMain/kotlin/com/codewave/qullqi/feature/auth/register/InfoStep.kt`: state `displayName` (replace `firstName`/`lastNamePaternal`/`lastNameMaternal` and their events with `RegEvent.Name`), capped at 100 chars, `infoValid = displayName.trim().length in 1..100 ∧ email valid ∧ terms`; one `QTextField` label "¿Cómo te llamamos?" (help: "Así te saludaremos en la app."); `createdName = displayName.trim().substringBefore(' ')`
- [ ] T072 [US1] `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/Dto.kt` + `shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/RemoteSessionRepository.kt`: `MeDto` gets `displayName: String? = null` and `firstName`, `lastNamePaternal` become `String? = null`; cache `full_name = displayName?.trim()` → else `"$firstName $lastNamePaternal".trim()` → else `"Cliente"`; store the legal names (nullable) in `first_name`, `last_name_paternal`, `last_name_maternal`; `CurrentUser.displayName` (M4 contract) unchanged
- [ ] T073 Tests: update `shared/src/androidHostTest/kotlin/com/codewave/qullqi/data/remote/MockBackend.kt` (`me()` with `displayName` and null legal names by default), `RemoteRegistrationRepositoryTest.kt` (body has `displayName`, no split names; 422 → `NAME`), `RemoteSessionRepositoryTest.kt` (`/me` with null legal names logs in and shows the display name; no `displayName` → legal names → "Cliente"), `shared/src/androidHostTest/kotlin/com/codewave/qullqi/data/RegistrationRepositoryTest.kt` and `shared/src/androidHostTest/kotlin/com/codewave/qullqi/feature/auth/RegistrationViewModelTest.kt` (single field, 101 chars rejected)
- [ ] T074 [P] `docs/backend-requirements/endpoints-change-request.md` (branch `docs/003-backend-requirements`): mark C4's split-name request as superseded by the display-name decision (legal names from KYC)
- [ ] T075 Run `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`, install on the device and re-check quickstart scenarios 2 and 6

## Release readiness (not part of this feature's integration — run before the first `master` release)

- [ ] T055 Before the first promotion to `master` (constitution v2.1.0): set `android:usesCleartextTraffic="false"` in `androidApp/src/main/AndroidManifest.xml` (optionally `"true"` only in `androidApp/src/debug/AndroidManifest.xml`), set `qullqi.api.url.release` to the production `https://` URL, and confirm `./gradlew :androidApp:assembleRelease` + device login over HTTPS

## Dependencies & execution order

- Phase 1 → Phase 2 (T003 before T007; T009 after T007–T008) → stories.
- US1 (Phase 3) is the base for US3, US4, US6 (same `RemoteSessionRepository`).
- US2 depends only on Phase 2 + T027's post-login step (T035 reuses it).
- US5 depends on US2's repository (T035).
- Polish after the stories in scope.

## Parallel examples

- Phase 1: T005 alongside T001–T004.
- Phase 2: T008, T011, T017, T018, T020, T021 in parallel (different files).
- US1: T026 (tests) while T028/T029 (routes/screen).
- US2: T034 while T037/T038 (UI).
- After US1: US3 (T040–T043), US4 (T044–T045), US6 (T049) in parallel with US2.

## Implementation strategy

1. MVP = Phase 1 + 2 + US1: existing backend users log in (device test scenario 2).
2. US2 registration → US3 renewal/fingerprint → US4 logout → US6 current user refresh.
3. US5 recovery last; keep behind the release note until backend S2 is fixed.
4. Commit per phase; never commit on `develop`.
