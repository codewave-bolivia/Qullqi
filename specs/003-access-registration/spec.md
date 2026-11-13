# Feature Specification: Access and Registration

**Feature Branch**: `feature/003-access-registration`

**Created**: 2026-11-13

**Status**: Draft

**Owner**: krypton612

**Depends on**: 002-local-data-layer waiting (integrated)

**Contract**: none

**Milestone**: M2 · Acceso y registro

**Input**: User description: "trabajar en el milestone M2 · Acceso y registro, M1 ya tiene todo lo necesario para funcionar tengo entendido"

## Context

M2 delivers the access part of Qullqi Pay: the screens a person sees from opening the app until they
are inside it. It uses the local data layer from spec 002 (users, PIN, session, SMS codes, terms),
already integrated into `develop`.

**Visual source of truth**: `docs/references-views/Qullqi Pay Auth.dc.html` (the access screens and
their guidelines) and the **Nocturne** design system it uses
(`docs/references-views/_ds/nocturne-*/styles.css`, `STYLEGUIDE.md`). Screens MUST look like the
reference. `Qullqi Pay.dc.html` (main app) is not a reference for this feature. Routes:
`ARCHITECTURE.md` §2.1 and §5.2–5.3; component rules: `UI_GUIDELINES.md` §2.3, §3.5, §3.6, §6, §7.

**What M1 already provides**: stored users, PIN check with 3 attempts and lock, single session,
account switching, SMS code issue/confirmation, registration, PIN reset, sample users.

**What M1 does not provide and this feature must address**:

- A per-user "biometrics enabled" preference (spec 002 left it out until M2 needed it).
- The visual foundation and screen-to-screen navigation (milestone M0 items, not yet built). This
  feature builds the **minimum needed for M2**: Nocturne tokens (colors, type, spacing, radii,
  dark default and light theme) and the M2 components (buttons, inputs, PIN/code boxes, keypad,
  flow progress bar, bottom sheet, toast), plus navigation for the access flows and the hand-off.
  These are shared areas: the other developer reuses them for M4 once integrated.

**Out of scope**: identity verification screens (M3), the main area with balances and activity (M4,
other developer), profile and settings (M9).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - App start routes the person correctly (Priority: P1)

When the app opens, a short splash with the Qullqi wordmark and a loading bar is shown. If at least
one account exists on the device, the person goes to login; otherwise to the welcome screen with
"Crear cuenta" and "Ya tengo cuenta".

**Why this priority**: it is the entry to every other screen.

**Independent Test**: start with sample data → login is shown; start with an empty device → welcome
is shown.

**Acceptance Scenarios**:

1. **Given** at least one stored account, **When** the app starts, **Then** after the splash
   (about 1.9 s) the login screen appears for the most recently used account.
2. **Given** no stored account, **When** the app starts, **Then** the welcome screen appears.
3. **Given** the welcome screen, **When** "Ya tengo cuenta" is chosen with no stored accounts,
   **Then** the person is told no account exists on this device and offered "Crear cuenta".

---

### User Story 2 - Returning person logs in with PIN (Priority: P1)

The person enters their 4-digit PIN on a custom 3×4 keypad. A correct PIN shows "Ingresando…"
and enters the app. A wrong PIN shakes the boxes, clears them and shows the remaining attempts.
After 3 wrong attempts the account is locked and only "Recuperar PIN" is offered. The person can
switch to another stored account.

**Why this priority**: the most frequent flow for existing users.

**Independent Test**: with sample users: correct PIN enters; 3 wrong PINs lock; switching account
changes the login target.

**Acceptance Scenarios**:

1. **Given** the login for "Lucía Prueba Quispe", **When** `1234` is entered, **Then** "Ingresando…"
   is shown and the person reaches the main area (US6).
2. **Given** a wrong PIN, **When** the 4th digit is entered, **Then** the boxes shake, clear, and
   the text shows "Te quedan 2 intentos" (then 1).
3. **Given** the last attempt fails, **When** the 4th digit is entered, **Then** the screen changes to
   the locked state with "Recuperar PIN" and no keypad.
4. **Given** a locked sample account, **When** login opens for it, **Then** the locked state is shown
   directly.
