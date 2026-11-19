# Tasks: Identity Verification with Persona (M3)

**Input**: `specs/005-kyc-persona/` (spec, plan, research, data-model, contracts, quickstart)
**Tests**: required by constitution VII (ViewModel/domain) and the plan (repository on `MockEngine`,
migration verify). Run `./gradlew :shared:testAndroidHostTest`.
**Precondition**: spec 004 integrated into `develop`; then `git merge develop` into this branch and set
the spec header `**Depends on**: 004-backend-auth-integration waiting (integrated)`, `**Status**: In Progress`.

Paths: `S` = `shared/src/commonMain/kotlin/com/codewave/qullqi`, `SQ` =
`shared/src/commonMain/sqldelight/com/codewave/qullqi/db`, `HT` =
`shared/src/androidHostTest/kotlin/com/codewave/qullqi`, `A` = `androidApp/src/main/kotlin/com/codewave/qullqi`.

## Phase 1: Setup (shared build files — coordinate)

- [X] T001 ~~superseded by Phase 8 (WebView)~~ Look up the latest **stable** `androidx.browser:browser` on Google Maven (no alpha/beta/RC); add `browser = "<version>"` and `androidx-browser = { module = "androidx.browser:browser", version.ref = "browser" }` to `gradle/libs.versions.toml`; add `implementation(libs.androidx.browser)` to `androidApp/build.gradle.kts`
- [X] T002 Generate the v2 schema snapshot **before editing any `.sq`**: `./gradlew :shared:generateCommonMainQullqiDatabaseSchema` → commit `shared/src/commonMain/sqldelight/databases/2.db`
- [X] T003 Baseline: `./gradlew :shared:testAndroidHostTest :androidApp:compileDebugKotlin` green

## Phase 2: Foundational (blocks every story)

**Remove the spec 002 local KYC (research R4)**

- [X] T004 Delete `SQ/KycAttempt.sq`; remove `DELETE FROM kyc_attempt;` from `deleteAll` and any `kyc_attempt` insert from `insertSamples` in `SQ/Seed.sq`; create `SQ/2.sqm` with `DROP TRIGGER IF EXISTS kyc_attempt_final_sets_user_status; DROP INDEX IF EXISTS kyc_attempt_one_open; DROP INDEX IF EXISTS kyc_attempt_history; DROP TABLE IF EXISTS kyc_attempt;`; run `./gradlew :shared:verifyCommonMainQullqiDatabaseMigration` until it passes
- [X] T005 Delete `S/data/SqlKycRepository.kt`, `S/data/MockKycProcessor.kt`, `HT/data/KycRepositoryTest.kt`; in `HT/data/CurrentUserContractTest.kt` replace the KYC-driven status change with a direct `UPDATE user SET verification = 'VERIFIED' WHERE id = …` and keep the contract assertions; update `HT/data/SchemaIntegrityTest.kt`/`HT/data/SeedDataTest.kt` table lists (no `kyc_attempt`)

**New domain contract (contracts/kyc-repository.md — shared area)**

- [X] T006 Replace `S/domain/model/Kyc.kt` with: `enum class DocumentType { CI, PASSPORT }`, `enum class KycStatus { CREATED, PENDING, APPROVED, DECLINED }`, `enum class DeclineReason { DATA_MISMATCH, DOCUMENT, IDENTITY, OTHER }`, `data class SimpleDate(year, month, day)` with `iso()` → `"YYYY-MM-DD"` and `companion fun parseDigits(d: String): SimpleDate?` (8 digits DDMMYYYY; month 1–12; day ≤ month length; Feb 29 only in leap years), `fun SimpleDate.isAdultOn(today: SimpleDate): Boolean` (birth < today ∧ age ≥ 18, birthday reached), `fun documentNumberValid(n: String) = Regex("^[A-Za-z0-9]{5,15}$").matches(n.trim())`, `data class Verification(status, pageUrl: String?, declineReason: DeclineReason?)`
- [X] T007 Replace `S/domain/repository/KycRepository.kt` with `KycRepository { suspend fun current(): KycRead; suspend fun start(type, number, birthDate: SimpleDate): KycStart }`, `KycRead { Found(verification: Verification?), Unavailable }`, `KycStart { Started(pageUrl), AlreadyVerified, InProgress, DocumentTaken, Invalid(fields: Set<KycField>), Refused, Unavailable }`, `enum class KycField { NUMBER, BIRTH_DATE, TYPE }`
- [X] T008 [P] Create `S/domain/system/ExternalPageOpener.kt`: `fun interface ExternalPageOpener { fun open(url: String) }` and `fun interface Today { fun date(): SimpleDate }`; `object NoPageOpener : ExternalPageOpener { override fun open(url: String) = Unit }`
- [X] T009 [P] Tests `HT/domain/KycRulesTest.kt`: `parseDigits("15041992")` ok, `"31022020"` null, `"29022024"` ok, `"29022023"` null; `isAdultOn` exactly 18 today true, one day before false, future false; `documentNumberValid` 5/15 chars ok, 4/16/space false

