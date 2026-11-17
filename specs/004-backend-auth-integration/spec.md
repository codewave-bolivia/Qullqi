# Feature Specification: Backend Integration for Access and Registration (M2)

**Feature Branch**: `feature/004-backend-auth-integration`

**Created**: 2026-11-17

**Status**: Draft

**Owner**: krypton612

**Depends on**: 003-access-registration waiting (integrated)

**Contract**: none

**Input**: User description: "integrar M2, el almacenamiento seguro de tokens, el registro con 3 campos de nombre, la migración del deviceId y GET /me"

## Context

Spec 003 delivered access and registration (M2) against local sample data. The Qullqi backend now
exists (`BackendQullpi`, reference `docs/integration-android/endpoints.md`) and constitution
v2.0.0 makes it the authority for sessions, PIN attempts, account lock, verification status and
identity data. This feature connects the existing M2 screens to that backend without changing
their look or flow, except where the backend requires it:

- Registration step 3 asks for **first name, paternal surname and maternal surname** (optional)
  instead of a single "full name" field.
- The session is kept with credentials stored only in the device's secure storage.
- Each installation gets a stable **device identifier** that the backend uses to bind sessions.
- The main area and M4 read the logged-in user (name, phone, email, verification status) from the
  backend.

**In scope**: SMS code request/confirm, account creation, PIN login, session renewal, fingerprint
login, logout, PIN recovery, current user, secure credential storage, device identifier, the
registration name fields, connection and service errors.

**Out of scope**: identity verification screens and flow (M3, next spec), balances, activity and
any other M4 data, changing the visual design, iOS/Desktop secure storage and network engines
(Builder).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Log in with PIN against the backend (Priority: P1)

A user with an existing backend account opens the app, sees their account on the login screen and
enters their 4-digit PIN; the backend validates it and the main area opens with their real name and
verification status.

**Why this priority**: every other flow needs a valid session; it proves the connection end to end.

**Independent Test**: with the backend dev profile running, log in with `+59170000001` / `1234`
and see the main area with that user's name and "Verificado".

**Acceptance Scenarios**:

1. **Given** an account used before on this device, **When** the correct PIN is entered, **Then**
   the session opens and the main area shows the name and status returned by the backend.
2. **Given** a wrong PIN, **When** it is entered, **Then** the boxes shake and clear and the remaining
   attempts shown are the ones reported by the backend.
3. **Given** the third wrong PIN, **When** it is entered, **Then** the locked state with only
   "Recuperar PIN" is shown.
4. **Given** an account locked in the backend, **When** any PIN is entered, **Then** the locked
   state is shown without using attempts.
5. **Given** an empty device, **When** "Ya tengo cuenta" is chosen, **Then** the user can enter their
   phone number and PIN to log in (an account not used on this device can still log in).

---

### User Story 2 - Register with separate name fields (Priority: P1)

A new user registers in 4 steps; step 3 asks for first name, paternal surname, maternal surname
(optional), email and terms. On success the session is open and "Cuenta creada" is shown.

**Why this priority**: entry point for every new customer; the backend rejects the old single field.

**Independent Test**: register a new number with the dev code `123456`; the account exists in the
backend and the main area shows the first name.

**Acceptance Scenarios**:

1. **Given** step 3, **When** first name and paternal surname are filled, email is valid and terms
   accepted, **Then** "Continuar" is enabled (maternal surname may stay empty).
2. **Given** first name or paternal surname is blank, **Then** "Continuar" stays disabled.
3. **Given** a number that already has an account, **When** the code step is reached, **Then** the
   screen offers "¿Ya tienes cuenta? Inicia sesión" because no code will arrive, and creating the
   account reports that the number is already registered and leads to login.
4. **Given** the confirmed code is older than 15 minutes when the account is created, **Then** the
   user is sent back to the code step to request a new code, keeping the other data.
5. **Given** a wrong code, **Then** the remaining tries come from the backend; after the last try
   or 5 minutes a new code is required.
6. **Given** a code requested less than 30 s ago or too many requests in an hour, **Then** the
   countdown shows the waiting time reported by the backend.

---

### User Story 3 - Stay logged in safely and use the fingerprint (Priority: P2)

While using the app the session renews silently; on later cold starts the user unlocks with PIN or
fingerprint. Credentials never leave the device's secure storage.

**Why this priority**: without renewal the session ends every 15 minutes; fingerprint is an M2
feature that depends on it.

**Independent Test**: log in, enable fingerprint, wait more than 15 minutes, use the app (no PIN
asked); close the app, reopen and enter with the fingerprint.

**Acceptance Scenarios**:

1. **Given** an open session whose short-lived access expired, **When** the app needs the backend,
   **Then** the session renews without asking for the PIN.
2. **Given** fingerprint enabled, **When** the fingerprint succeeds on cold start, **Then** the
   session renews and the main area opens; no PIN is stored on the device.
3. **Given** the renewal is rejected (expired after 30 days, revoked, PIN reset elsewhere, account
   locked), **Then** the user is taken to login for that account with a short message, and the
   fingerprint key is hidden until the next PIN login.
