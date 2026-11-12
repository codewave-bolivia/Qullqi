<!--
Sync Impact Report
- Version change: template (unversioned) → 1.0.0
- Modified principles: all template placeholders replaced (initial ratification)
  - [PRINCIPLE_1_NAME] → I. Frontend-Only with Replaceable Data Sources
  - [PRINCIPLE_2_NAME] → II. MVVM with Unidirectional State
  - [PRINCIPLE_3_NAME] → III. Kotlin Multiplatform Portability
  - [PRINCIPLE_4_NAME] → IV. Platform Boundaries and Builder Ownership
  - [PRINCIPLE_5_NAME] → V. Collaborative Integrity (NON-NEGOTIABLE)
- Added principles: VI. Responsive and Adaptive UI; VII. Spec-Driven, Verified Delivery
- Added sections: Technical Constraints; Development Workflow and Quality Gates
- Removed sections: none
- Templates requiring updates: none modified by this command
  - .specify/templates/plan-template.md: ⚠ review at next /speckit-plan that its
    "Constitution Check" gates map to principles I–VII
  - .specify/templates/tasks-template.md: ⚠ review that tasks never target iosApp/,
    desktopApp/, iosMain or jvmMain without approval
- Follow-up TODOs:
  - docs/references-views/ARCHITECTURE.md describes state as "UDF / MVI" and lists Koin,
    DataStore and SwiftUI screens; this constitution adopts MVVM with unidirectional state
    and does not mandate a DI library. Reconcile the document in a later approved change.
-->

# Qullqi Constitution

## Core Principles

### I. Frontend-Only with Replaceable Data Sources

Qullqi is a frontend application. No backend exists yet; one will provide the data in the future.

- All data MUST be accessed through repository interfaces declared in `shared/commonMain`.
- Until a backend exists, repositories MUST be backed by local mock or in-memory data sources
  containing synthetic data only. Real personal, financial or account data MUST NOT be committed.
- ViewModels and UI MUST depend only on repository interfaces or use cases, never on a concrete
  data source, so that a backend-backed implementation can replace the mock without changing
  presentation code.
- Data models used by the UI MUST NOT mirror an assumed backend wire format. When an API contract
  is provided, mapping happens inside the data layer.
- The frontend MUST NOT contain secrets, privileged keys or credentials, and MUST NOT be treated
  as the authority for authorization, balances or transaction validity.
- No network client, external API, analytics or telemetry may be added without explicit approval.

Rationale: the backend is unknown. Isolating data access behind interfaces lets it be plugged in
later with a change confined to the data layer.

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
Desktop and keep it unit-testable in `commonTest`.

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
- ViewModels and domain logic MUST have unit tests in `commonTest`. Tests MUST NOT be disabled or
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
  - `data/`: repository implementations and data sources (mock now, backend later).
  - `presentation/`: ViewModels, UI state and UI events.
  - `ui/` or `designsystem/`: Composables, theme and design tokens.
  Dependencies point inward: `ui → presentation → domain ← data`.
- Dependency injection: constructor injection is mandatory. Adopting a DI library is an
  architectural decision that requires approval and an amendment to this section.
- Data: synthetic fixtures only. No real personal or financial data, secrets or production
  endpoints in source, tests, screenshots or documentation.
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
  - The repository interfaces involved and their mock data sources.
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

**Version**: 1.0.0 | **Ratified**: 2026-11-12 | **Last Amended**: 2026-11-12
