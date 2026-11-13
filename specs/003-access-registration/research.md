# Research: Access and Registration

## R1. Visual tokens (Nocturne, from the Auth reference)

Source: `docs/references-views/_ds/nocturne-*/styles.css` and the `[data-theme]` blocks in
`Qullqi Pay Auth.dc.html`. OKLCH values converted to sRGB (script in this session).

| Role | Dark (default) | Light |
|------|----------------|-------|
| page (behind app) | `#0e0f18` | `#c4c8db` |
| bg | `#161826` | neutral-200 `#e4e7f5` |
| s1 (surface) | `#232532` | neutral-100 `#f3f5fe` |
| s2 | `#2c2e3c` | `#d9ddee` |
| s3 | neutral-800 `#3f424d` | neutral-300 `#cfd3e5` |
| ln (divider) | `#e9e9ed` @16 % | `#161826` @13 % |
| tx | `#e9e9ed` | `#161826` |
| tx2 | neutral-500 `#9397ab` | neutral-700 `#595d6c` |
| ac (accent) | `#9184d9` | accent-600 `#796cbf` |
| ac-t (accent tint) | accent-900 `#2b2741` | accent-200 `#e7e5fe` |
| ac-tx (accent text) | accent-300 `#d2cefd` | accent-700 `#5d5294` |
| neg | `#eb827b` | `#bd413f` |
| warn | `#eabc6e` | `#a06604` |

Type (STYLEGUIDE §1): Inter only, weights 400/500 (never ≥ 600); `title.flow` 28/1.15 −0.025em,
`title.lg` 24–26 −0.02em, `title.sheet` 20, `title.bar` 18 −0.01em, `body.lg` 15.5, `body.md` 14.5,
`body.sm` 14, `caption` 12.5. Radii: xs 6, sm 8, md 12, lg 14, sheet 20. Spacing base 2 dp
(2…36); onboarding horizontal margin 24–28. Motion (STYLEGUIDE §5): push 300 ms
`cubic-bezier(.2,.8,.2,1)` ±26 dp + fade; sheet 320 ms `(.2,.9,.2,1)`; shake 420 ms; pop 500 ms;
press scale .94 over 110 ms; reduce-motion → 150 ms fades.

## R2. Fonts and icons (bundled, approval needed)

- **Decision**: Inter v4.1 (rsms/inter, SIL OFL 1.1) — `Inter-Regular.ttf`, `Inter-Medium.ttf` only
  (400/500 are the only weights allowed). Phosphor Icons regular (MIT) as Compose vector XML:
  `caret-left`, `backspace`, `fingerprint`, `lock-simple`, `user-switch`, `check`, `x`, `warning`,
  `seal-check`, `sign-out`, `device-mobile`.
- **Rationale**: FR-026 forbids runtime downloads; the reference loads Inter from Google Fonts,
  which the app must not do.
- **Action**: downloading these files is a new external source — **approved 2026-11-13** (plan.md "Approvals");
  store licence texts in `docs/licenses/`.
- **Alternatives**: phosphor KMP library (third-party dependency for ~11 icons); system font
  (does not match the reference).

## R3. Activities, flow roots and in-activity navigation (rev. 2)

- **Decision**: two Android activities (clarified). Shared code exposes two flow roots:
  `AuthFlow(deps, start: AuthStart, onEnterMain, onKycDone)` and
  `MainFlow(deps, onLogout(UserId), onVerify())`. Inside each, a tiny `FlowNavigator` (back stack of
  that flow's routes in a `StateFlow`) renders the top route with `AnimatedContent` (push +26 dp,
  back −26 dp, fade, 300 ms). Android back via Compose `BackHandler`; at a flow root back is not
  consumed, so the activity finishes (FR-033).
- **Activity switching** (androidApp): login/registration success → `MainActivity` with
  `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`; logout → `AuthActivity(start = Login(id))`
  with the same flags; "Verificar" in MainActivity → `AuthActivity(start = Kyc)` *without* clearing,
  and AuthActivity finishes itself when the KYC flow ends (`onKycDone`) to reveal MainActivity.
- **Veil** (FR-032): the origin's `FlowHost` animates a `bg`-colored veil 0→1 (460 ms,
  `cubic-bezier(.4,0,.2,1)`), then starts the activity with `overridePendingTransition(0, 0)`
  (`overrideActivityTransition` on API 34+); the destination composes with the veil at 1 and fades it
  1→0 in 600 ms. Window background = `bg`, so no flash between activities.
- **iOS/Desktop** (FR-034): `QullqiRoot(deps)` keeps one window and swaps `AuthFlow`/`MainFlow` with
  the same veil; used by `App(deps)`; the Builder hosts it.
- **Rationale**: matches the reference architecture while keeping all UI shared; ponytail note in
  `FlowNavigator` names the upgrade path (navigation-compose / Navigation 3) when M4 needs tabs.

## R4. Biometrics

- **Decision**: `androidx.biometric:biometric:1.1.0` (latest **stable**; 1.4.0 is beta) in
  `androidApp`. `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)` for
  availability; `BiometricPrompt` with negative button "Usar PIN". Common interface:
  `suspend fun authenticate(title: String): BiometricResult { Success, Failed, Cancelled, Unavailable }`.
- **Activity**: `BiometricPrompt` requires `FragmentActivity`; `AuthActivity` and `MainActivity`
  (unlock overlay) extend it (androidx.fragment comes transitively).
- **Security note**: biometric success only opens the session for a non-locked account
  (`loginWithBiometrics`); it does not reveal or use the PIN. A real backend would bind a keystore
  key to biometric auth — out of scope (no backend).
