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
- **Action**: downloading these files is a new external source → **ask the user before the
  download task runs**; store licence texts in `docs/licenses/`.
- **Alternatives**: phosphor KMP library (third-party dependency for ~11 icons); system font
  (does not match the reference).

## R3. Navigation

- **Decision**: own `Navigator` holding a `List<Route>` in a `StateFlow`; `NavHost` renders the top
  route with `AnimatedContent`, direction-aware (push: enter from +26 dp, back: from −26 dp, fade,
  300 ms). Android back via `BackHandler` from Compose Multiplatform UI.
- **Rationale**: 12 routes, linear flows, custom motion. A library adds a dependency and makes the
  reference transitions harder; `ponytail:` comment names the upgrade path (navigation-compose or
  Navigation 3) when M4 needs tabs and deep links.

## R4. Biometrics

- **Decision**: `androidx.biometric:biometric:1.1.0` (latest **stable**; 1.4.0 is beta) in
  `androidApp`. `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)` for
  availability; `BiometricPrompt` with negative button "Usar PIN". Common interface:
  `suspend fun authenticate(title: String): BiometricResult { Success, Failed, Cancelled, Unavailable }`.
- **Activity**: `BiometricPrompt` requires `FragmentActivity`; `MainActivity` extends it
  (androidx.fragment comes transitively). `enableEdgeToEdge` and `setContent` keep working.
- **Security note**: biometric success only opens the session for a non-locked account
  (`loginWithBiometrics`); it does not reveal or use the PIN. A real backend would bind a keystore
  key to biometric auth — out of scope (no backend).
- **Testing**: physical device (clarified); unit tests use a fake authenticator.

## R5. App wiring and unsupported platforms

- **Decision**: `App(dependencies: AppDependencies? = null)`. `MainActivity` builds the
  dependencies (AndroidDatabaseFactory, Pbkdf2PinHasher, SampleOtpCodeGenerator, UuidIdGenerator,
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
