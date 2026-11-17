# Data Model: Backend Integration for Access and Registration

## Local database (SQLDelight) — schema v2

### `user` (changed: now the cache of backend users seen on this device)

| Column | Type | Rules |
|---|---|---|
| id | TEXT PK | backend user UUID (`GET /me` `userId`) |
| full_name | TEXT NOT NULL | display name: `/me.displayName`, else legal names, else "Cliente" (FR-018) |
| phone | TEXT NOT NULL UNIQUE | `+591[67]XXXXXXX` (existing CHECK) |
| email | TEXT NOT NULL | lowercase |
| verification | TEXT NOT NULL | `NOT_VERIFIED · UNDER_REVIEW · VERIFIED` (existing CHECK) |
| is_new | INTEGER | unchanged (spec 002) |
| pin_attempts_left | INTEGER 0–3 | mirror of the backend: `attempts_left` from 401, 0 on 423, 3 on success/reset |
| created_at | INTEGER | first time cached |
| **first_name** | TEXT NULL | new; 1–60 chars when filled by the backend |
| **last_name_paternal** | TEXT NULL | new; 1–60 |
| **last_name_maternal** | TEXT NULL | new; 0–60, optional |

New queries: `upsertUser(id, full_name, first_name, last_name_paternal, last_name_maternal, phone,
email, verification, created_at)` (`INSERT … ON CONFLICT(id) DO UPDATE`), `setAttemptsLeft(n, id)`,
`userByPhone(phone)`.

### `device` (new, `Device.sq`)

| Column | Type | Rules |
|---|---|---|
| slot | INTEGER PK | `CHECK (slot = 1)` — one row per installation |
| device_id | TEXT NOT NULL | UUID created once (FR-006) |

Queries: `ensureDevice(device_id)` (`INSERT OR IGNORE … VALUES (1, ?)`), `deviceId()`.

### Unchanged

`current_session`, `account_preference`, `terms_acceptance`, `credential`, `phone_confirmation`,
`kyc_attempt`: kept. `credential` and `phone_confirmation` are used only by the in-memory SQL
repositories in tests; the remote implementation never writes them.

### Migration `1.sqm` (v1 → v2)

```sql
ALTER TABLE user ADD COLUMN first_name TEXT;
ALTER TABLE user ADD COLUMN last_name_paternal TEXT;
ALTER TABLE user ADD COLUMN last_name_maternal TEXT;
CREATE TABLE device (slot INTEGER NOT NULL PRIMARY KEY CHECK (slot = 1), device_id TEXT NOT NULL);
DELETE FROM phone_confirmation;
DELETE FROM user;   -- local/sample accounts are not backend accounts (spec edge case)
```

Seed (`Seed.sq insertSamples`) is no longer run on install; only `SeededSchema` in tests.

## Secure storage (not in the database)

| Item | Where | Lifetime |
|---|---|---|
| access token | memory (Ktor bearer provider) | 15 min; lost on process death |
| renewal credential per user | `KeystoreTokenStore`: AES-GCM ciphertext in prefs `qullqi_secure`, key `rt_<userId>` | 30 days, rotated on each refresh; deleted on logout, PIN reset, rejected refresh, debug reset |
| phone proof (`phoneVerifiedToken`) | memory in `RemoteRegistrationRepository` | 15 min; dropped after use |

## UI state changes

### Registration draft (`RegistrationViewModel`)

| Field | Rules |
|---|---|
| displayName | required, trimmed 1–100 (FR-009); legal names come from KYC |

`infoValid = displayName valid ∧ email valid ∧ terms`.
`register → PhoneNotConfirmed` ⇒ step = CODE, other fields kept (FR-012).

### Login target (`LoginViewModel`)

`target = Account(userId) | Phone(phone)` — the second for accounts not on this device (FR-014).
New statuses: `Unavailable` (message, nothing consumed), `SessionExpired` (fingerprint refresh
rejected → PIN required, fingerprint key hidden).

### State transitions (additions to spec 003)

```text
Welcome ─"Ya tengo cuenta" (no accounts)─▶ LoginPhone ─phone ok─▶ Login(phone) ─PIN ok─▶ MainActivity
RegCode ─"¿Ya tienes cuenta? Inicia sesión"─▶ LoginPhone(prefilled)
RegPin ─register 401─▶ RegCode (draft kept)
Login(fingerprint) ─refresh 401/423─▶ Login(PIN only, "Ingresa tu PIN para continuar")
Main (onStart) ─▶ GET /me (silent) ─▶ cache ─▶ CurrentUserSource emits
Any request ─401 after refresh fails─▶ session closed ─▶ AuthActivity(Login(id))
```
