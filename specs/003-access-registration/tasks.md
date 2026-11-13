---

description: "Task list for Access and Registration (M2)"
---

# Tasks: Access and Registration

**Input**: Design documents from `/specs/003-access-registration/`

**Prerequisites**: plan.md (rev. 2 + Approvals), spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included for ViewModels, AppLock and data (constitution VII; contracts/viewmodels.md
invariants). UI is validated manually on a physical device (quickstart.md §2).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US6 from spec.md

## Path Conventions

- `K` = `shared/src/commonMain/kotlin/com/codewave/qullqi`
- `R` = `shared/src/commonMain/composeResources`
- `SQ` = `shared/src/commonMain/sqldelight/com/codewave/qullqi/db`
- `T` = `shared/src/androidHostTest/kotlin/com/codewave/qullqi`
- `AA` = `androidApp/src/main`
- Never edit `desktopApp/`, `iosApp/`, `shared/src/iosMain`, `iosTest`, `jvmMain`, `jvmTest`. No `expect`.
- Screens use only `Q.colors`, `Q.type`, `QSpacing`, `QRadius` (no literal colors/sizes) and the
  exact Spanish texts quoted in the tasks (from `Qullqi Pay Auth.dc.html`).
- Visual reference for every screen: `docs/references-views/Qullqi Pay Auth.dc.html`.

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add to `gradle/libs.versions.toml`: versions `biometric = "1.1.0"`, `coreSplashscreen = "1.2.0"`; libraries `androidx-biometric` (`androidx.biometric:biometric`), `androidx-core-splashscreen` (`androidx.core:core-splashscreen`), `kotlinx-coroutines-test` (`org.jetbrains.kotlinx:kotlinx-coroutines-test`, `version.ref = "kotlinx-coroutines"`)
- [X] T002 [P] In `androidApp/build.gradle.kts` add `implementation(libs.androidx.biometric)` and `implementation(libs.androidx.core.splashscreen)`
- [X] T003 [P] In `shared/build.gradle.kts` add `implementation(libs.kotlinx.coroutines.test)` to the `androidHostTest` dependencies block
- [X] T004 Download Inter v4.1 from the official rsms/inter GitHub release (approved): copy `Inter-Regular.ttf` → `R/font/inter_regular.ttf` and `Inter-Medium.ttf` → `R/font/inter_medium.ttf`; save the licence as `docs/licenses/Inter-OFL.txt`; nothing else from the archive is committed
- [X] T005 Download Phosphor Icons regular SVGs (approved, MIT, phosphor-icons/core) for `caret-left`, `backspace`, `fingerprint`, `lock-simple`, `user-switch`, `check`, `x`, `warning`, `seal-check`, `sign-out`, `device-mobile`, `compass`, `arrow-down`, `arrow-up`, `paper-plane-tilt`, `identification-card`; convert each to a Compose vector XML `R/drawable/ph_<name>.xml` (viewport 256, stroke/fill as in source, color `#FFFFFFFF` to be tinted at use); save licence `docs/licenses/Phosphor-MIT.txt`
- [X] T006 Generate the Qullqi icon: with fontTools extract the `q` glyph outline from `inter_medium.ttf`, write `AA/res/drawable/ic_qullqi.xml` (vector: glyph in `#E9E9ED` + 12 % sized dot in `#9184D9`, centered in a 108 dp adaptive safe zone); `AA/res/drawable/ic_launcher_background.xml` solid `#161826`; point `AA/res/drawable-v24/ic_launcher_foreground.xml` and `mipmap-anydpi-v26/ic_launcher*.xml` at them; delete template PNG mipmaps only if adaptive icons cover minSdk (keep PNGs, regenerate is out of scope)
- [X] T007 Create `AA/res/values/themes.xml`: `Theme.Qullqi` (parent `android:Theme.Material.NoActionBar`, `android:windowBackground` `#161826`, transparent status/navigation bars, light-icons off) and `Theme.Qullqi.Starting` (parent `Theme.SplashScreen`, `windowSplashScreenBackground` `#161826`, `windowSplashScreenAnimatedIcon` `@drawable/ic_qullqi`, `postSplashScreenTheme` `@style/Theme.Qullqi`); add colors to `AA/res/values/colors.xml`
- [X] T008 Run `./gradlew :shared:compileAndroidMain :androidApp:compileDebugKotlin` — success

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Design system (shared — contracts/design-system.md)