**Remote + wiring**

- [X] T010 In `S/data/remote/Dto.kt` add `KycStartBody(ciType: String, ciNumber: String, birthDate: String)` and `KycDto(kycStatus: String, hostedUrl: String? = null, declineReason: String? = null)`; in `S/data/remote/QullqiApi.kt` add `kycCurrent(): ApiResult<KycDto?>` (`GET my/kyc`, `data: null` → `Ok(null)`) and `kycStart(body): ApiResult<KycDto>` (`POST my/kyc`), both with the session bearer (no `skipSession`)
- [X] T011 Create `S/data/remote/RemoteKycRepository.kt` exactly per `contracts/api-mapping.md` (unknown status → `PENDING`; reason mapping R7: `data_mismatch`→DATA_MISMATCH, `document_unreadable`/`document_expired`→DOCUMENT, `identity_not_verified`/`selfie_mismatch`→IDENTITY, else OTHER; `POST` 200 without `hostedUrl` → `Unavailable`)
- [X] T012 [P] Tests `HT/data/remote/RemoteKycRepositoryTest.kt` with `MockBackend`: `GET` null → `Found(null)`; each status and reason mapping; `POST` body `{"ciType":"CI","ciNumber":"1234567","birthDate":"1992-04-15"}` with `Authorization: Bearer access-1`; 409 `kyc_already_verified`/`kyc_in_progress`/`ci_taken`, 403 `kyc_rejected`, 422 `fields.ciNumber` → expected results; offline → `Unavailable` and exactly 1 `POST`
- [X] T013 `S/AppDependencies.kt`: `kyc = RemoteKycRepository(api)` in `create`; add fields `pageOpener: ExternalPageOpener = NoPageOpener` and `today: Today`; `create(…, today: Today)`; `createInMemory` gets a fake `KycRepository` that returns `Found(null)` (tests override); update `HT/feature/TestSupport.kt` (fixed `Today { SimpleDate(2026, 11, 17) }`)
- [X] T014 `A/QullqiApplication.kt`: `today = Today { java.time.LocalDate.now().let { SimpleDate(it.year, it.monthValue, it.dayOfMonth) } }`

**Checkpoint**: builds, tests green, migration verified, no local KYC left.

## Phase 3: User Story 1 — Verify my identity end to end (P1) 🎯 MVP

**Goal**: intro → document → Persona (Custom Tab) → waiting → result.
**Independent test**: quickstart scenarios 1–3 and 6–9.

