# Data Model: Local Data Layer — Identity and Verification Model

Database `QullqiDatabase`, package `com.codewave.qullqi.db`. All instants are epoch milliseconds
(`INTEGER`); calendar dates are `TEXT` `YYYY-MM-DD`. Foreign keys enabled on every connection.

## Entity relationships

```text
user 1 ─── 1 credential
user 1 ─── 0..1 current_session        (table holds at most 1 row overall)
user 1 ─── * terms_acceptance
user 1 ─── * kyc_attempt               (at most 1 in DRAFT/SUBMITTED per user)
user 0..1 ─ * phone_confirmation       (user_id set once registration completes)
All child FKs: ON DELETE CASCADE
```

## Tables

### `user`

| Column | Type | Rules |
|--------|------|-------|
| id | TEXT PK | stable; contract `UserId` (FR-026) |
| full_name | TEXT NOT NULL | trimmed, non-empty `CHECK (length(trim(full_name)) > 0)` |
| phone | TEXT NOT NULL UNIQUE | `CHECK (phone GLOB '+591[67][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')` (FR-002) |
| email | TEXT NOT NULL | stored trimmed + lowercase (FR-003); format validated in repository |
| verification | TEXT NOT NULL DEFAULT 'NOT_VERIFIED' | `CHECK IN ('NOT_VERIFIED','UNDER_REVIEW','VERIFIED')` (FR-012) |
| is_new | INTEGER NOT NULL DEFAULT 1 | `CHECK IN (0,1)` |
| pin_attempts_left | INTEGER NOT NULL DEFAULT 3 | `CHECK BETWEEN 0 AND 3`; locked ⇔ `= 0` (FR-005) |
| created_at | INTEGER NOT NULL | |

### `credential`

| Column | Type | Rules |
|--------|------|-------|
| user_id | TEXT PK | FK → user ON DELETE CASCADE |
| pin_hash | TEXT NOT NULL | base64 PBKDF2 output; never the PIN (FR-004) |
| pin_salt | TEXT NOT NULL | base64, 16 bytes |
| iterations | INTEGER NOT NULL | `CHECK (iterations >= 100000)` |
| updated_at | INTEGER NOT NULL | |

### `current_session`

| Column | Type | Rules |
|--------|------|-------|
| slot | INTEGER PK | `CHECK (slot = 1)` → at most one open session (FR-006) |
| user_id | TEXT NOT NULL | FK → user ON DELETE CASCADE |
| opened_at | INTEGER NOT NULL | |

### `terms_acceptance`

| Column | Type | Rules |
|--------|------|-------|
| user_id | TEXT NOT NULL | FK → user ON DELETE CASCADE |
| terms_version | TEXT NOT NULL | e.g. `2026-01` |
| accepted_at | INTEGER NOT NULL | |
| PK | (user_id, terms_version) | (FR-007) |

### `phone_confirmation`

| Column | Type | Rules |
|--------|------|-------|
| id | TEXT PK | |
| phone | TEXT NOT NULL | same GLOB as `user.phone` |
| code | TEXT NOT NULL | `CHECK (length(code) = 6 AND code GLOB '[0-9]*')` |
| issued_at | INTEGER NOT NULL | resend allowed when `now - issued_at >= 30 000` (FR-010) |
| expires_at | INTEGER NOT NULL | `issued_at + 300 000` (FR-009) |
| tries_left | INTEGER NOT NULL DEFAULT 3 | `CHECK BETWEEN 0 AND 3` |
| confirmed_at | INTEGER | NULL until confirmed |
| user_id | TEXT | FK → user ON DELETE CASCADE; set at registration |
| index | (phone, issued_at) | latest code per phone |

### `kyc_attempt`