- [X] T009 [P] Create `K/designsystem/Tokens.kt`: `QColors` with dark values `page #0E0F18, bg #161826, s1 #232532, s2 #2C2E3C, s3 #3F424D, ln #E9E9ED@16%, tx #E9E9ED, tx2 #9397AB, ac #9184D9, acT #2B2741, acTx #D2CEFD, neg #EB827B, warn #EABC6E` and light values `page #C4C8DB, bg #E4E7F5, s1 #F3F5FE, s2 #D9DDEE, s3 #CFD3E5, ln #161826@13%, tx #161826, tx2 #595D6C, ac #796CBF, acT #E7E5FE, acTx #5D5294, neg #BD413F, warn #A06604`; `QSpacing` (2…36 dp), `QRadius` (xs 6, sm 8, md 12, lg 14, sheet 20 dp)
- [X] T010 [P] Create `K/designsystem/Type.kt`: `Inter` family from `Res.font.inter_regular` (W400) and `Res.font.inter_medium` (W500) — never W600+; `QType` styles: titleFlow 28/1.15 −0.025em, titleLg 25 −0.02em, titleSheet 20, titleBar 18 −0.01em, bodyLg 15.5/1.5, bodyMd 14.5 W500, bodySm 14, caption 12.5, overline 11 W500 0.08em; numeric styles with `fontFeatureSettings = "tnum"`
- [X] T011 [P] Create `K/designsystem/Motion.kt`: push 300 ms `CubicBezierEasing(.2f,.8f,.2f,1f)` offset 26 dp; tab 250 ms; sheet 320 ms `(.2,.9,.2,1)`; shake 420 ms; pop 500 ms `(.2,.9,.3,1.2)`; press 110 ms scale .94; veilOut 460 ms and veilIn 600 ms `(.4,0,.2,1)`; `reduceMotion` flag replacing moves with 150 ms fades
- [X] T012 Create `K/designsystem/Theme.kt`: `QullqiTheme(dark: Boolean = true)` providing `LocalQColors`, `LocalQType` via `CompositionLocalProvider`, wrapping `MaterialTheme` only for text selection/ripple colors; `object Q { val colors; val type }`
- [X] T013 [P] Create `K/designsystem/components/Buttons.kt`: `QPrimaryButton` (1 dp `ac` border, `ac` 8 % fill, `ac` text bodyLg W500, glow `0 0 24dp -8dp ac`, pressed fill 26 %, press scale .94, disabled alpha .4 and clicks ignored, height 52, radius sm — never a solid fill), `QSecondaryButton` (`ln` border, `tx` text), `QTextButton` (`ac` or `tx2` text)
- [X] T014 [P] Create `K/designsystem/components/Inputs.kt`: `QTextField(value, onValueChange, label, help, valid: Boolean?, prefix: String? = null, keyboardType)` height 50, `s1` fill, `ln` border; valid → `ac` border + `ac` help, invalid → `neg` border + `neg` help; cursor `ac`; focus outline 2 dp `ac` with 2 dp gap
- [X] T015 [P] Create `K/designsystem/components/PinBoxes.kt` (`QPinBoxes(length = 4, filled: Int, error: Boolean)` 54×62 boxes radius xs, current box `ac` border + glow, filled shows 12 dp `ac` dot; `QCodeBoxes(value: String, length = 6, error: Boolean)` grid of 6 showing digits) and `K/designsystem/components/Shake.kt` (`Modifier.shake(trigger: Int)` keyframes −10, 9, −6, 4, 0 dp over 420 ms)
- [X] T016 [P] Create `K/designsystem/components/Keypad.kt`: `QKeypad(onDigit, onDelete, leftKey: (@Composable () -> Unit)? = null, enabled = true)` 3×4 grid (1-9, left key, 0, `ph_backspace`), key height 56 dp (min 48 when height < 640 dp), radius sm, press scale; plus `Modifier.digitKeyboardInput(onDigit, onDelete, onEnter)` handling hardware digits, Backspace and Enter
- [X] T017 [P] Create `K/designsystem/components/FlowBar.kt`: `QFlowTopBar(step, total, onBack)` — back button 42 dp (`ph_caret_left`, 44 dp touch area), segmented 3 dp progress bar with the current segment glowing, `"$step/$total"` caption (UI_GUIDELINES §2.3)
- [X] T018 [P] Create `K/designsystem/components/Sheet.kt`: `QBottomSheet(visible, onDismiss, content)` — scrim `#0E0F18` 60 % fading 200 ms, sheet rising 100 %→0 in 320 ms, handle 32×4, top radius 20, padding 18/30, max height 82 %, tap scrim to dismiss
- [X] T019 [P] Create `K/designsystem/components/Toast.kt`: `QToastHostState.show(text)` and `QToastHost` showing for 1.9 s near the bottom
- [X] T020 [P] Create `K/designsystem/components/Wordmark.kt` (`QWordmark(size)`: "qullqi" Inter W500 −0.04em + `ac` dot) and `K/designsystem/components/Icons.kt` (`QIcon(res, size, tint)` + `object Ph` referencing the `ph_*` drawables)
- [X] T021 [P] Create `K/designsystem/Veil.kt`: `VeilState` (`coverThen(action)` animates a `bg`-colored overlay 0→1 in veilOut then runs `action`; `reveal()` animates 1→0 in veilIn) and `Modifier`/`@Composable VeilOverlay(state)`
- [X] T022 [P] Create `K/designsystem/ResponsiveColumn.kt`: `QScreen(content)` — fills with `page`, centers a `bg` column of max width 480 dp (radius lg on widths ≥ 600 dp), 24 dp horizontal padding, vertical scroll when content exceeds height, safe-content insets

