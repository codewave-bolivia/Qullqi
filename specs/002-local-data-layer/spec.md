# Feature Specification: Local Data Layer — Identity and Verification Model

**Feature Branch**: `feature/002-local-data-layer`

**Created**: 2026-11-13

**Status**: Draft

**Owner**: krypton612

**Depends on**: none

**Contract**: Draft

**Milestone**: M1 · Capa de datos local (SQLDelight)

**Input**: User description: "M1 · Capa de datos local (SQLDelight) preparar el modelo base para cumplir con M2 Y M3 los M4 y M5 y mas son de otro programador."

## Context

Qullqi has no backend yet. Until one exists, the app keeps its data in a local store on the
device, filled with synthetic sample data. This feature defines the **base data model** that the
next two milestones need:

- **M2 · Access and registration**: splash, welcome, PIN/biometric login, phone + SMS code
  registration, profile data, PIN creation.
- **M3 · Identity verification (KYC)**: document type, front/back capture, selfie, extracted
  data, sworn declaration, processing and result.

Milestones **M4 and later** (balances, activity, transfers, payments, cards, profile settings)
belong to another developer. This feature does **not** model their data. It only publishes a small,
stable **contract** (the user's identity and verification status) that those milestones will
build on.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Returning user is recognized and can log in (Priority: P1)

A person who already has an account on this device opens the app. The app recognizes them, asks
for their 4-digit PIN, and lets them in when it is correct. Wrong PINs reduce the remaining
attempts; when none remain, the account is locked until the PIN is recovered.

**Why this priority**: every other screen in M2–M9 starts after a successful login; without a
stored user and credential nothing can be demonstrated.

**Independent Test**: start the app with the sample data; the existing sample user is offered,
the correct sample PIN opens the session, wrong PINs decrement attempts and lock at zero.

**Acceptance Scenarios**:

1. **Given** the sample data contains an existing user, **When** the app starts, **Then** that user
   is recognized and login by PIN is offered instead of the welcome screen.
2. **Given** a recognized user, **When** the correct PIN is entered, **Then** a session is opened
   and the failed-attempt counter is reset.
3. **Given** a recognized user with 3 attempts left, **When** a wrong PIN is entered, **Then** 2
   attempts remain and no session is opened.
4. **Given** 1 attempt left, **When** a wrong PIN is entered, **Then** the user becomes locked and
   only PIN recovery is offered.
5. **Given** no user on the device, **When** the app starts, **Then** the welcome screen is shown.
6. **Given** more than one user stored on the device, **When** the person chooses "switch account",
   **Then** they can pick another stored user and log in with that user's PIN.

---

### User Story 2 - New person registers an account (Priority: P1)

A new person registers with their Bolivian mobile number, confirms it with a 6-digit SMS code,
enters name and email, accepts the terms, and creates a PIN. The account is stored on the device
as "not verified" and can be used immediately.

**Why this priority**: registration is the entry point for every new user and the precondition
for KYC (US3).

**Independent Test**: on an empty device, complete registration with valid inputs; the new user
exists, is "not verified", has accepted the current terms, and can log in with the new PIN.

**Acceptance Scenarios**:

1. **Given** a phone number of 8 digits starting with 6 or 7, **When** a code is requested,
   **Then** a 6-digit code is issued for that number, valid for 5 minutes, and a new code cannot be
   requested for 30 seconds.
2. **Given** an issued code, **When** the correct code is entered within its validity, **Then** the
   phone is confirmed; a wrong code reduces the remaining tries (3 in total) and an expired code is
   rejected.
3. **Given** a confirmed phone, **When** name, a valid email and terms acceptance are provided and
   the PIN is entered twice identically, **Then** the user is created with status "not verified",
   the terms version and acceptance time are recorded, and a session is opened.
4. **Given** a phone number already registered on the device, **When** registration is attempted,
   **Then** it is refused and the person is directed to log in.
5. **Given** a PIN confirmation that does not match, **When** submitted, **Then** the user is not
   created.

---

### User Story 3 - User verifies their identity (Priority: P1)

A registered user who is "not verified" goes through identity verification: chooses ID card (CI)
or passport, captures the document (front, and back for CI), takes a selfie, reviews the extracted
data, accepts the sworn declaration and submits. The verification is processed and ends as
approved, under review, or rejected. The user's verification status changes accordingly.

**Why this priority**: verification status decides whether money operations (M4+) are allowed;
the other developer's milestones depend on it.

**Independent Test**: with a "not verified" sample user, complete a verification using each of
the sample outcome scenarios; the user's status becomes verified, under review, or rejected with a
reason, and the history of attempts is kept.

**Acceptance Scenarios**:

1. **Given** a "not verified" user, **When** they start verification, **Then** a new verification
   attempt is created in "draft" and the user's status is unchanged.
2. **Given** a draft attempt with document type CI, **When** front, back, selfie, reviewed data and
   sworn-declaration acceptance are present, **Then** the attempt can be submitted; with passport,
   the back image is not required.
3. **Given** a draft missing any required item, **When** submission is attempted, **Then** it is
   refused, listing what is missing.
4. **Given** a submitted attempt, **When** processing ends as approved, **Then** the user becomes
   "verified"; as under review, the user becomes "under review"; as rejected, the user returns
   to "not verified" and the rejection reason is kept on the attempt.
5. **Given** a previous rejected attempt, **When** the user retries, **Then** a new attempt is
   created and earlier attempts remain in history.
6. **Given** a "verified" user, **When** verification is opened, **Then** no new attempt can be
   started.

---

### User Story 4 - Sample data covers every M2/M3 state (Priority: P2)

Developers and testers open the app and find ready-made sample users that put every M2 and M3
screen in each of its states (empty device, returning user, locked user, unverified, under review,
verified, rejected), without typing anything.

**Why this priority**: it makes M2/M3 screens demonstrable and testable, but the model (US1–US3)
must exist first.

**Independent Test**: reset the local data, list the sample users, and confirm one sample per
state listed below, with fixed identifiers and values that are identical on every reset.

**Acceptance Scenarios**:

1. **Given** a reset, **When** the sample data loads, **Then** it contains exactly the sample users
   in the Key Entities "Sample data" list, with the same identifiers and values every time.
2. **Given** the sample data, **When** inspected, **Then** no real person's name, phone number,
   email, document number or image appears.
3. **Given** a developer wants the "empty device" state, **When** they choose it, **Then** the app
   starts with no users.

---

### User Story 5 - Other milestones can rely on a stable contract (Priority: P2)

The developer working on M4 and later can read the logged-in user's identity and verification
status, and react when it changes, without depending on how M2/M3 store their data.

**Why this priority**: lets the other developer start M4 in parallel (stacked branch) once the
contract is approved.

**Independent Test**: a reviewer, using only the Contract section, can answer: how to get the
current user, their verification status, and be notified when it changes.

**Acceptance Scenarios**:

1. **Given** an open session, **When** another milestone asks for the current user, **Then** it
   receives the user's identifier, display name, phone, email and verification status.
2. **Given** a verification status change, **When** another milestone is observing it, **Then** it
   is notified with the new status.
3. **Given** no open session, **When** another milestone asks for the current user, **Then** it is
   told there is none.

### Edge Cases

- Code requested again before 30 seconds: refused, showing the remaining wait.
- Code tries exhausted: the code is invalidated; a new one must be requested.
- App closed during registration after confirming the phone: no user exists until the PIN step
  completes; the registration restarts from the phone step.
- App closed with a draft verification attempt: the draft remains and can be resumed or discarded.
- Two attempts submitted at once: only one non-final attempt per user may exist at a time.
- Locked user: login is refused even with the correct PIN until PIN recovery completes.
- Logout: the session closes; the user remains stored on the device and appears for login.
- Email or phone with surrounding spaces or mixed case: stored normalized (trimmed, email
  lowercase).
- Terms updated to a new version: existing users keep the version they accepted; nothing in M2/M3
  forces re-acceptance in this feature.

## Requirements *(mandatory)*

### Functional Requirements

**Users and credentials (M2)**

- **FR-001**: The system MUST store users locally with: identifier, full name, phone number,
  email, verification status, created time, and a flag "new user" (true until first completed
  session after registration).
- **FR-002**: Phone numbers MUST be Bolivian mobile numbers: 8 digits starting with 6 or 7, stored
  with country code +591; each phone number MUST belong to at most one user on the device.
- **FR-003**: Emails MUST be validated for format and stored trimmed and lowercase.
- **FR-004**: The PIN MUST be 4 digits and MUST NOT be stored in a readable form; only a protected
  representation that can be verified.
- **FR-005**: Each user MUST have a failed-PIN counter with a maximum of 3 attempts; reaching 0
  remaining MUST lock the user; a successful login MUST reset the counter.
- **FR-006**: The system MUST keep at most one open session at a time, linked to one user, with
  opened time; logout MUST close it without deleting the user.
- **FR-007**: The system MUST record which terms version each user accepted and when.
- **FR-008**: The system MUST allow several users on the same device and list them for
  "switch account".

**Phone confirmation (M2)**

- **FR-009**: The system MUST record each phone confirmation request: phone, 6-digit code, issue
  time, expiry (5 minutes), remaining tries (3), and whether it was confirmed.
- **FR-010**: A new code for the same phone MUST NOT be issued within 30 seconds of the previous one.
- **FR-011**: While there is no backend, the issued code MUST be predictable in sample mode so that
  testers can complete registration (documented fixed code).

**Identity verification (M3)**

- **FR-012**: Users MUST have exactly one verification status: not verified, under review or
  verified. A rejected attempt returns the user to "not verified"; the rejection reason lives on
  the attempt.
- **FR-013**: The system MUST store verification attempts per user with: document type (CI or
  passport), document number, front image reference, back image reference (CI only), selfie
  reference, extracted data (full name, document number, birth date, expiry date), sworn
  declaration accepted (yes/no and time), state (draft, submitted, approved, under review,
  rejected), rejection reason, created and finished times.
- **FR-014**: An attempt MUST NOT be submitted unless all items required for its document type are
  present.
- **FR-015**: At most one non-final attempt (draft or submitted) MUST exist per user at a time.
- **FR-016**: When an attempt reaches a final state, the user's verification status MUST be
  updated in the same operation, so both never disagree.
- **FR-017**: Earlier attempts MUST be kept as history and never overwritten.
- **FR-018**: While there is no backend, the processing outcome MUST be decided deterministically
  from the sample scenario (documented rule), so each outcome can be reproduced.
- **FR-019**: Real camera captures (document and selfie) MUST NOT be stored. Each attempt stores
  only a reference to a synthetic placeholder image bundled with the app; the camera may be shown
  on screen but its output is discarded.

**Relationships and integrity**

- **FR-020**: Every session, phone confirmation linked to a user, terms acceptance and verification
  attempt MUST reference an existing user; deleting a user MUST delete their sessions,
  terms acceptances and verification attempts.
- **FR-021**: Integrity rules (references, uniqueness of phone, single non-final attempt, single open
  session) MUST be enforced by the data store itself, not only by screens.

**Sample data**

- **FR-022**: The system MUST provide deterministic synthetic sample data covering every state in
  the "Sample data" list; resetting MUST restore exactly the same data.
- **FR-023**: Sample data MUST NOT contain real personal data or real document images.
- **FR-024**: Before the first release, data structure changes MAY reset local data instead of
  migrating it (constitution, Technical Constraints).

**Contract for M4+ (other developer)**

- **FR-025**: The system MUST expose to other milestones: the current user (identifier, display
  name, phone, email, verification status) or "no session", and a way to observe changes of the
  current user and their verification status.
- **FR-026**: Other milestones MUST be able to reference a user by its identifier in their own
  data; that identifier MUST be stable for the life of the user.
- **FR-027**: This feature MUST NOT define balances, accounts, transactions, recipients, requests,
  cards, devices or preferences (owned by M4+).

### Key Entities

- **User**: a person with an account on the device. Identifier, full name, phone (+591, unique),
  email, verification status, new-user flag, created time, failed-PIN count, locked flag.
  Has one credential, many terms acceptances, many verification attempts, zero or one open session.
- **Credential**: the user's protected PIN representation and last change time. One per user.
- **Session**: the single open login. Belongs to one user; opened time; closed time.
- **Phone confirmation**: a code issued to a phone number. Phone, code, issue time, expiry,
  remaining tries, confirmed time. Linked to a user once registration completes.
- **Terms acceptance**: user, terms version, accepted time.
- **Verification attempt**: one try at identity verification. Belongs to one user; document type,
  document number, image references, extracted data, sworn declaration, state, rejection reason,
  times. A user has many attempts; at most one non-final.

**Sample data** (synthetic, fixed values; final values set in the plan):

| Sample | Purpose | Verification | Notes |
|--------|---------|--------------|-------|
| Empty device | Welcome / registration flow | — | No users (selectable) |
| Returning verified user | Login, main app for M4+ | verified | 1 approved attempt (CI) |
| Unverified user | KYC banner, start M3 | not verified | No attempts |
| User under review | "En revisión" state | under review | 1 submitted → under review attempt |
| Rejected user | Rejected result and retry | not verified | 1 rejected attempt with reason |
| Locked user | Locked login, PIN recovery | verified | 0 PIN attempts remaining |
| Newly registered user | Empty states in M4+ | not verified | new-user flag set |

## Contract

What M4+ may rely on (status **Draft**; must be **Approved** before another branch stacks on this
one — CLAUDE.md §15.3):

- **Current user**: identifier, display name, phone, email, verification status; or "no session".
- **Observation**: changes to the current user (login, logout, switch account) and to their
  verification status are observable.
- **Verification statuses**: exactly three — not verified, under review, verified. A rejection
  returns the user to "not verified" (no separate rejected status).
  Money operations are allowed only for "verified" (constitution reference: KYC gate).
- **User identifier**: stable; other milestones may store it as a reference in their own data.
- **Not in the contract**: credentials, PIN data, phone confirmations, terms records, verification
  attempt details and images.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of M2 and M3 screen states listed in `docs/references-views/ARCHITECTURE.md`
  §2.1 can be reached using only the sample data, without manual data entry.
- **SC-002**: After a reset, sample data is identical across 10 consecutive resets (same users,
  identifiers and values).
- **SC-003**: 0 cases in which a user's verification status and their latest final attempt
  disagree, across all automated scenarios.
- **SC-004**: 0 integrity violations accepted: attempts to create a duplicate phone, an orphan
  attempt, a second open session or a second non-final attempt are all refused.
- **SC-005**: The developer of M4 can implement the "verified only" gate using only the Contract
  section, with no questions about M2/M3 internals.
- **SC-006**: A registration with valid inputs and a sample code completes in under 2 minutes for
  a tester.
- **SC-007**: 0 PINs readable in stored data.

## Clarifications

### Session 2026-11-13

- Q: User status after a rejected verification? → A: Back to "not verified"; the reason stays on
  the attempt. Only three statuses (matches the reference documentation).
- Q: Handling of captured document/selfie images? → A: Never store real captures; store only a
  reference to a bundled synthetic placeholder.

## Assumptions

- Owner is krypton612; M4+ belong to the other developer, who will stack on this branch only once
  the Contract is approved (§15.3 hybrid rule).
- Maximum PIN attempts is 3 and the SMS code is valid for 5 minutes with 3 tries; reasonable defaults
  not given in the reference documentation.
- "PIN recovery" in this feature only needs the data to support it (unlock and set a new PIN after
  confirming the phone again); its screens belong to M2.
- Biometric login needs at most a per-user "biometrics enabled" preference; it is left out of this
  feature until the M2 specification requires it.
- One device may hold several users ("cambiar de cuenta" in the reference login screen).
- No data leaves the device; there is no network in this feature.
- Before the first release, schema changes reset local data (constitution).
- The mock exchange rate, balances and transactions in the reference documentation belong to M4+
  and are intentionally excluded.

## Dependencies

- Depends on: constitution v1.1.0 (SQLDelight as the local data source; CLAUDE.md §14).
- Blocks: M2 (access and registration), M3 (KYC), and — through the Contract — M4+.
- Needs approval: adding the local data store touches high-risk shared files (build configuration,
  version catalog) and platform data drivers for iOS and Desktop are Builder-owned (constitution
  Principle IV); both must be covered in the plan.
