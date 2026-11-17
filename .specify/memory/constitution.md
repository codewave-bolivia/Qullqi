<!--
Sync Impact Report
- Version change: 2.0.0 → 2.0.1 (PATCH: clarifies where unit tests live; no principle added or
  removed)
- Modified principles:
  - II. MVVM rationale: "unit-testable in commonTest" → "on the JVM (commonTest / androidHostTest)"
  - VII. Spec-Driven, Verified Delivery: ViewModel/domain tests may live in `androidHostTest` when
    they need a JVM-only driver (SQLite JDBC, Ktor MockEngine); still shared code + fakes only
- Added sections: none
- Removed sections: none
- Reason: /speckit-analyze on spec 004 (finding D1) — specs 002/003 already test in
  androidHostTest because SQLDelight has no commonTest driver; the rule now matches practice.
- Previous amendment (2.0.0, same branch, not yet integrated): Principle I backend-authoritative,
  Ktor approved for M2/M3 + GET /me.
- Follow-up TODOs: none (CLAUDE.md does not restate the test location rule).
-->

# Qullqi Constitution

## Core Principles

### I. Backend-Authoritative Data with Replaceable Sources

Qullqi is a client of the Qullqi backend (`BackendQullpi`). The backend is the authority; the app
only presents data and collects input.

- All data MUST be accessed through repository interfaces declared in `shared/commonMain`.
  ViewModels and UI MUST depend only on those interfaces or use cases, never on a concrete source.
- **Approved remote scope** (and nothing more without a new amendment):
  - M2 access and registration: `POST /auth/request-otp`, `/auth/verify-otp`, `/auth/register`,
    `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/pin/reset`.
  - M3 identity verification: `POST /my/kyc`, `GET /my/kyc`, and opening the Persona `hostedUrl`
    returned by the backend in the system browser or a Custom Tab.
  - M4 (partial): `GET /me` only, to feed `CurrentUserSource`. Any other M4 endpoint (balance,
    transactions, transfers, payments) belongs to the M4 owner and needs its own approval.
- The backend is the single source of truth for sessions, PIN attempts and lock, verification
  status, identity data and money. The app MUST NOT decide or override any of them.
- SQLDelight remains the local store for device-only data (accounts used on this device,
  preferences, biometrics flags, `deviceId`) and MAY cache backend responses. Synthetic seed data
  MAY only be used by tests and by an explicitly selected offline/demo build; release builds MUST
  NOT present seed data as real accounts.
- Remote and local implementations of the same repository interface MUST be interchangeable by
  wiring (`AppDependencies`) without changes to ViewModels or UI.
- Wire DTOs MUST stay in the data layer and be mapped to domain models there. Domain models MUST
  NOT mirror the backend wire format.
- Secrets: the app MUST NOT contain API keys, Persona keys or any privileged credential. Access and
  refresh tokens MUST be stored only in platform secure storage (Android Keystore-backed); never
  in SQLDelight, logs, plain preferences or crash reports. PINs and OTP codes MUST NOT be stored
  or logged.
- Transport: release builds MUST use HTTPS only. Cleartext HTTP is allowed only in debug builds
  for a local backend. The base URL MUST come from build configuration, not hardcoded in screens
  or ViewModels.
- Any network client, endpoint, external SDK, analytics or telemetry outside the approved scope
  above MUST NOT be added without explicit approval and an amendment.

Rationale: the backend now exists and must be authoritative for security and money. Keeping it
behind the same repository interfaces confines the change to the data layer, and an explicit
endpoint allow-list prevents M2/M3 work from reaching into M4.

### II. MVVM with Unidirectional State

The presentation architecture is MVVM.

- Each screen MUST have one ViewModel that exposes an immutable UI state through `StateFlow`.
- Views (Composables) MUST be stateless renderers of that state: they display state and forward
  user events to the ViewModel. Views MUST NOT call repositories, hold business rules or
  launch data operations.
- State changes MUST flow one way: user event → ViewModel → (use case / repository) →
  new state → view. One-off events (navigation, messages) MUST be modeled explicitly, not by
  mutating shared mutable state from the view.
- ViewModels MUST live in shared code and MUST NOT reference platform classes
  (`android.*`, `UIKit`, `java.awt`, `Context`, `Activity`).
- Business rules (validation, limits, verification gates) MUST live in the domain layer, not in
  ViewModels or views.

Rationale: shared, platform-free ViewModels let one presentation logic serve Android, iOS and
Desktop and keep it unit-testable on the JVM (`commonTest` / `androidHostTest`).

### III. Kotlin Multiplatform Portability

- New code MUST default to `shared/src/commonMain`. Platform source sets are used only when a
  capability cannot be expressed portably.
- Platform-specific behavior MUST be isolated behind `expect`/`actual` declarations or interfaces
  injected from the platform side.