- [X] T015 ~~superseded by Phase 8 (WebView)~~ [P] [US1] Create `A/CustomTabPageOpener.kt`: `class CustomTabPageOpener(private val activity: Activity) : ExternalPageOpener` using `CustomTabsIntent.Builder().setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF161826.toInt()).build()).setShareState(CustomTabsIntent.SHARE_STATE_OFF).setUrlBarHidingEnabled(true).setShowTitle(true).build().launchUrl(activity, Uri.parse(url))` (falls back to the default browser automatically)
- [X] T016 ~~superseded by Phase 8 (WebView)~~ [P] [US1] Create `A/KycReturnActivity.kt`: in `onCreate` start `AuthActivity` with `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` and **no `start` extra**, then `finish()` + `noTransition(closing = true)`; ignore all intent data. Security (spec 003 FR-002a): an existing AuthActivity is reused (the open tab is cleared, the KYC flow continues on resume); if none exists (cold start, any app firing the link) the new AuthActivity starts at the splash → login, **never** directly in KYC. Register in `androidApp/src/main/AndroidManifest.xml`: `exported="true"`, `screenOrientation="portrait"`, `theme="@style/Theme.Qullqi"` (opaque: translucent + portrait crashes on API 26), `excludeFromRecents="true"`, `noHistory="true"`, intent-filter `VIEW` + `DEFAULT` + `BROWSABLE` + `<data android:scheme="qullqi" android:host="kyc" android:path="/result" />`; `./gradlew :androidApp:verifyPortraitActivitiesDebug` must list 5 activities, all portrait
- [X] T017 ~~superseded by Phase 8 (WebView)~~ [US1] `A/AuthActivity.kt`: inject `pageOpener = CustomTabPageOpener(this)` in the existing `dependencies.copy(…)`; override `onNewIntent` to `setIntent(it)` only (the flow reacts to resume, research R2)
- [X] T018 [US1] Create `S/feature/kyc/KycViewModel.kt` (`FlowViewModel<KycState, KycEffect>`; constructor `kyc: KycRepository, session: SessionRepository, opener: ExternalPageOpener, today: Today`): state per data-model.md "UI state" (`route`, `privacyAccepted`, `pending`, `documentType` = CI, `documentNumber`, `birthDigits` 0–8 digits, `busy`, `waitingPhase`, `result`); on init `current()` and route per FR-002; events `AcceptPrivacy(Boolean)`, `Continue`, `Type`, `Number`, `BirthDigits`, `Submit`, `Resumed` (from lifecycle), `CheckNow`, `ReopenProvider`, `Retry`, `Finish`, `Back`; `Submit` validates (`documentNumberValid` → "Revisa el número de documento.", `parseDigits` → "Revisa la fecha.", `isAdultOn(today)` → "Debes ser mayor de 18 años para verificar tu identidad."), keeps per-field errors in state (`numberError`, `birthError`), maps `KycStart.Invalid(fields)` to the same field errors (`NUMBER`/`TYPE` → number field, `BIRTH_DATE` → birth field), calls `start` once (`busy`, never retried), on `Started` calls `opener.open(url)` and sets `Waiting(InProvider)`; `Resumed` while `InProvider` starts polling per research R3 (immediately, then `delay(5_000)`, max 60 s); results per FR-009/FR-010/FR-011 with the exact texts of the spec and research R7; `Finish` calls `session.refreshCurrentUser()` then emits `Done`
- [X] T019 [US1] Create screens in `S/feature/kyc/`: `KycIntroScreen.kt` (requirements list + privacy text FR-001 + checkbox, CTA "Continuar"), `KycDocumentScreen.kt` (CI/Pasaporte segmented choice, number field, birth field with numeric keyboard and `DD/MM/AAAA` mask, each showing its error text from state, CTA "Continuar a la verificación" disabled while `busy` or invalid), `KycWaitingScreen.kt` (progress + "Comprobando tu verificación…", buttons "Ya terminé" and "Volver a la verificación"), `KycResultScreen.kt` (approved / in review / rejected with reason + "Intentar de nuevo" / refused "Contacta a soporte"; CTA "Ir al inicio"); reuse the registration `StepScaffold` look (`QScreen`, `Q.colors`, `Q.type`, `QSpacing`, `Ph` icons) — no literal colors or sizes
- [X] T020 [US1] Create `S/feature/kyc/KycFlow.kt`: `@Composable fun KycFlow(deps: AppDependencies, onDone: () -> Unit)` with one `KycViewModel` (`viewModel { … }`) and a `FlowNavigator<KycRoute>` + `FlowHost` from `S/navigation/` (same push/back transitions as registration, spec 003 FR-029); the ViewModel emits navigation effects (`ToDocument`, `ToWaiting`, `ToResult`, `BackToIntro`) that drive the navigator, forwards `Lifecycle.Event.ON_RESUME` as `KycEvent.Resumed` (`LocalLifecycleOwner`), handles back (`BackHandler`: Document → Intro, Intro → `onDone()`), collects `Done` → `onDone()`; in `S/feature/auth/AuthFlow.kt` change the default `kycEntry` to `{ onDone -> KycFlow(deps, onDone) }` (signature unchanged); delete `S/feature/auth/KycPlaceholderScreen.kt`
- [X] T021 [P] [US1] Tests `HT/feature/kyc/KycViewModelTest.kt` (fake `KycRepository`, fake opener recording URLs, fixed `Today`): privacy gate; invalid number/date/under-18 blocks submit with no `start` call; `Submit` → opener called once with the URL; `Resumed` → polls; `APPROVED` → Approved; `DECLINED(DATA_MISMATCH)` → Rejected with "Los datos no coinciden con tu documento."; `PENDING` for 60 s virtual time → InReview after 13 reads; `start` offline → message, data kept, 1 call; `Invalid({BIRTH_DATE})` → birth field error and still on Document; `Finish` → `refreshCurrentUser` then `Done`