### Navigation (shared — contracts/navigation.md)

- [X] T023 Create `K/navigation/FlowNavigator.kt` (`class FlowNavigator<R>(start: R)` with `stack: StateFlow<List<R>>`, `push`, `replace`, `resetTo`, `back(): Boolean`, last direction) and `K/navigation/FlowHost.kt` (`FlowHost(navigator, content: @Composable (R) -> Unit)` using `AnimatedContent` push/back transitions from Motion; `BackHandler(enabled = stack.size > 1) { navigator.back() }`); add `// ponytail: own back stack for short per-activity flows; switch to navigation-compose/Navigation 3 when M4 needs tabs or deep links.`

### Data and domain additions

- [X] T024 [P] Create `SQ/AccountPreference.sq`: table `account_preference (user_id TEXT NOT NULL PRIMARY KEY REFERENCES user(id) ON DELETE CASCADE, biometrics_enabled INTEGER NOT NULL DEFAULT 0 CHECK (biometrics_enabled IN (0,1)), biometrics_prompted INTEGER NOT NULL DEFAULT 0 CHECK (biometrics_prompted IN (0,1)), last_login_at INTEGER)`; queries `ensure` (`INSERT OR IGNORE`), `preferenceFor`, `setBiometrics`, `markPrompted`, `recordLogin`, `mostRecentUser` (latest `last_login_at`, else first user by `full_name`)
- [X] T025 Create `K/domain/repository/AccountPreferenceRepository.kt` (`AccountPreference(biometricsEnabled, biometricsPrompted, lastLoginAt)`, `mostRecentUser(): UserId?`, `preference(userId)`, `recordLogin(userId)`, `setBiometrics(userId, enabled)`, `markBiometricsPrompted(userId)`) and `K/data/SqlAccountPreferenceRepository.kt` (constructor `db`, `clock`, `io`; `ensure` before writes)
- [X] T026 Add `suspend fun loginWithBiometrics(userId: UserId): LoginResult` to `K/domain/repository/SessionRepository.kt` and implement in `K/data/SqlSessionRepository.kt` (UnknownUser / Locked when attempts 0 / else open session, never change attempts); update `specs/002-local-data-layer/contracts/repositories.md` accordingly
- [X] T027 [P] Create `K/domain/security/BiometricAuthenticator.kt`: `interface BiometricAuthenticator { fun isAvailable(): Boolean; suspend fun authenticate(title: String, subtitle: String): BiometricResult }`, `enum BiometricResult { Success, Failed, Cancelled, Unavailable }`, `object NoBiometrics : BiometricAuthenticator`
- [X] T028 [P] Create `K/domain/session/AppLock.kt`: `class AppLock(clock: Clock, timeoutMillis: Long = 300_000)` with `locked: StateFlow<Boolean>` (true at creation), `onBackground()`, `onForeground()` (lock if elapsed > timeout), `unlock()`
- [X] T029 Create `K/AppDependencies.kt`: data class holding `SessionRepository`, `RegistrationRepository`, `AccountPreferenceRepository`, `CurrentUserSource`, `BiometricAuthenticator`, `AppLock`, `Clock`, `LocalDataReset?` (debug tools, null in release) and a `companion fun create(db, hasher, biometric, clock, io, debug)` wiring the Sql* repositories
- [X] T030 Create `T/feature/AppLockTest.kt` (locked at start; unlock; background 4:59 → unlocked, 5:01 → locked) and `T/data/AccountPreferenceRepositoryTest.kt` (mostRecentUser fallback by name then by last login; setBiometrics; markPrompted; cascade on user delete); add `loginWithBiometrics` cases to `T/data/SessionRepositoryTest.kt` (locked user → Locked; attempts unchanged)
- [X] T031 Create `T/feature/TestSupport.kt`: `MainDispatcherRule` (`Dispatchers.setMain(StandardTestDispatcher())`), `FakeBiometricAuthenticator(available, nextResult)`, `testDependencies(withSamples)` building `AppDependencies` on `testDatabase()`