5. **Given** several stored accounts, **When** "Cambiar de cuenta" is chosen, **Then** a list of
   stored accounts appears (name and masked phone) plus "Agregar otra cuenta", and choosing one
   shows its login.

---

### User Story 3 - New person creates an account (Priority: P1)

A 4-step flow with a segmented progress bar and a "n/4" counter: (1) mobile number +591,
(2) 6-digit SMS code with resend after 30 s, (3) name, email and terms, (4) create and confirm PIN.
It ends on a "Cuenta creada" screen showing the account as "Sin verificar" with "Verificar mi
identidad" and "Hacerlo más tarde".

**Why this priority**: entry for every new user; precondition for M3.

**Independent Test**: on an empty device, complete the 4 steps with the sample code `123456`; the
account exists, is "Sin verificar", and the person is inside the app.

**Acceptance Scenarios**:

1. **Given** step 1, **When** a number not matching 8 digits starting with 6 or 7 is typed, **Then**
   "Enviar código" stays disabled and the field shows the rule.
2. **Given** a valid number already registered on the device, **When** "Enviar código" is pressed,
   **Then** the person is told the account exists and offered to log in.
3. **Given** step 2, **When** the screen opens, **Then** a 30-second countdown is shown and
   "Reenviar código" becomes available when it reaches 0.
4. **Given** step 2, **When** a wrong code is entered, **Then** the boxes shake and the remaining
   tries are shown; after 3 wrong tries or 5 minutes the person must request a new code.
5. **Given** step 3, **When** the email is invalid or terms are not accepted, **Then** "Continuar"
   stays disabled with live validation messages.
6. **Given** step 4, **When** the confirmation PIN differs, **Then** the boxes shake and the person
   re-enters the confirmation.
7. **Given** the account is created, **When** "Cuenta creada" appears, **Then** "Verificar mi
   identidad" leads to the identity verification entry (M3) and "Hacerlo más tarde" leads to the
   main area.
8. **Given** any step after 1, **When** "volver" is pressed, **Then** the previous step is shown with
   its data kept.

---

### User Story 4 - Locked or forgetful person recovers their PIN (Priority: P2)

From the locked state or "¿Olvidaste tu PIN?", the person confirms their phone with an SMS code and
creates a new PIN. The account is unlocked and they return to login.

**Why this priority**: without it a locked sample account is a dead end, but it is less frequent.

**Independent Test**: lock an account, recover with code `123456` and a new PIN, then log in with
the new PIN.

**Acceptance Scenarios**:

1. **Given** a locked account, **When** "Recuperar PIN" is chosen, **Then** a code is sent to that
   account's phone (masked on screen) without retyping it.
2. **Given** a confirmed code, **When** a new PIN is entered twice identically, **Then** a toast
   "PIN actualizado" appears and login opens with attempts reset.

---

### User Story 5 - Person logs in with biometrics (Priority: P2)

On login, the bottom-left key of the keypad is the fingerprint key. If biometrics are enabled for
the account and available on the device, using it enters the app without the PIN.

**Why this priority**: convenience; PIN remains the fallback.

**Independent Test**: with biometrics enabled for a sample account, the fingerprint key enters the app; with it disabled, the key is hidden.

**Acceptance Scenarios**:

1. **Given** biometrics enabled for the account, **When** the fingerprint key is used and
   recognition succeeds, **Then** the person enters the app as with a correct PIN.
2. **Given** recognition fails or is cancelled, **When** the person returns to login, **Then** the
   PIN keypad remains usable and no PIN attempt is consumed.
3. **Given** biometrics not enabled or not available, **When** login opens, **Then** the fingerprint
   key is not shown.
4. **Given** a locked account, **When** login opens, **Then** biometrics cannot unlock it.
5. **Given** the first successful login after registration, **When** the device supports
   biometrics, **Then** the person is offered "Activar huella" once; accepting enables it.

Biometric mechanism: the device's **real biometric prompt on Android** (verified on a physical
device). On iOS and Desktop the fingerprint key is not shown until the Builder provides an
implementation.

---

### User Story 6 - Hand-off to the rest of the app (Priority: P2)

After login or registration the person reaches the main area. The main area belongs to the other
developer (M4) and is not built here; until it exists, a temporary placeholder shows the person's
name, their verification status, "Verificar" (when not verified) and "Cerrar sesión". Logging out
returns to login.