- [X] T021a [US1] Session expired during KYC (spec edge case, spec 004 FR-016a): in `S/feature/kyc/KycFlow.kt` collect `deps.session.sessionExpired` and call a new `onSessionExpired: (UserId) -> Unit` parameter; in `S/feature/auth/AuthFlow.kt` the default `kycEntry` passes `{ id -> actions.toast.show(AuthMessages.SESSION_EXPIRED); actions.navigator.resetTo(AuthRoute.Login(id)) }`; test in `HT/feature/kyc/KycViewModelTest.kt` or a flow-level test that a rejected renewal leaves KYC for login once

**Checkpoint**: MVP — a not verified user completes verification on the device.

## Phase 4: User Story 2 — Resume an unfinished verification (P2)

**Independent test**: quickstart scenarios 4–5.

- [X] T022 [US2] `S/feature/kyc/KycViewModel.kt`: entry with `CREATED` → Intro shows "Tienes una verificación pendiente" + "Continuar verificación" (`ReopenProvider`: fresh `current()`, open its `pageUrl`, `Waiting(InProvider)`); polling sees `CREATED` → `NotFinished` with "No terminaste la verificación" + "Volver a la verificación"; `start` → `InProgress` → re-read and resume; entry with `PENDING` → Result(InReview); `APPROVED` → Result(Approved) "Ya verificaste tu identidad"
- [X] T023 [US2] `S/feature/kyc/KycIntroScreen.kt`: pending variant (title, help, CTA "Continuar verificación")
- [X] T024 [P] [US2] Tests in `HT/feature/kyc/KycViewModelTest.kt`: `CREATED` entry → resume uses the latest `pageUrl` from a second `current()`; polling `CREATED` → NotFinished; `InProgress` on submit → resume

## Phase 5: User Story 3 — Retry after a rejection (P2)

**Independent test**: quickstart scenario 6.

- [X] T025 [US3] `S/feature/kyc/KycViewModel.kt`: `Retry` → Document with empty number/date (type kept); `Refused` → Result(Refused) "Contacta a soporte" with "Ir al inicio"; `DocumentTaken` → field error "Este documento ya está asociado a otra cuenta."; entry with `DECLINED` → Intro (new attempt allowed)
- [X] T026 [P] [US3] Tests in `HT/feature/kyc/KycViewModelTest.kt`: retry clears fields; refused; document taken message

## Phase 6: User Story 4 — Status always current (P3)

- [X] T027 [US4] Verify `A/MainActivity.kt` `onStart` already refreshes `/me` (spec 004 T033) and `KycViewModel.Finish` refreshes before `onDone`; add a test in `HT/feature/kyc/KycViewModelTest.kt` that `Finish` happens after `refreshCurrentUser` completes

## Phase 7: Polish & cross-cutting