### Android hosts and roots

- [X] T032 [P] Create `AA/kotlin/com/codewave/qullqi/QullqiApplication.kt`: builds `AppDependencies` once (`AndroidDatabaseFactory`, `Pbkdf2PinHasher`, `SampleOtpCodeGenerator`, `UuidIdGenerator`, system clock, `Dispatchers.IO`, debug tools when `BuildConfig.DEBUG`); biometric authenticator is set per activity
- [X] T033 [P] Create `AA/kotlin/com/codewave/qullqi/AndroidBiometricAuthenticator.kt` (`FragmentActivity`; `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == SUCCESS`; `BiometricPrompt` with negative button "Usar PIN"; maps errors to Failed/Cancelled via `suspendCancellableCoroutine`)
- [X] T034 Create `K/feature/auth/AuthFlow.kt` (`AuthStart`, `AuthRoute`, `AuthFlow(deps, start, onEnterMain, onKycDone, kycEntry)` with a `FlowNavigator<AuthRoute>`, `FlowHost`, `VeilOverlay`, toast host; route bodies stubbed until each story), `K/feature/main/MainFlow.kt` (`MainFlow(deps, onLogout, onVerify, content)`: when `AppLock.locked` show `UnlockScreen` stub instead of composing `content`), `K/QullqiRoot.kt` (swap AuthFlow/MainFlow in one window) and modify `K/App.kt` to `App(dependencies: AppDependencies? = null)` → `QullqiTheme { dependencies?.let { QullqiRoot(it) } ?: UnavailableScreen() }` with `K/feature/main/UnavailableScreen.kt` ("Qullqi aún no está disponible en esta plataforma")
- [X] T035 Create `AA/kotlin/com/codewave/qullqi/AuthActivity.kt` (extends `FragmentActivity`; `installSplashScreen()` before `super.onCreate`; reads extra `start` = `splash|login:<id>|kyc`; `setContent { QullqiTheme { AuthFlow(...) } }`; `onEnterMain` → veil then `MainActivity` with `NEW_TASK|CLEAR_TASK` + `overridePendingTransition(0,0)` / `overrideActivityTransition` on API 34+; `onKycDone` → `finish()` when started with `kyc`, else open MainActivity) and update `AA/AndroidManifest.xml` (`android:name=".QullqiApplication"`, theme `Theme.Qullqi`; AuthActivity exported launcher with `Theme.Qullqi.Starting`; MainActivity `exported=false`)
- [X] T036 Rewrite `AA/kotlin/com/codewave/qullqi/MainActivity.kt`: extends `FragmentActivity`; `setContent { QullqiTheme { MainFlow(...) } }`; `onStop` → `appLock.onBackground()`, `onStart` → `appLock.onForeground()`; API 33+ `setRecentsScreenshotEnabled(false)`; `onLogout(id)` → veil then `AuthActivity(start = login:<id>)` with `NEW_TASK|CLEAR_TASK`; `onVerify` → veil then `AuthActivity(start = kyc)` without clearing; keep `AppAndroidPreview` removed or pointing to a design-system preview
- [X] T037 Run `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :desktopApp:compileKotlin` — all green; install on device and confirm launch screen → AuthActivity stub (no white frame)