- Android-only APIs and dependencies MUST NOT appear in `commonMain`. Dependencies added to
  `commonMain` MUST support all declared targets (android, jvm, iosArm64, iosSimulatorArm64).
- If a build fails because platform-specific code entered portable code, work MUST stop and the
  issue MUST be reported in the "PLATFORM PORTABILITY ISSUE" format defined in `CLAUDE.md`.
- An implementation MUST NOT be described as portable until it compiles for the shared targets.

Rationale: portability is the reason for using KMP; leaking platform APIs into shared code breaks
the other targets silently.

### IV. Platform Boundaries and Builder Ownership

- A separate Builder process owns iOS and Desktop outputs. Without explicit authorization, agents
  and developers working in this scope MUST NOT modify: `iosApp/` (Swift, Xcode project),
  `desktopApp/`, `shared/src/iosMain`, `shared/src/iosTest`, `shared/src/jvmMain`,
  `shared/src/jvmTest`, generated files or build outputs.
- Authorized scope: `shared/src/commonMain`, `shared/src/commonTest`, `shared/src/androidMain`,
  `shared/src/androidHostTest` and `androidApp/`.
- When a feature needs a change outside the authorized scope, the need MUST be documented in the
  plan and approved before implementation.

Rationale: two production paths writing the same platform files cause overwrites and
inconsistent builds.

### V. Collaborative Integrity (NON-NEGOTIABLE)

Two developers, possibly with different AI agents, work in this repository at the same time.

- Before starting a feature, the working branch MUST be synchronized with the integration branch
  using the Git protocol in `CLAUDE.md` §3 (fast-forward only where applicable).
- Uncommitted changes MUST NOT be discarded, reset, stashed or overwritten automatically.
- On any merge, rebase or sync conflict, all development MUST stop and the conflict MUST be
  reported to the user in the "GIT CONFLICT DETECTED" format. Conflicts MUST NOT be resolved
  automatically; `git reset --hard`, destructive checkouts and force pushes are forbidden.
- High-risk shared files (Gradle and version catalog, navigation, DI, theme and design system,
  shared models, repository interfaces) MUST NOT be modified without checking recent history and
  coordinating ownership.

Rationale: preventing loss of another developer's work outranks delivering any feature.

### VI. Responsive and Adaptive UI

- Every screen MUST work on phone, tablet and resizable desktop windows: layouts MUST adapt to
  available width and height, MUST NOT overflow horizontally and MUST remain usable when resized
  or rotated.
- Layout decisions MUST be based on available constraints (window size classes or measured
  constraints), not on the platform or a fixed device size.
- Fixed dimensions are allowed only for intrinsic elements (icons, touch targets, borders);
  containers and content widths MUST be adaptive.
- Both touch and mouse/keyboard interaction MUST be considered.
- Visual design MUST follow `docs/references-views/STYLEGUIDE.md` and `UI_GUIDELINES.md` through
  shared design tokens; colors, typography and spacing MUST NOT be hardcoded in screens.

Rationale: a screen that only looks right on an Android phone fails two of three targets.

### VII. Spec-Driven, Verified Delivery

- Every significant feature MUST follow the Spec Kit flow: specify → clarify → plan → (checklist)
  → tasks → analyze → implement. Implementation MUST NOT start from a conversational description
  alone.
- Each specification MUST name a human owner. Another owner's active specification MUST NOT be
  modified without coordination.
- Requirements and acceptance criteria MUST NOT be invented or changed during implementation;
  deviations from the plan MUST be documented and approved.
- ViewModels and domain logic MUST have unit tests, in `commonTest` or, when they need a JVM-only
  test driver (SQLite JDBC driver, Ktor `MockEngine` on JVM), in `androidHostTest`; such tests MUST
  use only shared code and fakes, no Android framework classes. Tests MUST NOT be disabled or
  weakened to make a build pass.
- A task MUST NOT be marked complete unless it is done and verified.

Rationale: two parallel contributors need a written, reviewable source of truth to avoid
divergent implementations.

## Technical Constraints

- Language and platform: Kotlin Multiplatform with Compose Multiplatform. Modules: `:shared`
  (KMP library: android, jvm, iosArm64, iosSimulatorArm64), `:androidApp`, `:desktopApp`;
  `iosApp/` is the Xcode host.
- Build: Gradle Kotlin DSL with the version catalog `gradle/libs.versions.toml`. Versions are
  declared only in the catalog. New dependencies require justification and approval, and MUST be
  multiplatform-compatible when added to `commonMain`.
- Concurrency and state: Kotlin Coroutines and Flow (`StateFlow` for UI state). ViewModels use
  the multiplatform `lifecycle-viewmodel` already in the catalog.
- Layering inside `shared/src/commonMain/kotlin/com/codewave/qullqi/`:
  - `domain/`: models, use cases, business rules; no framework or UI dependencies.
  - `data/`: repository implementations: remote (backend) and local (SQLDelight).
  - `presentation/`: ViewModels, UI state and UI events.
  - `ui/` or `designsystem/`: Composables, theme and design tokens.
  Dependencies point inward: `ui → presentation → domain ← data`.