4. **Given** the device storage, **When** it is inspected, **Then** no PIN, SMS code or session
   credential appears in the app database, logs or plain settings.

---

### User Story 4 - Log out (Priority: P2)

"Cerrar sesión" ends the session in the backend and on the device.

**Independent Test**: log out, then try to renew with the old credential: the backend rejects it.

**Acceptance Scenarios**:

1. **Given** an open session, **When** logout is confirmed, **Then** the backend is told to end the
   session for this device, local credentials are deleted and login opens for the same account.
2. **Given** no connection, **When** logout is confirmed, **Then** local credentials are still
   deleted and the user reaches login (the backend session expires on its own).

---

### User Story 5 - Recover the PIN (Priority: P2)

A locked or forgetful user confirms their phone with an SMS code, sets a new PIN twice and can log in.

**Independent Test**: lock `+59170000005`'s PIN state, recover with code `123456`, set a new PIN,
log in with it.

**Acceptance Scenarios**:

1. **Given** the recovery screen, **When** the code is confirmed and the new PIN entered twice,
   **Then** "PIN actualizado" is shown, the account is unlocked and login works with the new PIN.
2. **Given** a recovery, **Then** sessions on other devices are ended by the backend.

---

### User Story 6 - Current user for the main area (Priority: P3)

The main area (and M4 later) shows the logged-in user from the backend: name, masked phone, email
and verification status, refreshed after login and when returning to the app.

**Independent Test**: change a dev user's status in the backend, return to the app, see the new
status.

**Acceptance Scenarios**:

1. **Given** login succeeded, **Then** the current user is loaded from the backend before the main
   area is shown.
2. **Given** the app returns to the foreground, **Then** the current user is refreshed; a status
   change is reflected without logging in again.
3. **Given** no connection, **Then** the last known user is shown and refreshed when back online.

### Edge Cases

- No connection or backend down on any step: a clear message ("Sin conexión. Intenta de nuevo."),
  the entered data is kept and nothing is counted as a wrong PIN or code.
- Backend error not in the known list: generic message; no technical details shown.
- Slow response: buttons show progress and cannot be pressed twice; account creation, PIN reset
  and other one-time actions are never repeated automatically.
- Two renewals at the same time (several screens): only one is sent; the others wait for it.
- Phone not Bolivian mobile (`+591` 6x/7x): rejected before contacting the backend.
- App upgraded from a build that only had local sample accounts: those sample accounts are no
  longer listed (they do not exist in the backend); the device starts at welcome.
- Device identifier lost (app data cleared): a new one is created; the user logs in again with PIN.

## Requirements *(mandatory)*

### Functional Requirements

**Connection**

- **FR-001**: Access, registration, PIN recovery, logout and the current user MUST be served by
  the backend; the device MUST NOT decide whether a PIN or code is correct, the attempts left, the
  lock state or the verification status.
- **FR-002**: Release builds MUST connect only over encrypted connections; the backend address MUST
  depend on the build (local backend in debug, production address in release).
- **FR-003**: Every backend error MUST be shown as a Spanish (Bolivia, "tú") message per
  UI_GUIDELINES §7; connection failures MUST NOT consume attempts or lose entered data.
- **FR-004**: One-time actions (create account, reset PIN) MUST NOT be retried automatically.

**Credentials and device**

- **FR-005**: The session credentials (short-lived access and long-lived renewal credential) MUST be
  stored only in the platform's secure storage; never in the app database, logs, plain settings or
  crash reports. PINs and SMS codes MUST NOT be stored at all.
- **FR-006**: Each installation MUST have a stable device identifier, created once and kept with the
  device-only data; it MUST be sent at login, registration, renewal, PIN reset and logout.
- **FR-007**: The access MUST be renewed automatically before or when it expires; concurrent
  renewals MUST be merged into one; a rejected renewal MUST end the local session (US3-3).
- **FR-008**: Fingerprint login MUST be an app-level gate: a successful device biometric check
  (strong or weak) lets the app use the account's stored renewal credential, which the backend
  bounds (30 days, rotation on every use, reuse detection, bound to the device). The credential is
  encrypted with a device key that is not tied to the biometric check (ceiling: readable on a
  compromised/rooted device; upgrade path: biometric-bound key, research R3). Fingerprint login
  MUST NOT unlock an account the backend reports as locked or consume PIN attempts (spec 003
  FR-017); if the backend rejects the credential, the PIN is required.

**Registration**

- **FR-009**: Registration step 3 MUST ask for first name (1–60 characters, required), paternal
  surname (1–60, required), maternal surname (0–60, optional), email and terms; "Continuar" enabled
  only when the required fields are valid.
- **FR-010**: The terms version accepted MUST be sent with the registration.
- **FR-011**: After the code step, the code screen MUST show "¿Ya tienes cuenta? Inicia sesión"
  as a way out, because the backend does not reveal whether a number is registered.