- [X] T028 [P] Grep guard: no `pageUrl`/`hostedUrl` logged or stored (`S/feature/kyc/**`, `S/data/remote/**`, `A/**`); `adb shell run-as com.codewave.qullqi ls -R` shows no images (SC-004)
- [X] T029 [P] Update `CLAUDE.md` §2 tree (`feature/kyc/`, `KycReturnActivity`, `CustomTabPageOpener`, `2.sqm`, `databases/2.db`, `KycAttempt.sq` removed) and §14 status (schema v3); `docs/references-views` untouched
- [X] T030 Full gates: `./gradlew :shared:verifyCommonMainQullqiDatabaseMigration :shared:testAndroidHostTest`, CI command (CLAUDE.md §16.1), `./gradlew :androidApp:assembleDebug assembleRelease`
- [ ] T031 Device walkthrough: quickstart scenarios 1–15 with the Persona sandbox (owner); measure "Verificar" → provider page (SC-001 < 1 min, ≤ 3 screens); record results in `specs/005-kyc-persona/quickstart.md`
- [ ] T032 Spec header `**Status**: Ready`; `scripts/branches.sh check feature/005-kyc-persona` → OK

## Phase 8: Migration to an in-app WebView (rev. 2026-11-17, supersedes T001, T015–T017)

**Decision**: spec Clarifications 2026-11-17 (WebView, camera only), constitution v2.2.0, research R1/R2.
T001/T015/T016/T017 (Custom Tab, `KycReturnActivity`, `androidx.browser`) were built and are now
replaced. **Independent test**: quickstart scenarios 2–4, 12, 14, 15.

**Contract (shared — coordinate)**

- [X] T033 [US1] Replace `shared/src/commonMain/kotlin/com/codewave/qullqi/domain/system/ExternalPageOpener.kt` with `shared/src/commonMain/kotlin/com/codewave/qullqi/domain/system/ProviderPage.kt`: `interface ProviderPage { suspend fun open(url: String): PageOpen; fun openCameraSettings() }`, `sealed interface PageOpen { data object Opened : PageOpen; data class CameraDenied(val permanent: Boolean) : PageOpen }`, `object NoProviderPage : ProviderPage` (returns `Opened`, no-op settings) and keep `fun interface Today`; in `shared/src/commonMain/kotlin/com/codewave/qullqi/AppDependencies.kt` rename `pageOpener: ExternalPageOpener = NoPageOpener` → `providerPage: ProviderPage = NoProviderPage`
- [X] T034 [US1] `shared/src/commonMain/kotlin/com/codewave/qullqi/feature/kyc/KycViewModel.kt`: constructor takes `ProviderPage`; `openProvider(url)` launches `providerPage.open(url)` → `Opened` → `Waiting(InProvider)` (navigate **before** opening, as today); `CameraDenied(permanent)` → stay on Document (or Intro when resuming) with `cameraDenied = Ask|Settings` and remember the URL in memory only; new event `AllowCamera`: `Settings` → `providerPage.openCameraSettings()`, `Ask` → retry `open(lastUrl)`; clear `cameraDenied` on success. Add `cameraDenied: CameraDenied? = null` (`enum class CameraDenied { Ask, Settings }`) to `KycState`
- [X] T035 [US1] `shared/src/commonMain/kotlin/com/codewave/qullqi/feature/kyc/KycScreens.kt`: when `state.cameraDenied != null` show on Document/Intro the block "Necesitamos tu cámara para verificarte" (help "Para fotografiar tu documento y tomar tu selfie.") with `QPrimaryButton("Permitir cámara")` → `KycEvent.AllowCamera`; `shared/src/commonMain/kotlin/com/codewave/qullqi/feature/kyc/KycFlow.kt` passes `deps.providerPage`

**Android**