**Why this priority**: makes M2 demonstrable end to end without waiting for M4.

**Independent Test**: log in, see the placeholder with the right name and status, log out, land on
login.

**Acceptance Scenarios**:

1. **Given** a successful login, **When** the main area opens, **Then** it shows the current
   person's name and verification status.
2. **Given** the placeholder, **When** "Cerrar sesión" is confirmed, **Then** login opens for the same
   account.
3. **Given** a not-verified person, **When** "Verificar" is chosen, **Then** the identity
   verification entry (M3) opens; until M3 exists it is a placeholder that says so.

### Edge Cases

- App sent to background during the splash: on return, routing continues normally.
- Rotation or window resize in the middle of a step: entered data and step are kept.
- Desktop/tablet: physical keyboard digits and Backspace work on PIN and code boxes; Enter submits.
- Paste of a 6-digit code: fills all boxes.
- Countdown keeps running correctly if the app is backgrounded during step 2.
- Back from step 1 returns to welcome; back from login with several accounts returns to the account
  list.
- Platforms where the local data is not yet available (iOS/Desktop until the Builder provides it):
  the app shows a clear "no disponible en esta plataforma todavía" screen instead of crashing.

## Requirements *(mandatory)*

### Functional Requirements

**Entry**

- **FR-001**: The app MUST show the splash for about 1.9 s with the wordmark and a loading bar, then
  route to login (≥ 1 stored account) or welcome (none).
- **FR-002**: Login MUST target the most recently used account; others are reachable via
  "Cambiar de cuenta".

**Login**

- **FR-003**: PIN entry MUST use 4 boxes and a custom 3×4 keypad; the system keyboard MUST NOT open.
- **FR-004**: The PIN MUST be checked when the 4th digit is entered, with no extra button.
- **FR-005**: Wrong PIN MUST shake and clear the boxes and show the remaining attempts; reaching
  0 MUST show the locked state with only "Recuperar PIN".
- **FR-006**: A successful login MUST show "Ingresando…" briefly and then open the main area.
- **FR-007**: The account list MUST show names and masked phones (`+591 7••• ••01`) and an
  "Agregar otra cuenta" action that starts registration.

**Registration**

- **FR-008**: Registration MUST be a 4-step flow with a segmented progress bar, an "n/4" counter, a
  question as title and one help line (UI_GUIDELINES §2.3, §7).
- **FR-009**: Step 1 MUST accept only 8 digits starting with 6 or 7, with the +591 prefix shown.
- **FR-010**: Step 2 MUST show 6 boxes, a 30 s resend countdown, remaining tries on error, and require
  a new code after 3 wrong tries or 5 minutes.
- **FR-011**: Step 3 MUST validate name (not blank), email (format) and terms acceptance live, and
  keep "Continuar" disabled until valid.
- **FR-012**: Step 4 MUST ask for the PIN twice; mismatch MUST shake and clear the confirmation.
- **FR-013**: On success the account MUST be stored as "Sin verificar", the session opened, and the
  "Cuenta creada" screen offered with "Verificar mi identidad" and "Hacerlo más tarde".
- **FR-014**: Each step MUST keep its data when going back and when the screen is rotated or resized.

**PIN recovery**

- **FR-015**: Recovery MUST confirm the account's phone with an SMS code and set a new PIN entered
  twice; success MUST unlock the account and show "PIN actualizado".

**Biometrics**

- **FR-016**: Each account MUST have a "biometrics enabled" preference, off by default, stored with the
  account's local data.
- **FR-017**: The fingerprint key MUST appear only when the preference is on and the device can
  perform the check; it MUST NOT unlock a locked account or consume PIN attempts.
- **FR-018**: After the first successful login of a new account, "Activar huella" MUST be offered
  once.

**Hand-off**

- **FR-019**: After login/registration the app MUST open the main-area entry point. Until M4
  provides it, a placeholder MUST show name, verification status, "Verificar" (if not verified) and
  "Cerrar sesión".
- **FR-020**: "Verificar" and "Verificar mi identidad" MUST open the M3 entry point; until M3 exists,
  a placeholder.
- **FR-021**: Logout MUST ask for confirmation in a bottom sheet and return to login for the same
  account.

**Quality**