- **FR-012**: If the phone confirmation is no longer valid when creating the account, the flow
  MUST return to the code step keeping the other entered data.
- **FR-013**: On success the session MUST be open immediately (no extra PIN) and "Cuenta creada"
  shown as in spec 003.

**Login, recovery, logout**

- **FR-014**: Login MUST use the phone of the chosen account; an account not yet used on this
  device MUST be reachable by entering its phone number.
- **FR-015**: PIN recovery MUST use the backend's recovery code and reset; on success the account
  is unlocked and login works with the new PIN.
- **FR-016**: Logout MUST end the session in the backend for this device (when reachable) and
  always delete the local credentials.

**Current user**

- **FR-017**: The current user (id, first name, paternal surname, maternal surname, phone, email,
  verification status, account status) MUST come from the backend after login and on return to
  the foreground; the last known value is kept for offline display.
- **FR-018**: The display name used by M2 screens and the M4 contract MUST be "first name +
  paternal surname"; the M4 contract (`CurrentUserSource`) keeps its shape.
- **FR-019**: The accounts listed on the login screen MUST be accounts that logged in on this device
  (name and masked phone), not sample data, in release builds.

**Data on the device**

- **FR-020**: Upgrading an installed build MUST keep working: the device-only data gains the device
  identifier and separate name fields through a migration, not a reinstall.
- **FR-021**: The app MUST always use the backend; there is no offline demo mode. Sample (seed)
  accounts MUST appear only in automated tests. The debug-only "Reiniciar datos" action (spec 003)
  MUST only clear the device-only data and credentials; it no longer offers "con ejemplos".

### Key Entities

- **Session credentials**: short-lived access + renewal credential bound to the device; secure
  storage only.
- **Device identifier**: one per installation; device-only data.
- **Account on this device**: user id, display name, masked phone, biometrics preference, last login
  (extends spec 003 `account_preference` and stored users).
- **Current user**: id, first name, paternal surname, maternal surname, phone, email, verification
  status (`NOT_VERIFIED`, `UNDER_REVIEW`, `VERIFIED`), account status (`ACTIVE`, `LOCKED`).
- **Registration draft**: phone, confirmed-phone proof (kept only in memory), first name, paternal
  surname, maternal surname, email, terms, PIN.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A dev-profile user logs in and sees the main area with their backend name and status
  in under 3 s on a normal connection after entering the 4th digit.
- **SC-002**: A new user completes registration with the separate name fields in under 2 minutes.
- **SC-003**: A user who keeps the app open for 1 hour is never asked for the PIN because of session
  renewal.
- **SC-004**: Inspecting the app's database, settings and logs after login, registration, recovery
  and logout finds 0 PINs, 0 SMS codes and 0 session credentials.
- **SC-005**: After logout, the previous session cannot be renewed (backend rejects it) in 100 % of
  attempts made while the backend was reachable.
- **SC-006**: With the connection off, every M2 action shows the connection message and 0 PIN
  attempts or codes are consumed.
- **SC-007**: An installed spec 003 build upgraded to this version opens without crashing and
  reaches welcome or login.

## Clarifications

### Session 2026-11-17

- Q: Keep an offline demo mode with local sample accounts? → A: No. The app always uses the
  backend; local sample data stays only for automated tests (FR-021).
- Q: Must the fingerprint cryptographically unlock the renewal credential? → A: No. Accept research
  R3: fingerprint is an app-level gate over a Keystore-encrypted credential bounded by the backend;
  a biometric-bound key is a documented upgrade (FR-008).

## Assumptions

- The backend API is the one documented in `BackendQullpi/docs/integration-android/endpoints.md`
  (spec 006 level). Its open items (`docs/backend-requirements/endpoints-change-request.md` rev. 2:
  S1 OTP generator and parallel attempts, S2 code purpose not stored, S3 terms not stored) are
  backend work; **PIN recovery (US5) must not reach a release until S2 is fixed**.
- The backend responds 200 to code requests for already-registered numbers without sending SMS;
  registration then reports "already registered" (409).
- Access lasts 15 minutes and renewal 30 days with rotation, as documented.
- The backend's locked-account answer on renewal is not documented; this spec treats any rejected
  renewal as "go to login" (US3-3).
- Sample accounts from spec 002 are not backend accounts; they are not migrated.
- Android only in this scope; iOS/Desktop secure storage and network engines are Builder work
  (constitution IV).
- New dependencies (HTTP client and JSON) are approved by constitution v2.0.0.

## Dependencies

- Constitution v2.0.0 (branch `docs/000-constitution-backend`, pending approval by both developers).
- Spec 003 screens and contracts (`specs/003-access-registration/contracts/`): flows and activities
  unchanged.
- Shared areas touched (coordinate, §9): `domain/model/User.kt` (current user fields),
  `domain/repository/RegistrationRepository.kt` (`RegistrationForm` name fields), SQLDelight schema
  (device identifier, stored accounts), version catalog (HTTP client).
- M3 (identity verification) builds on this spec's session and current user.
