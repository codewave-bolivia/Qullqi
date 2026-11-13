# Data Model: Access and Registration

## New table `account_preference` (`AccountPreference.sq`)

| Column | Type | Rules |
|--------|------|-------|
| user_id | TEXT PK | `REFERENCES user(id) ON DELETE CASCADE` |
| biometrics_enabled | INTEGER NOT NULL DEFAULT 0 | `CHECK IN (0,1)` (FR-016) |
| biometrics_prompted | INTEGER NOT NULL DEFAULT 0 | `CHECK IN (0,1)` — "Activar huella" offered once (FR-018) |
| last_login_at | INTEGER | epoch ms of the last successful login (FR-002) |

Rows are created on demand (`INSERT OR IGNORE`). Seed: none (sample users start with biometrics
off and no last login).

Queries: `ensure(user_id)`, `setBiometrics(enabled, user_id)`, `markPrompted(user_id)`,
`recordLogin(at, user_id)`, `preferenceFor(user_id)`, `mostRecentUser()` (`ORDER BY last_login_at
DESC NULLS LAST` fallback first user by `full_name`).

## Repository additions

- `SessionRepository.loginWithBiometrics(userId: UserId): LoginResult` — `UnknownUser`, `Locked`
  (attempts 0), else opens session and returns `Success`; never changes `pin_attempts_left`.
- `AccountPreferenceRepository` (new): `mostRecentUser(): UserId?`, `preference(userId):
  AccountPreference`, `recordLogin(userId)`, `setBiometrics(userId, enabled)`,
  `markBiometricsPrompted(userId)`.

## UI state (not persisted)

### Registration draft (`RegistrationViewModel`)

| Field | Rules |
|-------|-------|
| step | `PHONE(1) → CODE(2) → INFO(3) → PIN(4) → DONE` |
| phone | 8 digits `^[67]\d{7}$`; shown with `+591` prefix |
| codeState | resend seconds left (30→0), tries left, error |
| fullName, email, termsAccepted | live validation; email `^[^@\s]+@[^@\s]+\.[^@\s]+$` after trim |
| pin, pinConfirmation | 4 digits each; cleared on mismatch |

Survives back navigation and configuration changes (held by the ViewModel); discarded when the flow
is left without finishing.

### Login (`LoginViewModel`)

| Field | Rules |
|-------|-------|
| target account | most recent or chosen in the accounts sheet |
| entered digits | 0–4; checked at 4; cleared after check (never kept) |
| status | `Idle · Checking · Entering · Wrong(attemptsLeft) · Locked` |
| biometricKeyVisible | preference on ∧ device available ∧ not locked |

### AppLock (in-memory, process-wide)

| Field | Rules |
|-------|-------|
| unlocked | false at process start; true after successful PIN/biometric in AuthActivity or UnlockScreen |
| backgroundedAt | epoch ms set on MainActivity `onStop` |
| timeout | 300 000 ms; on `onStart`, `now - backgroundedAt > timeout` ⇒ unlocked = false |

### State transitions

```text
AuthActivity
  Launch: system screen ─▶ Splash ──(≥1 user)──▶ Login(most recent)   Splash ──(0)──▶ Welcome
  Login:  Idle ─4 digits─▶ Checking ─ok─▶ Entering ─▶ [EnableBiometrics once] ─▶ veil ─▶ MainActivity
                                    └─wrong─▶ Wrong(n) ─▶ Idle      └─0 left─▶ Locked ─▶ RecoverPin
          Idle ─fingerprint─▶ ok ─▶ MainActivity | failed/cancel ─▶ Idle (no attempt used)
  Register: Phone ─▶ Code ─▶ Info ─▶ Pin ─▶ Done ─"Verificar"─▶ KycIntro ─done─▶ MainActivity
                                              └─"Más tarde"─▶ MainActivity
  Recover: SendCode ─▶ NewPin ─▶ toast ─▶ Login
MainActivity
  Unlocked ─onStop─▶ Backgrounded ─onStart ≤5 min─▶ Unlocked
                                  └─onStart >5 min─▶ Locked(UnlockScreen) ─PIN/fingerprint─▶ Unlocked
  process restored ─▶ Locked
  "Verificar" ─▶ AuthActivity(Kyc) ─done─▶ (finish) ─▶ MainActivity
  "Cerrar sesión" (sheet) ─▶ AuthActivity(Login(id)), task cleared
```
