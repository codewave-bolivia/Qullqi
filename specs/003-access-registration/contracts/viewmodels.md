# Contract: M2 ViewModels (internal)

Package `com.codewave.qullqi.feature.auth`. Each exposes `val state: StateFlow<UiState>`,
`fun onEvent(e: Event)` and `val effects: Flow<Effect>` (one-off: navigate, toast, shake,
biometric prompt). Shakes are a counter in the state (`shakeCount`/`codeShake`/`pinShake`) so the
screen replays the animation on change. Constructor injection only.

| ViewModel | Depends on | Key events | Key effects |
|-----------|-----------|------------|-------------|
| `StartViewModel` | SessionRepository, AccountPreferenceRepository, splashMillis | `Started`, `HaveAccount`, `CreateAccount` | `Navigate(Login(id) / Welcome / RegPhone)`, `Toast("No hay cuentas…")` |
| `AppLock` (not a ViewModel) | Clock | `onBackground()`, `onForeground()`, `unlock()` | `locked: StateFlow<Boolean>` |
| `LoginViewModel` (modes: `Login`, `Unlock`) | SessionRepository, AccountPreferenceRepository, BiometricAuthenticator, AppLock | `Digit(d)`, `Delete`, `Fingerprint`, `SwitchAccount`, `PickAccount(id)`, `AddAccount`, `Recover`, `EnableBiometrics(yes)` | `Shake`, `EnterMain` (Login mode) / `Unlocked` (Unlock mode), `ShowEnableBiometrics`, `Navigate(RecoverPin(id))` |
| `RegistrationViewModel` | RegistrationRepository, SessionRepository, Clock | `Phone(v)`, `SendCode`, `CodeDigit`, `PasteCode`, `Resend`, `Name/Email/Terms`, `Continue`, `PinDigit`, `Back` | `Navigate(next step)`, `Shake`, `Navigate(Login(existing))` |
| `RecoverPinViewModel` | RegistrationRepository, SessionRepository | `SendCode`, `CodeDigit`, `PinDigit` | `Toast("PIN actualizado")`, `Navigate(Login(id))` |
| `MainPlaceholderViewModel` | CurrentUserSource, SessionRepository | `Verify`, `Logout`, `ConfirmLogout` | `OpenKyc` (→ `onVerify`), `LoggedOut(id)` (→ `onLogout`) |

Invariants (tested):
- Entered PIN digits are cleared after every check and never part of an emitted `UiState` beyond
  their count.
- Biometric `Failed/Cancelled` never decrements attempts.
- Registration draft survives `Back` and is cleared when leaving the flow.
- AppLock: locked at process start; > 300 000 ms in background ⇒ locked; ≤ 300 000 ms ⇒ unchanged;
  `unlock()` only after a successful PIN or biometric check.
- Unlock mode never changes the session's user and shares the 3-attempt counter with login.