**Checkpoint**: Two activities, theme, navigation, lock and data additions in place.

---

## Phase 3: User Story 1 - App start routes the person correctly (Priority: P1) 🎯 MVP

**Goal**: Launch screen → animated splash → login (most recent account) or welcome.

**Independent Test**: `StartViewModelTest`; on device quickstart rows 1 and 13.

- [X] T038 [US1] Create `T/feature/auth/StartViewModelTest.kt`: with samples and no logins → `Navigate(Login(usr-locked…))` per `mostRecentUser` (first by name) after `splashMillis`; after `recordLogin(usr-review)` → Login(usr-review); empty DB → `Navigate(Welcome)`; `HaveAccount` on empty DB → toast "No hay cuentas en este dispositivo" and stays
- [X] T039 [US1] Create `K/feature/auth/start/StartViewModel.kt` (`splashMillis = 1900`; waits for both splash time and `mostRecentUser()`; effects per contracts/viewmodels.md)
- [X] T040 [P] [US1] Create `K/feature/auth/start/SplashScreen.kt`: `QWordmark` centered on `bg`, loading bar under it animating width 0→100 % over 1.9 s (`qLoad`), rise-in text; debug only (`deps.debugTools != null`): long-press on the wordmark opens a `QBottomSheet` "Reiniciar datos" with "Con ejemplos" / "Sin ejemplos" calling `LocalDataReset.reset`
- [X] T041 [P] [US1] Create `K/feature/auth/start/WelcomeScreen.kt`: wordmark, title "Tu billetera en Bolivianos y dólares", subtitle "Envía, cobra y paga con QR en segundos.", `QPrimaryButton("Crear cuenta")`, `QSecondaryButton("Ya tengo cuenta")`, `QScreen` layout
- [X] T042 [US1] Wire `AuthRoute.Splash` and `AuthRoute.Welcome` in `K/feature/auth/AuthFlow.kt` (`viewModel { StartViewModel(...) }`, effects → navigator)

**Checkpoint**: Cold start routes correctly.

---

## Phase 4: User Story 2 - Returning person logs in with PIN (Priority: P1)

**Goal**: PIN login with shake, attempts, lock, account switching; success hands off to MainActivity.

**Independent Test**: `LoginViewModelTest` (login mode); quickstart rows 2, 3, 10.