| Column | Type | Rules |
|--------|------|-------|
| id | TEXT PK | |
| user_id | TEXT NOT NULL | FK → user ON DELETE CASCADE |
| document_type | TEXT NOT NULL | `CHECK IN ('CI','PASSPORT')` |
| document_number | TEXT | required to submit |
| front_ref / back_ref / selfie_ref | TEXT | each `NULL OR LIKE 'placeholder:%'` (FR-019); `CHECK (document_type = 'CI' OR back_ref IS NULL)` |
| extracted_full_name, extracted_document_number | TEXT | required to submit |
| extracted_birth_date, extracted_expiry_date | TEXT | `YYYY-MM-DD`, required to submit |
| declaration_accepted_at | INTEGER | required to submit |
| state | TEXT NOT NULL DEFAULT 'DRAFT' | `CHECK IN ('DRAFT','SUBMITTED','APPROVED','REVIEW','REJECTED')` |
| rejection_reason | TEXT | `CHECK (state <> 'REJECTED' OR rejection_reason IS NOT NULL)` |
| created_at | INTEGER NOT NULL | |
| finished_at | INTEGER | set on final state |
| unique partial index | `(user_id) WHERE state IN ('DRAFT','SUBMITTED')` | FR-015 |
| index | (user_id, created_at) | history (FR-017) |

Submission completeness (FR-014) is checked by the repository before `DRAFT → SUBMITTED`:
CI needs front, back, selfie, document number, 4 extracted fields, declaration; passport the same
without back.

### Trigger `kyc_attempt_final_sets_user_status`

`AFTER UPDATE OF state ON kyc_attempt WHEN new.state IN ('APPROVED','REVIEW','REJECTED')` →
`UPDATE user SET verification = CASE new.state WHEN 'APPROVED' THEN 'VERIFIED'
WHEN 'REVIEW' THEN 'UNDER_REVIEW' ELSE 'NOT_VERIFIED' END WHERE id = new.user_id` (FR-016).

## State transitions

```text
kyc_attempt:  DRAFT ──submit (complete)──▶ SUBMITTED ──process──▶ APPROVED | REVIEW | REJECTED
              DRAFT ──discard──▶ (deleted)
              REVIEW ──(future backend decision)──▶ APPROVED | REJECTED   (not in this feature)

user.verification:  NOT_VERIFIED ──APPROVED──▶ VERIFIED
                    NOT_VERIFIED ──REVIEW────▶ UNDER_REVIEW
                    any ─────────REJECTED────▶ NOT_VERIFIED
user.pin_attempts_left: 3 ─wrong─▶ 2 ─▶ 1 ─▶ 0 (locked);  correct PIN or PIN reset ─▶ 3
```

Start-attempt rule: refused when `verification = 'VERIFIED'` (US3-6) or a non-final attempt exists
(index).

## Sample data (`Seed.sq`, synthetic, fixed)

Sample PIN for every user: `1234`. Sample SMS code: `123456`. Terms version `2026-01`.
Phones are in the fictitious `+59170000xxx` range; emails `@example.com`.

| id | full_name | phone | verification | pin_attempts_left | is_new | attempts |
|----|-----------|-------|--------------|-------------------|--------|----------|
| usr-verified | Lucía Prueba Quispe | +59170000001 | VERIFIED | 3 | 0 | `att-verified-1` CI, APPROVED |
| usr-unverified | Mateo Demo Choque | +59170000002 | NOT_VERIFIED | 3 | 0 | none |
| usr-review | Valeria Ejemplo Mamani | +59170000003 | UNDER_REVIEW | 3 | 0 | `att-review-1` PASSPORT, REVIEW |
| usr-rejected | Diego Muestra Flores | +59170000004 | NOT_VERIFIED | 3 | 0 | `att-rejected-1` CI, REJECTED "Documento ilegible" |
| usr-locked | Sofía Ficticia Rojas | +59170000005 | VERIFIED | 0 | 0 | `att-locked-1` CI, APPROVED |
| usr-new | Andrés Nuevo Condori | +59170000006 | NOT_VERIFIED | 3 | 1 | none |

Each user has a `credential` (hash of `1234`, fixed salt per user) and a `terms_acceptance` for
`2026-01`. No `current_session` row (every start goes through login). "Empty device" =
`LocalDataReset.reset(withSamples = false)`.

Seed rows are inserted with final states directly, so the trigger is not involved; `SeedDataTest`
asserts each user's status equals the mapping of their latest final attempt (SC-003).