- **FR-022**: Every screen MUST adapt to phone, tablet and resizable desktop windows: content in a
  readable centered column on wide windows, no horizontal overflow, keypad and boxes usable by
  touch, mouse and keyboard.
- **FR-023**: Texts MUST follow UI_GUIDELINES §7: Spanish (Bolivia), "tú", concrete CTAs (no
  "Aceptar"/"OK").
- **FR-024**: Touch targets MUST be at least 44×44; keyboard focus MUST be visible.
- **FR-025**: The PIN MUST never be shown, logged or kept after it is checked.

**Visual foundation (shared)**

- **FR-026**: The app MUST use the Nocturne tokens from `_ds/nocturne-*/styles.css`: accent
  `#9184d9` with its tonal ramp, background `#161826`, surface `#232532`, text `#e9e9ed`, neutral
  ramp, divider, negative and warning colors; font Inter (bundled with the app, never downloaded at
  runtime).
- **FR-027**: Dark MUST be the default theme; a light theme derived from the same tokens MUST exist
  (STYLEGUIDE / UI_GUIDELINES §8). Only the Nocturne accent is in scope (Electric Lime excluded).
- **FR-028**: Components MUST follow UI_GUIDELINES §3: primary buttons are outlined with an 8 %
  accent fill and glow, never solid; disabled at 40 % opacity; inputs with live validation colors;
  PIN boxes 54×62 and 6-box code grid; 3×4 keypad with 54–58 high keys.
- **FR-029**: Transitions MUST follow ARCHITECTURE.md §6 (push from right, back from left, sheets
  from bottom, result pop).
- **FR-030**: Colors, type sizes and spacing MUST come only from the shared tokens; screens MUST NOT
  hardcode them (constitution VI).

### Key Entities

- **Account (from spec 002)**: name, phone, verification status, locked state; listed for login.
- **Biometric preference (new)**: per account, enabled/disabled, prompted-once flag.
- **Registration draft**: phone, code state, name, email, terms acceptance — kept only during the
  flow, never stored after cancellation.
- **Main-area and verification entry points**: navigation destinations owned by M4 and M3; M2
  only navigates to them.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A returning person reaches the main area in under 5 seconds from app start (including
  the splash) using their PIN.
- **SC-002**: A new person completes registration in under 2 minutes with the sample code.
- **SC-003**: 100% of the routes in ARCHITECTURE.md §2.1 for access and registration
  (`splash`, `welcome`, `login`, `regPhone`, `regOtp`, `regInfo`, `regPin`, `regDone`) are reachable
  with sample data.
- **SC-004**: Every screen is usable without horizontal scrolling at 360 px wide and at a 1280 px
  wide window.
- **SC-005**: 0 cases where the PIN or SMS code appears on screen in clear text or in logs.
- **SC-006**: A locked sample account can be recovered and used again in under 1 minute.

## Clarifications

### Session 2026-11-13

- Q: Build the visual foundation and navigation in this feature or in a separate M0 spec? → A: In
  this feature, minimum needed for M2 (Nocturne style from the Auth reference), reusable by M4.
- Q: Real or simulated biometrics? → A: Real device biometrics on Android, tested on a physical
  device; hidden on iOS/Desktop.
- Visual source: only `Qullqi Pay Auth.dc.html` and its guidelines; same Nocturne style everywhere.

## Assumptions

- The data layer of spec 002 is the only data source; no network.
- Most recently used account = the account of the last opened session; if none, the first by name.
- Terms text is a static placeholder screen; its content is out of scope.
- Screen transitions follow ARCHITECTURE.md §6 (push from right, back from left, sheets from bottom).
- iOS and Desktop show the "no disponible todavía" screen until the Builder provides the local data
  implementations for them (constitution IV); Android is the verified platform.
- The main-area and M3 placeholders will be replaced by their owners; their routes are the
  hand-off contract.

## Dependencies

- Depends on: spec 002 (integrated), constitution v1.1.0.
- Needs a small extension of spec 002's data (biometric preference): a shared-area change
  (`sqldelight/`, `domain/`) requiring the other developer's approval.
- Creates shared areas (design system tokens/components, navigation): the other developer should
  review them, since M4 will reuse them; changes there need coordination (CLAUDE.md §9).
- Provides to M3 and M4: the navigation entry points they replace.