- Remote access: **Ktor Client 3.x** (approved 2026-11-17) with `kotlinx-serialization-json`
  for JSON. `ktor-client-core`, `ktor-client-content-negotiation` and
  `ktor-serialization-kotlinx-json` in `commonMain`; engines per platform: OkHttp in
  `androidMain`, Darwin in `iosMain` and Java/CIO in `jvmMain` (the last two fall under
  Principle IV). Exact versions are pinned in the version catalog. Requests use the backend
  envelope (`{success, message, status, data}` / `{error:{code,message,details}}`); error codes
  are mapped to domain results in the data layer. Timeouts are explicit; no automatic retry of
  non-idempotent calls (`register`, `pin/reset`, `POST /my/kyc`).
- Secure token storage: an interface in `commonMain` with an Android implementation backed by
  the Android Keystore; iOS/Desktop implementations fall under Principle IV.
- Local persistence: SQLDelight, following `CLAUDE.md` §14.
  - Schema, queries, seed data and migrations in
    `shared/src/commonMain/sqldelight/com/codewave/qullqi/db/`.
  - Relationships declared with `FOREIGN KEY` and explicit `ON DELETE`; foreign keys enabled on
    every connection; many-to-many through join tables.
  - Money stored as integer minor units plus currency code; no floating-point amounts.
  - One `SqlDriver` per platform. The Android driver is in scope; iOS (`iosMain`) and Desktop
    (`jvmMain`) drivers fall under Principle IV and require the Builder or explicit approval.
  - Before first release, schema changes may reset the local database; afterwards every change
    MUST ship a verified `.sqm` migration.
  - Data-layer tests use an in-memory database with the same schema and seed.
- Dependency injection: constructor injection is mandatory. Adopting a DI library is an
  architectural decision that requires approval and an amendment to this section.
- Data: synthetic fixtures only. No real personal or financial data, secrets or tokens in
  source, tests, screenshots or documentation. Remote calls in tests use fakes (Ktor
  `MockEngine`), never a live backend.
- Reference documentation: `docs/references-views/` (architecture, style guide, UI guidelines,
  reference views) describes intended screens and flows; where it conflicts with this
  constitution, the constitution prevails until the document is reconciled.

## Development Workflow and Quality Gates

- Branches: one feature branch per specification, created by Spec Kit. The integration branch is
  confirmed from repository configuration before synchronizing; it is not assumed.
- Before implementation: Git synchronized (Principle V), constitution read, specification
  approved, plan passes the Constitution Check, tasks analyzed.
- Every plan's Constitution Check MUST explicitly state:
  - Which code is portable (`commonMain`) and which is Android-specific (`androidMain`,
    `androidApp`).
  - That no task modifies Builder-owned paths, or which approval permits it.
  - The repository interfaces involved, the SQLDelight tables, relations and seed data they
    require, and whether a migration is needed.
  - The backend endpoints used, confirming each is inside Principle I's approved scope, and how
    their errors map to domain results.
  - The responsive strategy for each new screen.
- Gates before a feature is considered done:
  1. `./gradlew :shared:allTests` (or the subset runnable on the host) passes.
  2. Shared code compiles for the authorized targets, and `./gradlew :androidApp:assembleDebug`
     succeeds when Android is affected.
  3. Responsive behavior is verified at compact and expanded widths.
  4. The final diff contains no unrelated or Builder-owned files.
  5. The development report required by `CLAUDE.md` §11 is produced.
- Work MUST NOT be reported as committed, pushed or merged unless it actually was.

## Governance

- This constitution overrides individual preferences and any conflicting guidance. `CLAUDE.md`
  is the operational runtime guide and MUST stay consistent with it; when they conflict, this
  constitution prevails and `CLAUDE.md` MUST be corrected.
- Priority when principles conflict: (1) prevent loss of another developer's work,
  (2) repository integrity, (3) platform boundaries and Builder ownership, (4) approved
  specifications and architecture, (5) KMP portability, (6) responsive behavior,
  (7) existing functionality, (8) the requested feature, (9) optimization and refactoring.
- Amendments: proposed through `/speckit-constitution` on a dedicated branch, approved by both
  developers, and recorded with a Sync Impact Report. Amendments that change architecture or
  boundaries MUST include a migration note for affected specifications.
- Versioning: semantic. MAJOR for removing or redefining a principle; MINOR for adding a principle
  or section or materially expanding guidance; PATCH for clarifications and wording.
- Compliance: every plan includes a Constitution Check; every review verifies Principles III–V
  and the quality gates. Unjustified complexity or new dependencies are grounds for rejection.

**Version**: 2.0.1 | **Ratified**: 2026-11-12 | **Last Amended**: 2026-11-17
