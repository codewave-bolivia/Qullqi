# Feature Specification: Identity Verification with Persona (M3)

**Feature Branch**: `feature/005-kyc-persona`

**Created**: 2026-11-17

**Status**: Draft

**Owner**: krypton612

**Depends on**: 004-backend-auth-integration waiting

**Contract**: none

**Input**: User description: "trabajar en M3"

## Context

M3 is identity verification (KYC). Specs 003/004 left an entry point: `kycIntro` inside AuthActivity,
reached from "Verificar mi identidad" after registration and from "Verificar" in the main area; it is
a placeholder today (`AuthFlow(kycEntry = …)` slot).

The backend (`BackendQullpi`, constitution v2.1.0 Principle I scope: `POST /my/kyc`, `GET /my/kyc`)
creates a Persona verification and returns a **Persona web address** where the person captures the
document and the selfie. Persona reports the result to the backend (webhook); the app only reads the
result from the backend. Legal names come from the document (Persona → backend), so the app never asks
for them (spec 004 FR-009).

This replaces the reference design's in-app capture screens (`kycFront`, `kycBack`, `kycSelfie`,
`kycData`, `kycProc`, docs/references-views/ARCHITECTURE.md): **Persona's own pages do the capture**;
no camera code and no images in the app.

**In scope**: intro with requirements and privacy notice, document type + number + birth date,
opening Persona, returning to the app, waiting for the result, result screens (approved / in review
/ rejected with retry), resuming an unfinished verification, verification badge refresh.

**Out of scope**: money operations and the `kycGate` sheet (M4), any in-app camera/liveness capture,
editing legal names, manual review tools, iOS/Desktop (Builder).

**Implementation waits for spec 004 to be integrated** (session, current user, HTTP client); this
branch only holds the specification until then (CLAUDE.md §15.3, parent without approved contract).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Verify my identity end to end (Priority: P1)

A logged-in, not verified customer opens "Verificar mi identidad", reads the requirements and the
privacy notice, chooses CI or passport, types the document number and birth date, continues to
Persona, completes the capture there and comes back to the app, which shows the result.

**Why this priority**: it is the milestone: without it the customer can never operate money (M4).

**Independent Test**: with the dev backend (Persona sandbox), user `+59170000002` completes the flow
and sees "Identidad verificada"; the main area shows "Verificado".

**Acceptance Scenarios**:

1. **Given** a not verified user on `kycIntro`, **When** they accept the privacy notice and continue,
   **Then** the document step opens; without accepting, "Continuar" stays disabled.
2. **Given** the document step, **When** the type is chosen, the number is 5–15 letters/digits and the
   birth date makes them 18 or older, **Then** "Continuar a la verificación" is enabled.
3. **Given** valid data, **When** they continue, **Then** the Persona page opens and the app shows a
   waiting screen behind it.
4. **Given** Persona finishes and returns to the app, **Then** the app checks the result and shows:
   approved → "Identidad verificada"; in review → "Estamos revisando tus datos"; rejected →
   "No pudimos verificarte" with the reason and "Intentar de nuevo".
5. **Given** the result is approved or in review, **When** they tap the final button, **Then** they reach
   the main area, whose status shows "Verificado" or "Verificación en revisión".

---

### User Story 2 - Resume an unfinished verification (Priority: P2)

A customer who closed Persona before finishing comes back later and continues where they left off,
without typing their data again.

**Why this priority**: closing the page by mistake must not block the customer (the backend refuses a
second verification while one is open).

**Independent Test**: start a verification, close Persona without finishing, reopen "Verificar":
the app offers "Continuar verificación" and opens Persona again.

**Acceptance Scenarios**:

1. **Given** a verification created but not finished, **When** `kycIntro` opens, **Then** it shows
   "Tienes una verificación pendiente" with "Continuar verificación", which reopens Persona.
2. **Given** a verification already submitted (in review), **When** `kycIntro` opens, **Then** it shows the
   in-review state instead of starting again.

---

### User Story 3 - Retry after a rejection (Priority: P2)

A rejected customer reads why and starts a new verification.

**Independent Test**: dev user with a rejected verification taps "Intentar de nuevo" and reaches the
document step with fields empty.

**Acceptance Scenarios**:

1. **Given** a rejected result, **Then** the reason is shown in plain Spanish (document unreadable,
   data mismatch, identity not verified, other) and "Intentar de nuevo" opens the document step.
2. **Given** the backend refuses another attempt, **Then** the screen says "Contacta a soporte" and
   offers to go to the main area.

---

### User Story 4 - Status always current (Priority: P3)

The verification status shown in the main area updates without logging in again after Persona
finishes or the backend changes it.

**Acceptance Scenarios**:

1. **Given** a result in review, **When** the backend approves it later and the customer returns to
   the app, **Then** the main area shows "Verificado".

### Edge Cases

- No connection while creating the verification or checking the result: "No pudimos conectarnos.
  Intenta de nuevo."; data kept; nothing created twice.
- Persona returns but the result is not ready yet: the waiting screen checks again for up to 60 s,
  then shows "Estamos revisando tus datos" (in review) instead of an error.
- The customer returns to the app without Persona's return link (switches apps manually): the waiting
  screen offers "Ya terminé" (check now) and "Volver a Persona".
- Document number already used by another account: "Este documento ya está asociado a otra cuenta."
- Under 18: "Debes ser mayor de 18 años para verificar tu identidad." before contacting the backend.
- Already verified (e.g. verified on another device): the intro shows "Ya verificaste tu identidad"
  and goes to the main area.
- Session expired while in Persona: on return the app asks for the PIN (spec 004 FR-016a) and then
  shows the result.