- [X] T043 [US2] Create `T/feature/auth/LoginViewModelTest.kt` (login mode): 4 digits `1234` → `EnterMain`, `recordLogin` called, `AppLock.unlock()` called; wrong → `Shake`, digits cleared, message "Te quedan 2 intentos." then "Te quedan 1 intento."; third wrong → state Locked with "Bloqueamos el acceso por seguridad. Recupera tu PIN."; `usr-locked` opens Locked directly; `SwitchAccount` lists 6 accounts with masked phones `+591 7••• ••01`; `PickAccount` changes target; entered digits never exceed count in state
- [X] T044 [US2] Create `K/feature/auth/login/LoginViewModel.kt` (`mode: Login|Unlock`; state: account name/first name, filled count, status Idle/Checking/Entering/Wrong(n)/Locked, message, accounts list, biometricKeyVisible=false for now); after success in Login mode show "Ingresando…" ~380 ms then `EnterMain`
- [X] T045 [P] [US2] Create `K/feature/auth/login/LoginScreen.kt`: avatar initials, title "Hola de nuevo, {first}", subtitle "Ingresa tu PIN de 4 dígitos", `QPinBoxes` with `shake`, message line (`tx2` / `neg` / `ac` for "Ingresando…"), `QKeypad` + hardware keys, `QTextButton("¿Olvidaste tu PIN?")`, `QTextButton("Cambiar de cuenta")` with `ph_user_switch`; locked state replaces keypad with `ph_lock_simple`, message and `QPrimaryButton("Recuperar PIN")`
- [X] T046 [P] [US2] Create `K/feature/auth/login/AccountsSheet.kt`: `QBottomSheet` listing accounts (initials chip, name, masked phone, lock badge if locked) + row "Agregar otra cuenta" → `RegPhone`
- [X] T047 [US2] Wire `AuthRoute.Login(userId?)` in `AuthFlow.kt`; `EnterMain` → `onEnterMain()`; `Recover` → push `RecoverPin(id)`

**Checkpoint**: Returning users can log in and land in MainActivity.

---

## Phase 5: User Story 3 - New person creates an account (Priority: P1)

**Goal**: 4-step registration ending in "Cuenta creada".

**Independent Test**: `RegistrationViewModelTest` on an empty DB; quickstart rows 11, 12.

- [X] T048 [US3] Create `T/feature/auth/RegistrationViewModelTest.kt`: phone `5123…` invalid (CTA disabled); `70000001` → `Navigate(Login(usr-verified))` with message account exists; valid `71234567` → code step with resend 30 → 0 using `advanceTimeBy`; wrong code → shake + tries; correct `123456` → info step; invalid email → `Continuar` disabled + "Revisa el formato del correo."; terms required; PIN mismatch → shake + cleared confirmation; success → Done state with "Sin verificar", session open; `Back` keeps entered values
- [X] T049 [US3] Create `K/feature/auth/register/RegistrationViewModel.kt` (single ViewModel shared by the 4 steps, scoped to the registration entry; draft per data-model.md; resend countdown via clock ticks; effects per contract)
- [X] T050 [P] [US3] Create `K/feature/auth/register/PhoneStep.kt`: `QFlowTopBar(1, 4)`, title "¿Cuál es tu número de celular?", help "Te enviaremos un código por SMS para confirmarlo.", `QTextField` with prefix "BO +591", numeric, `QPrimaryButton("Enviar código")`
- [X] T051 [P] [US3] Create `K/feature/auth/register/CodeStep.kt`: `QFlowTopBar(2, 4)`, title "Escribe el código", help "Te enviamos un código por SMS al +591 {phone}", `QCodeBoxes` + keypad + paste, caption "Demo: usa el código 123456", `QTextButton` "Reenviar en 0:SS" / "Reenviar código", tries message
- [X] T052 [P] [US3] Create `K/feature/auth/register/InfoStep.kt`: `QFlowTopBar(3, 4)`, title "Cuéntanos sobre ti", help "Usaremos tu correo para comprobantes y avisos de seguridad.", fields "Nombre y apellido", "Correo electrónico" (error "Revisa el formato del correo."), terms checkbox "Acepto los Términos y condiciones y la Política de privacidad" (links open a static placeholder sheet), `QPrimaryButton("Continuar")`
- [X] T053 [P] [US3] Create `K/feature/auth/register/PinStep.kt`: `QFlowTopBar(4, 4)`, title "Crea un PIN de 4 dígitos" then "Confirma tu PIN", help "Lo usarás para entrar a la app y confirmar operaciones.", `QPinBoxes` + `QKeypad`
- [X] T054 [P] [US3] Create `K/feature/auth/register/DoneScreen.kt`: pop `ph_check` result, title "¡Listo!", kicker "ESTADO DE LA CUENTA" with badge "Sin verificar", limits list ("Explorar la app" Disponible `ph_compass`; "Depositar dinero", "Retirar dinero", "Enviar, cobrar y pagar" Requiere KYC with `ph_arrow_down`, `ph_arrow_up`, `ph_paper_plane_tilt`), `QPrimaryButton("Verificar mi identidad")`, `QTextButton("Hacerlo más tarde")`
- [X] T055 [US3] Wire `RegPhone…RegDone` in `AuthFlow.kt` (shared ViewModel instance across steps; "Crear cuenta" and "Agregar otra cuenta" start it; "Hacerlo más tarde" → `onEnterMain()` after `AppLock.unlock()`; "Verificar mi identidad" → push `KycIntro`)

