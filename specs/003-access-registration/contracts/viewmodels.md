# Contract: M2 ViewModels (internal)

Package `com.codewave.qullqi.feature.auth`. Each exposes `val state: StateFlow<UiState>`,
`fun onEvent(e: Event)` and `val effects: Flow<Effect>` (one-off: navigate, toast, shake,
biometric prompt). Constructor injection only.

| ViewModel | Depends on | Key events | Key effects |
|-----------|-----------|------------|-------------|
| `StartViewModel` | SessionRepository, AccountPreferenceRepository, splashMillis | `Started`, `HaveAccount`, `CreateAccount` | `Navigate(Login(id) / Welcome / RegPhone)`, `Toast("No hay cuentas…")` |
| `LoginViewModel` | SessionRepository, AccountPreferenceRepository, BiometricAuthenticator | `Digit(d)`, `Delete`, `Fingerprint`, `SwitchAccount`, `PickAccount(id)`, `AddAccount`, `Recover`, `EnableBiometrics(yes)` | `Shake`, `Navigate(MainArea)`, `ShowEnableBiometrics`, `Navigate(RecoverPin(id))` |
| `RegistrationViewModel` | RegistrationRepository, SessionRepository, Clock | `Phone(v)`, `SendCode`, `CodeDigit`, `PasteCode`, `Resend`, `Name/Email/Terms`, `Continue`, `PinDigit`, `Back` | `Navigate(next step)`, `Shake`, `Navigate(Login(existing))` |
| `RecoverPinViewModel` | RegistrationRepository, SessionRepository | `SendCode`, `CodeDigit`, `PinDigit` | `Toast("PIN actualizado")`, `Navigate(Login(id))` |
| `MainPlaceholderViewModel` | CurrentUserSource, SessionRepository | `Verify`, `Logout`, `ConfirmLogout` | `Navigate(KycEntry)`, `Navigate(Login(id))` |

Invariants (tested):
- Entered PIN digits are cleared after every check and never part of an emitted `UiState` beyond
  their count.
- Biometric `Failed/Cancelled` never decrements attempts.
- Registration draft survives `Back` and is cleared when leaving the flow.