- Persona page address expired: the app asks the backend again (resume) instead of opening a dead page.
- Return link opened while no verification is in progress: ignored; the app opens normally.

## Requirements *(mandatory)*

### Functional Requirements

**Entry and intro**

- **FR-001**: `kycIntro` MUST explain what is needed (document at hand, good light, a few minutes)
  and show the privacy notice: the document and selfie are processed by a verification provider on
  behalf of Qullqi; acceptance is required to continue.
- **FR-002**: On opening, the app MUST read the current verification from the backend and route:
  none or rejected → intro; created-not-finished → resume (US2); in review → in-review result;
  approved → "Ya verificaste tu identidad".

**Document data**

- **FR-003**: The document step MUST ask for type (CI or passport), number (5–15 letters or digits,
  no spaces) and birth date (a past date; age ≥ 18 years on the device's date), and MUST NOT ask for
  names (they come from the document).
- **FR-004**: The birth date MUST be entered without the system keyboard defaulting to letters, in
  day/month/year order, and validated live.

**Provider**

- **FR-005**: Continuing MUST create the verification in the backend and open the provider page it
  returns, outside the app's own screens; the app MUST NOT capture, store or upload images itself.
- **FR-005a**: The provider page MUST open in a browser tab shown over the app (Custom Tab) with
  Qullqi's colors; if the phone has no browser supporting it, it falls back to the default browser.
  The tab belongs to the browser, not to the app (no app activity is added; portrait rule unaffected).
- **FR-006**: When the provider page finishes, it returns to the app through the Qullqi return link;
  the app MUST then show the waiting screen and check the result.
- **FR-007**: The waiting screen MUST check the result every 5 s for up to 60 s; pending after that
  is shown as "in review" (not as an error). It MUST also offer "Ya terminé" and "Volver a la
  verificación".
- **FR-008**: Creating a verification MUST NOT be retried automatically (spec 004 FR-004); a
  connection failure keeps the entered data.

**Results**

- **FR-009**: Results MUST be: approved ("Identidad verificada" → main area), in review ("Estamos
  revisando tus datos. Te avisaremos." → main area), rejected (reason + "Intentar de nuevo").
- **FR-010**: Rejection reasons MUST be shown as fixed Spanish texts by code: data mismatch,
  document unreadable/expired, identity not verified; any other code → "No pudimos verificar tu
  identidad."
- **FR-011**: Backend refusals MUST map to messages: already verified → approved screen; verification
  already in progress → resume/in-review; document already used → FR edge text; invalid data →
  field errors; attempts exhausted → "Contacta a soporte".

**Status**

- **FR-012**: After a result, the current user MUST be refreshed so the main area and M4 see the new
  verification status (spec 004 FR-017).

**Hand-off**

- **FR-013**: The flow MUST plug into the existing entry (`kycEntry` slot) and end with the existing
  "done" callback, returning to the main area as specs 003/004 define; no change to MainActivity
  or M2 screens.
- **FR-014**: All screens MUST use the Nocturne tokens and the registration step layout (bar, title,
  help line), be portrait-locked on phones and responsive on large screens (spec 003 FR-022, FR-035).

### Key Entities

- **Verification (KYC inquiry)**: status (`CREATED` not finished · `PENDING` in review · `APPROVED`
  · `DECLINED`), provider page address (only while `CREATED`), decline reason code, timestamps.
  Lives in the backend; the app keeps no copy beyond the screen state.
- **Document data (draft)**: type, number, birth date; in memory only until sent.
- **Privacy acceptance**: shown and accepted per verification (not stored in M3).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A customer goes from "Verificar mi identidad" to the provider page in under 1 minute
  (3 screens max).
- **SC-002**: After the provider finishes, the result (or the in-review state) is shown within 60 s in
  100 % of cases with connection.
- **SC-003**: A customer who closes the provider page resumes without retyping data in 100 % of cases
  while the verification is unfinished.
- **SC-004**: 0 document images or selfies are stored by the app (inspection of app data).
- **SC-005**: After approval, the main area shows "Verificado" without logging in again.

## Clarifications

### Session 2026-11-17

- Q: How is the provider page opened? → A: Custom Tab (in-app browser tab over the app); fallback to
  the default browser when unsupported (FR-005a). Requires approving the `androidx.browser` library.

## Assumptions

- Backend KYC endpoints as in `BackendQullpi/docs/integration-android/endpoints.md` (display-name
  level, commit `830814e`). Open backend items affecting M3 (`docs/backend-requirements/endpoints-change-request.md`):
  C1 (`POST /my/kyc` answers `PENDING` instead of `CREATED`; the app re-reads with `GET` to route),
  C2 (no CI complement; not asked), H1 (expired provider address), H2 (attempt limit undefined;
  the app shows "Contacta a soporte" when refused).
- The provider sandbox is used in dev; the provider's own pages handle camera permission, capture,
  liveness and their accessibility.
- The return link is `qullqi://kyc/result` with no parameters; the result always comes from the backend.
- Consent text is static in M3; recording consent in the backend is pending (change request L4).
- Spec 004 is integrated before implementation (session renewal, current user refresh, HTTP client).

## Dependencies

- Spec 004 (session, `/me`, HTTP client, error handling) — waiting.
- Constitution v2.1.0 Principle I: `POST /my/kyc`, `GET /my/kyc` and opening the provider page are
  approved. New dependency `androidx.browser` (Custom Tabs) approved by the owner 2026-11-17 (Q1),
  as the constitution's follow-up anticipated; Android-only, in `androidApp`.
- Shared areas likely touched (coordinate): `domain/repository/KycRepository.kt` and `domain/model/Kyc.kt`
  (contract replaced for the provider flow), `AndroidManifest.xml` (return link), version catalog (if
  Custom Tab).