**Checkpoint**: New accounts can be created end to end.

---

## Phase 6: User Story 4 - Locked or forgetful person recovers their PIN (Priority: P2)

**Independent Test**: `RecoverPinViewModelTest`; quickstart row 10.

- [X] T056 [US4] Create `T/feature/auth/RecoverPinViewModelTest.kt`: for `usr-locked` code is requested to its phone (masked "+591 7••• ••05"), `123456` confirms, new PIN twice → toast "PIN actualizado" + `Navigate(Login(usr-locked))`, attempts back to 3; mismatch shakes
- [X] T057 [US4] Create `K/feature/auth/recover/RecoverPinViewModel.kt` and `K/feature/auth/recover/RecoverPinScreen.kt` (steps: code → new PIN → confirm, reusing `QCodeBoxes`, `QPinBoxes`, `QKeypad`, texts "Te enviamos un código por SMS", "Crea un PIN de 4 dígitos", "Confirma tu PIN")
- [X] T058 [US4] Wire `AuthRoute.RecoverPin(id)` in `AuthFlow.kt`; also from login "¿Olvidaste tu PIN?"

---

## Phase 7: User Story 5 - Person logs in with biometrics (Priority: P2)

**Independent Test**: LoginViewModelTest biometric cases; quickstart rows 2, 5 on the physical device.

- [ ] T059 [US5] Extend `T/feature/auth/LoginViewModelTest.kt`: key hidden when preference off, device unavailable or account locked; `Fingerprint` + Success → `EnterMain` and attempts unchanged; Failed/Cancelled → Idle and attempts unchanged; first successful login of an account with `biometrics_prompted = 0` and device available → `ShowEnableBiometrics` once; `EnableBiometrics(true)` stores enabled + prompted, `(false)` stores prompted only
- [ ] T060 [US5] Extend `K/feature/auth/login/LoginViewModel.kt` with biometric key visibility, `Fingerprint` event (prompt title "Ingresa con tu huella", subtitle "Toca el sensor") calling `loginWithBiometrics`, and the once-only enable offer before `EnterMain`
- [ ] T061 [P] [US5] Create `K/feature/auth/login/EnableBiometricsSheet.kt` (`ph_fingerprint`, title "Ingresa con tu huella", text "Activa la huella para entrar más rápido.", `QPrimaryButton("Activar huella")`, `QTextButton("Ahora no")`) and pass the fingerprint key (`ph_fingerprint`) as `QKeypad.leftKey` in `LoginScreen.kt`
- [ ] T062 [US5] In `AuthActivity.kt` and `MainActivity.kt`, create `AndroidBiometricAuthenticator(this)` and inject it into the dependencies used by the flow (per-activity copy of `AppDependencies` with the authenticator)

---

## Phase 8: User Story 6 - Hand-off to the rest of the app (Priority: P2)

**Goal**: MainActivity placeholder, logout, Verificar → AuthActivity(kyc), 5-minute unlock.

**Independent Test**: `MainPlaceholderViewModelTest`, unlock tests; quickstart rows 3–9.