- [X] T036 [US1] Delete `androidApp/src/main/kotlin/com/codewave/qullqi/CustomTabPageOpener.kt`, `androidApp/src/main/kotlin/com/codewave/qullqi/KycReturnActivity.kt` and its `<activity>` (exported deep-link filter) in `androidApp/src/main/AndroidManifest.xml`; remove `androidx-browser` / `browser` from `gradle/libs.versions.toml` and `implementation(libs.androidx.browser)` from `androidApp/build.gradle.kts`
- [X] T037 [US1] Create `androidApp/src/main/kotlin/com/codewave/qullqi/KycWebViewActivity.kt` (`ComponentActivity`): if `savedInstanceState != null` → `finish()` (no page restored); read `EXTRA_URL`; Compose `QullqiTheme` screen = top bar (`Q.colors.bg`, `Ph.X` close → `finish()`, title "Verificación de identidad") + `AndroidView(WebView)`; `BackHandler` → `goBack()` if possible else `finish()`. WebView per research R1: `javaScriptEnabled`, `domStorageEnabled`, `mediaPlaybackRequiresUserGesture = false`, `allowFileAccess = false`, `allowContentAccess = false`, no `addJavascriptInterface`; `WebViewClient.shouldOverrideUrlLoading`: `qullqi://kyc/result` → `finish()`; `https` and host == start host or ends with `.withpersona.com` → load; else `startActivity(Intent(ACTION_VIEW, uri))` and do not load; `onReceivedSslError` → `handler.cancel()`; `WebChromeClient.onPermissionRequest`: grant only `PermissionRequest.RESOURCE_VIDEO_CAPTURE` for an allowed origin when `CAMERA` is granted, else `deny()`; `onShowFileChooser` → `false`; `onDestroy`: `CookieManager.getInstance().removeAllCookies(null)`, `WebStorage.getInstance().deleteAllData()`, `clearCache(true)`, `destroy()`; never log the URL
- [X] T038 [US1] `androidApp/src/main/AndroidManifest.xml`: `<uses-permission android:name="android.permission.CAMERA" />`, `<uses-feature android:name="android.hardware.camera" android:required="false" />`, `<activity android:name=".KycWebViewActivity" android:exported="false" android:screenOrientation="portrait" android:theme="@style/Theme.Qullqi" android:configChanges="orientation|screenSize|screenLayout|keyboardHidden" />`; `./gradlew :androidApp:verifyPortraitActivitiesDebug` → 5 activities, all portrait
- [X] T039 [US1] Create `androidApp/src/main/kotlin/com/codewave/qullqi/AndroidProviderPage.kt`: `class AndroidProviderPage(activity: ComponentActivity) : ProviderPage` registering `ActivityResultContracts.RequestPermission()` in its constructor (must be built in `AuthActivity.onCreate` before `STARTED`); `open(url)`: granted → start `KycWebViewActivity` with `EXTRA_URL` → `Opened`; else request and await a `CompletableDeferred<Boolean>`; denied → `CameraDenied(permanent = !shouldShowRequestPermissionRationale(CAMERA))`; `openCameraSettings()` → `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `package:` URI. In `androidApp/src/main/kotlin/com/codewave/qullqi/AuthActivity.kt` replace `pageOpener = CustomTabPageOpener(this)` with `providerPage = AndroidProviderPage(this)` (created in `onCreate`) and remove the `onNewIntent` override

**Tests**

- [X] T040 [P] [US1] `shared/src/androidHostTest/kotlin/com/codewave/qullqi/feature/kyc/KycViewModelTest.kt`: fake `ProviderPage` (records URLs, scripted `PageOpen`); `CameraDenied(false)` → stays on Document with `cameraDenied = Ask`, no Waiting; `AllowCamera` → `open` again with the same URL → `Opened` → Waiting; `CameraDenied(true)` → `Settings` and `AllowCamera` calls `openCameraSettings`; existing tests migrated to the fake
- [X] T041 Full gates: `./gradlew :shared:testAndroidHostTest`, CI command (CLAUDE.md §16.1), `:androidApp:assembleDebug assembleRelease` (portrait guard), grep: no `addJavascriptInterface`, no `setWebContentsDebuggingEnabled(true)`, no URL logging in `androidApp/src/main/kotlin/com/codewave/qullqi/KycWebViewActivity.kt`
- [X] T042 Update `CLAUDE.md` §2 tree (`KycWebViewActivity.kt`, `AndroidProviderPage.kt`; remove `KycReturnActivity.kt`, `CustomTabPageOpener.kt`) and mark T001/T015/T016/T017 as superseded in this file

## Phase 9: Remove the document form (rev. 2026-11-17, option A)

**Decision**: spec FR-003/FR-003a — the provider reads type, number and birth date; the backend checks
age and uniqueness on verified data. Supersedes T006 rules (dates/number), T009, the document screen
of T019 and the field errors of T018/T025.

- [X] T043 [US1] Domain/contract: `KycRepository.start()` without parameters; remove `KycStart.Invalid`, `KycStart.DocumentTaken`, `KycField`, `DocumentType`, `SimpleDate`, `documentNumberValid`, `Today`; add `DeclineReason.UNDERAGE`, `DOCUMENT_IN_USE` (`shared/src/commonMain/kotlin/com/codewave/qullqi/domain/model/Kyc.kt`, `domain/repository/KycRepository.kt`, `domain/system/ProviderPage.kt`)
- [X] T044 [US1] Remote: `POST my/kyc` without body; map `underage`, `document_in_use` (`shared/src/commonMain/kotlin/com/codewave/qullqi/data/remote/RemoteKycRepository.kt`, `Dto.kt`, `QullqiApi.kt`); remove `today` wiring (`AppDependencies.kt`, `androidApp/src/main/kotlin/com/codewave/qullqi/QullqiApplication.kt`)
- [X] T045 [US1] `KycViewModel`/`KycScreens`/`KycFlow` (`shared/src/commonMain/kotlin/com/codewave/qullqi/feature/kyc/`): no Document screen; "Continuar" creates the verification and opens the provider; "Intentar de nuevo" returns to the intro; camera-denied block on the intro; texts for the new reasons
- [X] T046 [US1] Tests: delete `KycRulesTest`; rewrite `KycViewModelTest`; update `RemoteKycRepositoryTest` (no body, new reasons) in `shared/src/androidHostTest/kotlin/com/codewave/qullqi/`
- [X] T047 Backend (`BackendQullpi`, coordinate): `POST /my/kyc` without body; webhook reads Persona's extracted birth date and identification number, declines `underage` / `document_in_use`, stores verified values; document in `endpoints.md`

## Phase 10: Smooth hand-off to the provider page (rev. 2026-11-19)

**Problem** (owner, device): after "Continuar" the activity switch is abrupt and the WebView shows a
white screen for ~1 s before Persona renders. Window background is already Nocturne (`Theme.Qullqi`);
the white comes from the WebView's default background and Persona's first paint before its theme.

**Goal**: Intro → provider page fades on the Nocturne background, with a Nocturne loader until
Persona has painted; no white frame at any point. Android only; no new dependencies.

**Independent test**: on the device, tap "Continuar" on "Verifica tu identidad": the screen fades to
the Nocturne background with a loader, then Persona fades in; record a screen video and check no
white frame. Back / Persona's close fades back to the KYC waiting screen.

- [X] T048 [US1] Dark WebView before any page: in `buildWebView` call `setBackgroundColor(ContextCompat.getColor(context, R.color.qullqi_bg))` before `loadUrl` (`androidApp/src/main/kotlin/com/codewave/qullqi/KycWebViewActivity.kt`)
- [X] T049 [US1] Hide until painted: `ProviderWebScreen(loading: Boolean, page)` draws the WebView at alpha 0 while `loading`, with a centered `CircularProgressIndicator` (`Q.colors.ac`, `QSize.iconResult`) and caption "Abriendo la verificación…" (`Q.type.bodySm`, `Q.colors.tx2`) on `Q.colors.bg`; when `loading` turns false the WebView fades in with `animateFloatAsState` using a `QMotion` duration token (no literal values) (`shared/src/androidMain/kotlin/com/codewave/qullqi/feature/kyc/ProviderWebScreen.kt`)
- [X] T050 [US1] Loading signal: `KycWebViewActivity` holds `var loading by mutableStateOf(true)`; set it to false in `WebViewClient.onPageFinished` of the first allowed `https` page (only once; later navigations inside Persona do not show the loader again); fallback timeout of 8 s so the user never stays on the loader if `onPageFinished` never arrives (`androidApp/src/main/kotlin/com/codewave/qullqi/KycWebViewActivity.kt`)
- [X] T051 [US1] Cross-fade instead of the system slide: open and close `KycWebViewActivity` with `android.R.anim.fade_in` / `android.R.anim.fade_out` — API 34+: `overrideActivityTransition(OVERRIDE_TRANSITION_OPEN|CLOSE, …)` in `onCreate`; below: `overridePendingTransition` after `startActivity` (`androidApp/src/main/kotlin/com/codewave/qullqi/AndroidProviderPage.kt`) and in `finish()` (`KycWebViewActivity.kt`)
- [X] T052 Update research R1 (WebView background, loader, cross-fade) and quickstart (new scenario: no white frame on open/close) in `specs/005-kyc-persona/research.md`, `specs/005-kyc-persona/quickstart.md`; build `:androidApp:assembleDebug`, run `:shared:testAndroidHostTest`, verify on device

- [X] T053 [US1] No navigation behind the opening provider page: on `PageOpen.Opened` the flow stays on the current screen (only `inProvider = true`); on `Resumed` it resets to the waiting screen and polls, so the KYC screen change no longer animates under the WebView fade (`shared/src/commonMain/kotlin/com/codewave/qullqi/feature/kyc/KycViewModel.kt`, tests in `shared/src/androidHostTest/kotlin/com/codewave/qullqi/feature/kyc/KycViewModelTest.kt`)

- [X] T054 [US2] "No terminaste la verificación": primary "Volver a la verificación", text "Hacerlo más tarde" (no useless "Ya terminé"); offline: "Reintentar" + "Hacerlo más tarde"; back on the KYC root goes home (`BackHandler` → `Finish`); loader fallback 20 s (`shared/src/commonMain/kotlin/com/codewave/qullqi/feature/kyc/KycScreens.kt`, `KycFlow.kt`, `androidApp/src/main/kotlin/com/codewave/qullqi/KycWebViewActivity.kt`)

- [X] T055 [US2] Same as T053 for "Volver a la verificación": opening the page no longer switches the waiting screen to the "Completa la verificación" phase under the fading WebView; visible state is untouched until the user comes back (`shared/src/commonMain/kotlin/com/codewave/qullqi/feature/kyc/KycViewModel.kt`)

- [X] T056 [US1] Back home from KYC showed an empty dark screen: MainFlow covers its veil before opening AuthActivity for "Verificar" and nothing lifted it when AuthActivity finished back. MainFlow reveals the veil on every `ON_RESUME` (`shared/src/commonMain/kotlin/com/codewave/qullqi/feature/main/MainFlow.kt`)

- [X] T057 [US1] "Hecho" in Persona showed "No terminaste" because the backend is still `CREATED` until the webhook: the WebView returns `RESULT_OK` on the return link (`KycWebViewActivity`), `AndroidProviderPage` launches it for result and exposes `ProviderPage.consumeFinished()`; `KycViewModel.poll(finishedInProvider)` keeps polling on `CREATED` and ends in review (`shared/src/commonMain/kotlin/com/codewave/qullqi/domain/system/ProviderPage.kt`, `feature/kyc/KycViewModel.kt`, `androidApp/src/main/kotlin/com/codewave/qullqi/AndroidProviderPage.kt`, `KycWebViewActivity.kt`; 2 tests in `KycViewModelTest`)

## Dependencies & execution order

- **Phase 8 (WebView)** runs before T031/T032: T033 → T034 → T035; T036 → T037 → T038 → T039 (androidApp); T040 after T034; T041/T042 last.

- Precondition (004 integrated) → Phase 1 (T002 before T004) → Phase 2 → US1 → US2/US3 (same ViewModel file: sequential) → US4 → Polish.
- T015, T016 (androidApp) parallel with T018–T019 (shared).

## Parallel examples

- Phase 2: T008, T009, T012 in parallel after T006–T007/T010–T011 respectively.
- US1: T015 + T016 + T021 while T018–T020 are written.

## Implementation strategy

1. MVP = Phases 1–3 (verify end to end on the sandbox).
2. US2 resume → US3 retry → US4 status check.
3. Release gate: backend C1/H1/H2 items do not block (the app absorbs them); S1/S2 from spec 004 still gate registration/recovery releases.