- **Testing**: physical device (clarified); unit tests use a fake authenticator.

## R5. App wiring and unsupported platforms

- **Decision**: `App(dependencies: AppDependencies? = null)`. `QullqiApplication` (androidApp) builds
  the dependencies once (AndroidDatabaseFactory, Pbkdf2PinHasher, SampleOtpCodeGenerator, UuidIdGenerator,
  system clock, Dispatchers.IO via `kotlinx.coroutines.IO`, AndroidBiometricAuthenticator).
  `desktopApp` and iOS keep calling `App()` → `UnavailableScreen` ("Qullqi aún no está disponible
  en esta plataforma").
- **Rationale**: no protected file changes; no crash where no driver exists.
- **ViewModel creation**: `viewModel { LoginViewModel(deps…) }` from lifecycle-viewmodel-compose
  (already a dependency).

## R6. Data extension for M2

- **Decision**: table `account_preference(user_id PK FK→user ON DELETE CASCADE, biometrics_enabled,
  biometrics_prompted, last_login_at)`, created lazily (`INSERT OR IGNORE`) when first needed.
  `SessionRepository.loginWithBiometrics(userId): LoginResult` (Locked / UnknownUser / Success;
  never touches attempts). "Most recent account" = max `last_login_at`, else first by name.
- **Rationale**: keeps the `user` table and M4 contract untouched; separate concern.
- **Migration**: none. No released build ever opened the database (`AndroidDatabaseFactory` was
  not called by `App` before this feature), so schema v1 can still change. From this feature on,
  installed builds create the DB: **any later schema change needs a `.sqm` migration or an explicit
  pre-release reset note** (CLAUDE.md §14.6).

## R7. Registration code hint

The reference shows "Demo: cualquier código de 6 dígitos"; spec 002 uses a fixed sample code.
- **Decision**: show "Demo: usa el código 123456" (fixed code from `SampleOtpCodeGenerator.CODE`),
  as a caption under the boxes. Same for login: "PIN de demostración: 1234" only for sample users
  is **not** shown (would leak the PIN pattern for real-like accounts); the sample PIN is
  documented in CLAUDE.md §14 instead.

## R8. Responsive strategy

- Compact (< 600 dp width): full-width content with 24 dp margins (onboarding).
- Medium/expanded: content centered, max width 480 dp; background `page` color around a `bg`
  panel with `r.lg` corners, mirroring the reference phone frame on desktop.
- Short heights (< 640 dp): keypad keys shrink to 48 dp min, content scrolls; touch targets ≥ 44 dp.
- Keyboard: digits/Backspace/Enter handled on PIN and code screens (desktop, tablets with keyboards).

## R9. ViewModel testing

- `kotlinx-coroutines-test` `StandardTestDispatcher` + `Dispatchers.setMain`; ViewModels receive
  repositories built on the in-memory DB from spec 002 tests (`testDatabase()`), a fake biometric
  authenticator and `FakeClock`. Splash delay injected (0 in tests).

## R10. System launch screen (clarified)

- **Decision**: `androidx.core:core-splashscreen:1.2.0` (stable). `Theme.Qullqi.Starting`:
  `windowSplashScreenBackground = #161826`, `windowSplashScreenAnimatedIcon = @drawable/ic_qullqi`,
  `postSplashScreenTheme = @style/Theme.Qullqi` (window background `#161826`). AuthActivity calls
  `installSplashScreen()` before `super.onCreate`; the system screen is dismissed on the first frame,
  which is the in-app splash on the same background → no jump (FR-001a).
- **Icon**: no Qullqi app icon exists in the references (current one is the KMP template). Plan: a
  vector derived from the Inter Medium `q` glyph (extracted with fontTools after the approved Inter
  download) plus an accent `#9184d9` dot, on `#161826`, as adaptive icon + splash icon. The owner can
  replace it later with a designed icon.

## R11. Re-authentication lock (clarified)

- **Decision**: `AppLock(clock, timeoutMillis = 300_000)` in `commonMain`, a process-wide object held by
  `AppDependencies`: `unlocked` flag (false at process start), `onBackground()` records time,
  `onForeground()` locks if elapsed > timeout, `unlock()` after a successful PIN/biometric.
  AuthActivity calls `unlock()` before handing off. MainActivity calls `onBackground()` in `onStop`
  and `onForeground()` in `onStart`.
- **Process death**: a restored MainActivity starts with `unlocked = false` ⇒ shows unlock (cold start
  rule FR-002a holds even when Android restores the task).
- **Unlock UI**: `UnlockScreen` = login UI fixed to the current account (same `LoginViewModel` in
  unlock mode: same 3-attempt counter, biometric key, "Recuperar PIN" → AuthActivity recover then
  back). While locked, `MainFlow` content is not composed, so its state lives in its ViewModels and
  is restored after unlocking (FR-002b).
- **Recents snapshot**: `setRecentsScreenshotEnabled(false)` on MainActivity for API 33+. Below 33 no
  equivalent without `FLAG_SECURE` (which also blocks user screenshots) — documented limitation.

## R12. Splash duration vs readiness

The animated splash runs 1.9 s (reference) while `StartViewModel` reads the most recent account; it
routes when both are done. Tests inject `splashMillis = 0`.

## R13. Debug-only empty-device action

- **Decision**: in debug builds only, long-press the splash wordmark → sheet "Reiniciar datos"
  (con / sin ejemplos) using `LocalDataReset`. Lets the owner reach the welcome route on the device
  (quickstart). Excluded from release (`BuildConfig.DEBUG` passed via `AppDependencies.debugTools`).