- [ ] T063 [US6] Create `T/feature/main/MainPlaceholderViewModelTest.kt` (shows current user name and status from `CurrentUserSource`; `Verify` → `OpenKyc` only when not verified; `ConfirmLogout` → session closed + `LoggedOut(id)`) and add unlock-mode cases to `T/feature/auth/LoginViewModelTest.kt` (correct PIN → `AppLock.unlock()` + `Unlocked`, user unchanged; wrong PINs share the 3-attempt counter; locked → "Recuperar PIN" effect)
- [ ] T064 [US6] Create `K/feature/main/MainPlaceholderViewModel.kt` and `K/feature/main/MainPlaceholderScreen.kt` (greeting with name, verification badge "Sin verificar" / "Verificación en revisión" / "Verificado", warning banner with `warn` border and "Verifica tu identidad" + "Verificar" when NOT_VERIFIED, caption "Área principal en construcción (M4)", destructive `QTextButton("Cerrar sesión")` → confirm `QBottomSheet` with `ph_sign_out`)
- [ ] T065 [US6] Create `K/feature/main/UnlockScreen.kt` (LoginScreen UI with `LoginViewModel(mode = Unlock)` for the current user; "Recuperar PIN" → `onVerify`-like callback that opens `AuthActivity(start = login:<id>)` with recovery) and replace the stub in `MainFlow.kt`
- [ ] T066 [P] [US6] Create `K/feature/auth/kyc/KycPlaceholderScreen.kt` (`ph_identification_card`, title "Verifica tu identidad", text "La verificación llega en M3.", `QPrimaryButton("Volver")` → `onDone`) and wire `AuthRoute.KycIntro` + `AuthStart.Kyc` in `AuthFlow.kt`

**Checkpoint**: M2 complete end to end on the device.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T067 Run `./gradlew :shared:testAndroidHostTest` and the PR gate `./gradlew clean :shared:compileAndroidMain :shared:compileKotlinJvm :androidApp:compileDebugKotlin :desktopApp:compileKotlin` — all green; record counts
- [ ] T068 Grep `K/feature` for literal `Color(`, `.dp` sizes outside designsystem tokens and `0xFF` — fix any found (FR-030)
- [ ] T069 Install on the physical device (`./gradlew :androidApp:installDebug`) and hand quickstart.md §2 rows 1–14 to the owner; record which were verified by the owner (the agent cannot operate the device)
- [ ] T070 Update `CLAUDE.md`: §2 tree (designsystem/, navigation/, feature/auth, feature/main, AuthActivity, QullqiApplication, fonts/icons, docs/licenses), §14 biometric preference table and debug reset action, and a note that `designsystem/` and `navigation/` are shared areas reused by M4
- [ ] T071 Set `**Status**: Ready` in `specs/003-access-registration/spec.md`, run `scripts/branches.sh check feature/003-access-registration`, and produce the CLAUDE.md §11 report (shared-area approvals; device verification status)

---

## Dependencies & Execution Order

- Setup (T001–T008) → Foundational (T009–T037) → US1 → US2 → US3 → US4 → US5 → US6 → Polish.
- US3 and US4 depend only on Foundational + `AuthFlow` wiring; US5 extends US2's `LoginViewModel`;
  US6's unlock reuses US2's `LoginViewModel`.
- Same-file sequences: `AuthFlow.kt` (T034, T042, T047, T055, T058, T066), `LoginViewModel.kt`
  (T044, T060), `LoginViewModelTest.kt` (T043, T059, T063), `MainFlow.kt` (T034, T065).

### Parallel Opportunities

- Design system T009–T011 and components T013–T022 are independent files.
- T024 ∥ T027 ∥ T028; T032 ∥ T033.
- Screen files within a story ([P]) after their ViewModel exists, e.g. T050–T054.

## Parallel Example: User Story 3

```text
After T049: T050 PhoneStep ∥ T051 CodeStep ∥ T052 InfoStep ∥ T053 PinStep ∥ T054 DoneScreen
Then: T055 wiring
```

## Implementation Strategy

### MVP

Setup + Foundational + US1 + US2: launch screen, splash, login with lock, hand-off to MainActivity.

### M2 complete

Add US3 (registration), US4 (recovery), US5 (biometrics), US6 (MainActivity placeholder, logout,
KYC entry, 5-minute unlock). Then the polish phase and device validation by the owner.
