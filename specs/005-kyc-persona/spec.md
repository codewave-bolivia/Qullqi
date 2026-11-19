# Feature Specification: Identity Verification with Persona (M3)

**Feature Branch**: `feature/005-kyc-persona`

**Created**: 2026-11-17

**Status**: In Progress

**Owner**: krypton612

**Depends on**: 004-backend-auth-integration waiting (integrated)

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

Spec 004 is integrated (PR #14); implementation proceeds on this branch.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Verify my identity end to end (Priority: P1)

A logged-in, not verified customer opens "Verificar mi identidad", reads the requirements and the
privacy notice, continues to Persona (no form: the provider reads the document), completes the
capture there and comes back to the app, which shows the result.

**Why this priority**: it is the milestone: without it the customer can never operate money (M4).

**Independent Test**: with the dev backend (Persona sandbox), user `+59170000002` completes the flow
and sees "Identidad verificada"; the main area shows "Verificado".

**Acceptance Scenarios**:

1. **Given** a not verified user on `kycIntro`, **When** they accept the privacy notice and continue,
   **Then** the verification is created and the Persona page opens (the app shows a waiting screen
   behind it); without accepting, "Continuar" stays disabled.
2. *(Removed 2026-11-17: no document form; see FR-003.)*
3. **Given** Persona's page, **Then** the user photographs the document and takes the selfie there;
   the app never asks for the document type, number or birth date.
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

**Independent Test**: dev user with a rejected verification taps "Intentar de nuevo" and starts a new
verification from the intro.

**Acceptance Scenarios**:

1. **Given** a rejected result, **Then** the reason is shown in plain Spanish (document unreadable,
   data mismatch, identity not verified, under 18, document already used, other) and "Intentar de
   nuevo" returns to the intro to start a new verification.
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
- The customer returns to the app without finishing in Persona: "No terminaste la verificación" with
  "Volver a la verificación" (primary) and "Hacerlo más tarde" (home). No "check now" there: the
  status is known to be unfinished.
- Document already used by another account (number read by the provider): the backend declines
  with `document_in_use` → "Este documento ya está asociado a otra cuenta."
- Under 18 (birth date read by the provider): the backend declines with `underage` → "Debes ser
  mayor de 18 años para verificar tu identidad."
- Camera permission denied (or "don't ask again"): the provider page does not open; the app explains
  and offers "Permitir cámara" (FR-005b).
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
- **FR-002** (the current verification is the user's latest one; earlier declined ones are history): On opening, the app MUST read the current verification from the backend and route:
  none or rejected → intro; created-not-finished → resume (US2); in review → in-review result;
  approved → "Ya verificaste tu identidad".

**Document data**

- **FR-003**: The app MUST NOT ask for any document data (type, number, birth date, names). The
  provider reads them from the photographed document and the backend receives them through the
  webhook (owner decision 2026-11-17, option A).
- **FR-003a**: The backend MUST decline a verification whose document shows an age under 18
  (`underage`) or whose document number already belongs to another account (`document_in_use`);
  the app shows those reasons as fixed Spanish texts (FR-010).
- **FR-004**: *(Removed: no birth date input.)*

**Provider**

- **FR-005**: Continuing MUST create the verification in the backend and open the provider page it
  returns; the app MUST NOT store, inspect or upload the images itself (the provider's page does).
- **FR-005a**: The provider page MUST open **inside the app in a full-screen web view** (portrait,
  Nocturne background, a close button). The web view MUST: allow only the provider's pages over
  HTTPS (any other address opens in the default browser), grant the page camera access only after
  the user allowed the app's camera permission, keep no cookies/cache after it closes, and never
  expose app code to the page.
- **FR-005b**: The provider flow is **camera only** (no file upload; the provider template has no
  upload step). The app MUST ask for the camera permission just before opening the provider page,
  explaining why ("Para fotografiar tu documento y tomar tu selfie"). If denied, the page MUST NOT
  open: the document step shows "Necesitamos tu cámara para verificarte" with "Permitir cámara"
  (asks again or opens the app's system settings when "don't ask again" was chosen).
- **FR-005c**: The web view MUST NOT offer file selection (no file chooser); only the camera is used.
- **FR-006**: When the provider page navigates to the Qullqi return link (`qullqi://kyc/result`), the
  web view MUST close itself and the app MUST show the waiting screen and check the result; closing
  the web view with the close button or back does the same.
- **FR-007**: The waiting screen MUST check the result every 5 s for up to 60 s; pending after that
  is shown as "in review" (not as an error). Not finished → "Volver a la verificación" + "Hacerlo
  más tarde"; no connection → "Reintentar" + "Hacerlo más tarde". Back on any KYC root screen goes
  home.
- **FR-008**: Creating a verification MUST NOT be retried automatically (spec 004 FR-004); a
  connection failure keeps the user on the intro with the connection message.

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
- **Document data**: read by the provider and stored by the backend from the webhook; the app
  never handles it.
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

- Q: How is the provider page opened? → A: In-app web view (owner, 2026-11-17), replacing the Custom
  Tab: the return link is intercepted inside the app, no deep-link activity, no `androidx.browser`
  (FR-005a/b, FR-006). Needs the camera permission; constitution amended to v2.2.0.
- Q: Allow choosing document photos from files in the web view? → A: No. Camera only; the owner
  removed the upload step from the provider flow (FR-005b, FR-005c).
- Q: What is the "Tu documento" form for? → A: It only duplicated what Persona reads and promised a
  comparison that never happened. Removed (option A): the provider reads type, number and birth date;
  the backend checks age (≥ 18) and uniqueness on the verified data (FR-003, FR-003a).
- Q: After a retry the app stayed on "Comprobando…" and then "No pudimos comprobar" although the
  backend had approved. Why? → A: Backend bug: `GET /my/kyc` failed with two verifications for the
  user. The current verification is the **latest one** (FR-002); fixed in the backend.

## Assumptions

- Backend KYC endpoints as in `BackendQullpi/docs/integration-android/endpoints.md` (display-name
  level, commit `830814e`). Open backend items affecting M3 (`docs/backend-requirements/endpoints-change-request.md`):
  C1 (`POST /my/kyc` answers `PENDING` instead of `CREATED`; the app re-reads with `GET` to route),
  C2 (no CI complement; not asked), H1 (expired provider address), H2 (attempt limit undefined;
  the app shows "Contacta a soporte" when refused).
- The provider sandbox is used in dev; the provider supports running inside a web view with camera
  access granted by the host app; the provider's own pages handle capture,
  liveness and their accessibility.
- The return link is `qullqi://kyc/result` with no parameters; the result always comes from the backend.
- Consent text is static in M3; recording consent in the backend is pending (change request L4).
- Spec 004 is integrated before implementation (session renewal, current user refresh, HTTP client).

## Dependencies

- Spec 004 (session, `/me`, HTTP client, error handling) — waiting.
- Constitution v2.1.0 Principle I: `POST /my/kyc`, `GET /my/kyc` and opening the provider page are
  approved; the in-app web view is allowed by constitution **v2.2.0** with its safeguards
  (branch `docs/000-constitution-webview`, pending integration). `androidx.browser` is no longer used. New Android permission:
  `CAMERA` (runtime, FR-005b).
- Shared areas likely touched (coordinate): `domain/repository/KycRepository.kt` and `domain/model/Kyc.kt`
  (contract replaced for the provider flow), `AndroidManifest.xml` (web view activity, `CAMERA`),
  version catalog (remove `androidx.browser`).
