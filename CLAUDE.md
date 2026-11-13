# CLAUDE.md — Engineering & Development Protocol

## 1. Project Overview

This project is a Kotlin Multiplatform (KMP) application.

Development is performed by two human developers, potentially using different AI coding agents simultaneously.

The repository must maintain strict development boundaries to prevent code conflicts, accidental overwrites, architectural inconsistencies and platform-specific incompatibilities.

The application targets multiple platforms:

- Android
- iOS
- Desktop

**Important:** This development environment is responsible for Kotlin application development. A separate Builder process is responsible for generating and maintaining portable platform versions and platform-specific build outputs.

Claude must respect this separation at all times.

---

## 2. Repository Structure and File Ownership

Before modifying any code, inspect the complete repository structure.

Document the actual directory tree below. Do not invent directories, files or modules.

Update this section whenever the project structure changes through an approved architectural modification.

### Repository Directory Tree

<!-- Maintain this tree using the actual repository structure. Last verified: 2026-11-13 (spec 003). -->

Package root for all Kotlin code: `com.codewave.qullqi`.

```
Qullqi/
├── build.gradle.kts                  # Root Gradle (plugin aliases only)         [shared/high-risk]
├── settings.gradle.kts               # Modules: :androidApp :desktopApp :shared   [shared/high-risk]
├── gradle.properties                                                              [shared/high-risk]
├── gradle/
│   ├── libs.versions.toml            # Version catalog                            [shared/high-risk]
│   ├── gradle-daemon-jvm.properties  # Daemon JVM criteria (Azul 21)              [shared/high-risk]
│   └── wrapper/                      # Gradle 9.5.1 wrapper                       [shared/high-risk]
├── shared/                           # KMP library module (main development area)
│   ├── build.gradle.kts              # Targets: android, jvm, iosArm64, iosSimulatorArm64
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/codewave/qullqi/
│       │   │   ├── App.kt            # App(deps?) → QullqiRoot, or "no disponible" without deps
│       │   │   ├── AppDependencies.kt · QullqiRoot.kt   # wiring; single-window host for iOS/Desktop
│       │   │   ├── designsystem/     # Nocturne tokens, theme, type, motion, veil, components/  [shared]
│       │   │   ├── navigation/       # FlowNavigator, FlowHost (per-activity back stacks)       [shared]
│       │   │   ├── feature/auth/     # AuthFlow (AuthActivity): start/, login/, register/, recover/, KYC slot
│       │   │   ├── feature/main/     # MainFlow (MainActivity): M4 content slot, UnlockScreen, placeholder
│       │   │   ├── Greeting.kt
│       │   │   ├── GreetingUtil.kt
│       │   │   ├── Platform.kt       # expect declarations
│       │   │   ├── domain/
│       │   │   │   ├── model/        # UserId, VerificationStatus, CurrentUser, KYC types   [shared]
│       │   │   │   ├── repository/   # CurrentUserSource (M4+ contract), Session/Registration/Kyc  [shared]
│       │   │   │   ├── security/     # PinHasher, BiometricAuthenticator interfaces
│       │   │   │   ├── session/      # AppLock (5-minute re-authentication)
│       │   │   │   └── system/       # Clock, IdGenerator, OtpCodeGenerator
│       │   │   └── data/             # Sql*Repository, MockKycProcessor, LocalDataReset; db/DatabaseFactory
│       │   ├── sqldelight/com/codewave/qullqi/db/   # User, PhoneConfirmation, KycAttempt, AccountPreference, Seed .sq  [shared]
│       │   └── composeResources/   # font/ (Inter 400/500, OFL), drawable/ph_*.xml (Phosphor, MIT)
│       ├── commonTest/kotlin/com/codewave/qullqi/SharedCommonTest.kt
│       ├── androidMain/kotlin/com/codewave/qullqi/
│       │   ├── Platform.android.kt
│       │   └── data/                 # AndroidDatabaseFactory, Pbkdf2PinHasher
│       ├── androidHostTest/kotlin/com/codewave/qullqi/
│       │   ├── SharedLogicAndroidHostTest.kt
│       │   ├── data/                 # data-layer tests (in-memory SQLite)
│       │   └── feature/              # ViewModel + AppLock tests (coroutines-test)
│       ├── jvmMain/kotlin/com/codewave/qullqi/Platform.jvm.kt          # Desktop actuals   [Desktop — do not modify]
│       ├── jvmTest/kotlin/com/codewave/qullqi/SharedLogicDesktopTest.kt                     [Desktop — do not modify]
│       ├── iosMain/kotlin/com/codewave/qullqi/
│       │   ├── MainViewController.kt                                                     [iOS — do not modify]
│       │   └── Platform.ios.kt                                                           [iOS — do not modify]
│       └── iosTest/kotlin/com/codewave/qullqi/SharedLogicIOSTest.kt                      [iOS — do not modify]
├── androidApp/                       # Android application entry point
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/codewave/qullqi/
│       │   ├── QullqiApplication.kt  # builds AppDependencies once per process
│       │   ├── AuthActivity.kt       # launcher: system launch screen + AuthFlow
│       │   ├── MainActivity.kt       # MainFlow (M4) + AppLock on onStop/onStart
│       │   └── AndroidBiometricAuthenticator.kt
│       └── res/                      # ic_qullqi + adaptive icon, values/themes.xml (Theme.Qullqi[.Starting]), colors.xml
├── desktopApp/                       # Desktop (JVM) entry point                  [Desktop — do not modify]
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/codewave/qullqi/main.kt
├── iosApp/                           # Xcode project, Swift                       [iOS — do not modify]
│   ├── Configuration/Config.xcconfig
│   ├── iosApp/                       # iOSApp.swift, ContentView.swift, Info.plist, Assets.xcassets
│   └── iosApp.xcodeproj/
├── docs/licenses/                    # Inter-OFL.txt, Phosphor-MIT.txt (bundled assets)
├── docs/references-views/            # UI reference designs and guidelines (read-only reference)
│   ├── ARCHITECTURE.md
│   ├── STYLEGUIDE.md
│   ├── UI_GUIDELINES.md
│   ├── Qullqi Pay.dc.html
│   ├── Qullqi Pay Auth.dc.html
│   └── _ds/                          # Design system bundle
├── specs/                            # Spec Kit feature specs: NNN-short-name/{spec,plan,tasks}.md
├── scripts/
│   ├── branches.sh                   # Branch/spec view and integration checks (§15)
│   └── test-branches.sh              # Self-check for branches.sh and the pre-commit hook
├── .githooks/pre-commit              # Refuses commits on develop/master (§15)
├── .github/workflows/                # CI (pr-check.yml) and CD (develop-release.yml), §16
├── .specify/                         # Spec Kit: constitution, templates, scripts, workflows
└── .claude/skills/                   # Spec Kit agent skills
```

Notes:
- No feature modules, navigation, DI or test directories beyond the template defaults exist yet.
- No Builder-managed directories exist in the repository yet. Build outputs (`**/build/`, `.gradle`, `.kotlin`) are gitignored.
- Tests: `commonTest` (portable), `androidHostTest` (Android JVM host), `jvmTest` and `iosTest` (platform-specific).

### Repository Areas

Every path belongs to exactly one class. Precedence: **protected > shared > authorized** (e.g.
`desktopApp/build.gradle.kts` is protected). `scripts/branches.sh check` applies the same table.

| Class | Paths | Required action |
|-------|-------|-----------------|
| **protected** | `iosApp/**`, `desktopApp/**`, `shared/src/iosMain/**`, `shared/src/iosTest/**`, `shared/src/jvmMain/**`, `shared/src/jvmTest/**`, `**/build/**` | Builder involvement or written approval recorded in the feature plan |
| **shared** (high-risk) | `*.gradle.kts`, `gradle/**`, `gradle.properties`, `CLAUDE.md`, `.specify/**`, `.claude/**`, `scripts/**`, `.githooks/**`, `.github/**`, `**/sqldelight/**`, `**/designsystem/**`, `**/navigation/**`, `**/domain/model/**` (shared models), `**/domain/repository/**` (repository interfaces) | Check recent history and coordinate before editing; the other developer approves the integration (§15.4) |
| **authorized** | everything else, notably `shared/src/commonMain/**`, `shared/src/commonTest/**`, `shared/src/androidMain/**`, `shared/src/androidHostTest/**`, `androidApp/src/**`, `specs/**`, `docs/**` | None beyond the normal workflow |

Layers inside `shared/src/commonMain/kotlin/com/codewave/qullqi/` follow the constitution:
`ui → presentation → domain ← data` (dependencies point inward; `domain` depends on nothing).

### File Ownership Rules

Claude must:

1. Identify the correct Kotlin source directory before modifying code.
2. Modify only files belonging to the assigned development scope.
3. Never modify iOS implementation files.
4. Never modify Desktop implementation files.
5. Never modify generated or Builder-managed files.
6. Never modify build outputs or generated portable artifacts.
7. Never move or reorganize directories without explicit authorization.
8. Never create duplicate implementations of existing components.

When uncertain about file ownership, stop and ask for clarification.

**The Builder is responsible for producing portable versions for iOS and Desktop. Claude must not take over this responsibility.**

---

## 3. Mandatory Git Synchronization Protocol

This is a critical requirement.

Two developers work simultaneously in the same repository. Therefore, synchronization is mandatory before starting any new feature or independent development task.

### Before starting a feature

Claude MUST execute the following procedure:

1. Inspect the current Git branch.
2. Execute `git status`.
3. Identify uncommitted changes.
4. If uncommitted changes exist, determine whether they belong to the current developer.
5. Never discard, overwrite, reset or automatically stash existing changes.
6. Fetch the latest remote changes.
7. Synchronize with the appropriate integration branch using the project's established Git workflow.
8. Verify that the working tree is clean and synchronized.
9. Only then proceed with feature development.

Use fast-forward-only synchronization where applicable.

Do not assume that `main`, `master` or `develop` is the integration branch. Inspect the repository configuration and existing workflow.

### Conflict handling — mandatory stop condition

If Git reports any merge, rebase or synchronization conflict:

**STOP ALL DEVELOPMENT IMMEDIATELY.**

Claude MUST NOT:

- Automatically resolve conflicts.
- Choose one developer's implementation over another.
- Delete conflicting changes.
- Execute `git reset --hard`.
- Execute destructive checkout commands.
- Force push.
- Continue implementing the feature.
- Modify conflicting files in an attempt to make the build pass.

Instead, report the situation to the user.

Use this format:

**GIT CONFLICT DETECTED**

- Current branch:
- Target branch:
- Conflicting files:
- Nature of the conflict:
- Potential impact:
- Recommended action:

Explain briefly what happened and recommend that the user coordinate directly with the other developer before proceeding.

Example:

"Synchronization failed because `WalletScreen.kt` contains conflicting modifications from another developer. Both branches appear to modify the wallet interface. I have stopped development to prevent overwriting existing work. Please coordinate with the other developer and determine which implementation should be preserved."

Do not continue until the conflict has been resolved and the user authorizes proceeding.

---

## 4. Feature-Based Development

All significant development must be organized around features.

Before implementation:

1. Understand the requested functionality.
2. Identify the relevant specification.
3. Identify affected Kotlin files.
4. Identify dependencies and shared components.
5. Determine whether another feature could be modifying the same files.
6. Define the implementation scope.
7. Verify Git synchronization.
8. Proceed with implementation.

### Feature isolation

- Do not modify unrelated features.
- Avoid touching shared files unless necessary.
- Do not introduce unnecessary refactoring.
- Keep changes focused.
- Do not silently change existing behavior.
- Do not expand the feature scope without authorization.

### Feature completion

A feature is complete only when:

- Requirements are satisfied.
- Existing architecture is respected.
- Relevant tests pass.
- The application builds successfully for the authorized target.
- No unrelated files were modified.
- Known limitations are documented.

---

## 5. Kotlin Multiplatform Development Rules

The application uses Kotlin Multiplatform.

Claude must distinguish between portable Kotlin code and platform-specific implementations.

### Portable implementation

Prefer:

- Kotlin standard library.
- Kotlin Coroutines.
- Kotlin Flow.
- Shared business logic.
- Shared UI components where supported.
- Platform-independent abstractions.
- Dependency injection compatible with the intended targets.

Do not introduce Android-only dependencies into shared code.

### Android-specific code

Android-native APIs may only be used in explicitly authorized Android-specific source sets.

Before using an Android-native class, verify whether an equivalent portable implementation exists.

If an Android-native class is required:

1. Identify the dependency.
2. Determine whether it is compatible with the shared architecture.
3. Check whether a portable alternative exists.
4. Use platform-specific implementations only when permitted by the project architecture.
5. Document the platform limitation.

### Portability failure

If a build or compilation fails because an Android-native class has been introduced into code that must be portable:

STOP and explain the issue.

Use this format:

**PLATFORM PORTABILITY ISSUE**

- Problematic class:
- File location:
- Why it fails:
- Platform dependency:
- Portable alternatives:
- Recommended solution:

Example:

"The build fails because `android.content.Context` is an Android-native class and cannot be resolved in a platform-independent Kotlin source set. Consider using a portable abstraction, dependency injection, or a platform-specific implementation through an expected interface."

Do not modify iOS or Desktop implementations to work around the issue.

Do not claim that an implementation is portable unless its compatibility has been verified.

---

## 6. Platform Boundaries

The Builder owns portable platform generation.

Claude's responsibility is to maintain the authorized Kotlin implementation.

### Forbidden modifications

Unless explicitly authorized, Claude must not:

- Modify Swift or Objective-C files.
- Modify Xcode project configuration.
- Modify iOS-specific application code.
- Modify Desktop-specific application code.
- Modify generated platform artifacts.
- Modify Builder-managed outputs.
- Introduce platform-specific workarounds into generated files.

If a portable implementation requires changes outside the authorized Kotlin scope, explain the requirement and request approval.

---

## 7. Responsive UI Requirements

The application must provide a consistent experience across mobile and desktop.

Responsive design is mandatory.

All UI implementations must consider:

- Different screen widths.
- Different screen heights.
- Window resizing.
- Orientation changes.
- Dynamic content.
- Scrollable layouts.
- Touch interactions.
- Mouse and keyboard interactions where applicable.
- Proper spacing and typography.
- Adaptive component sizing.
- Navigation appropriate to available screen space.

### Responsive principles

- Avoid hardcoded dimensions when adaptive sizing is appropriate.
- Avoid assuming the device is always a phone.
- Avoid layouts that break when the application window is resized.
- Use adaptive layouts based on available constraints.
- Keep content readable and accessible.
- Prevent horizontal overflow.
- Ensure dialogs and navigation remain usable on smaller and larger screens.

A feature must not be considered complete simply because it looks correct on an Android phone.

Responsive behavior must be considered during implementation, not added as an afterthought.

---

## 8. Architecture and Coding Standards

- Respect the existing architecture.
- Maintain separation of concerns.
- Avoid unnecessary abstractions.
- Reuse existing components.
- Keep business logic separate from presentation.
- Avoid duplicated functionality.
- Use descriptive naming.
- Maintain consistent Kotlin conventions.
- Handle errors explicitly.
- Avoid magic values.
- Keep functions and classes focused.
- Avoid unnecessary dependencies.

Do not redesign the architecture without explicit approval.

---

## 9. Shared Files and Developer Coordination

The following types of files are considered high-risk shared resources:

- Application configuration.
- Navigation configuration.
- Dependency injection.
- Shared theme and design system.
- Shared models.
- API contracts.
- Database definitions.
- Build configuration.
- Gradle configuration.
- Shared utilities.

Before modifying these files:

1. Inspect the current implementation.
2. Check Git history and current changes.
3. Determine whether another developer could be working on them.
4. Explain why the modification is necessary.
5. Request coordination when ownership is unclear.

Never assume that two independent feature branches can safely modify the same shared file.

---

## 10. Testing and Build Validation

Before declaring work complete:

- Execute relevant unit tests.
- Execute relevant integration tests when applicable.
- Run the appropriate Kotlin compilation task.
- Validate the Android build when relevant.
- Check for regressions.
- Verify responsive behavior.
- Review the final Git diff.

Do not modify unrelated files to fix failing tests.

Do not disable tests to bypass failures.

If a build fails because of an existing issue unrelated to the current feature, document the failure separately.

---

## 11. Final Development Report

At the end of every implementation task, provide:

### Summary
What was implemented.

### Modified files
List every modified file and explain why it was changed.

### Tests
List the tests and build commands executed, including their actual results.

### Portability
Explain whether the implementation respects the Kotlin Multiplatform boundaries.

### Responsive behavior
Explain how mobile and desktop layouts were considered.

### Remaining issues
List known limitations, unresolved dependencies or coordination requirements.

### Git status
Report the current branch and working tree status.

Never claim that work has been committed, pushed or merged unless those actions were actually performed.

---

## 12. Absolute Development Priorities

When requirements conflict, follow this priority:

1. Prevent loss of another developer's work.
2. Preserve repository integrity.
3. Respect platform boundaries and Builder ownership.
4. Respect approved specifications and architecture.
5. Maintain Kotlin Multiplatform portability.
6. Maintain responsive behavior.
7. Preserve existing functionality.
8. Implement the requested feature.
9. Optimize and refactor only when justified.

**Final rule: When uncertain, stop, explain the issue and ask. Never make destructive assumptions or silently modify another developer's work.**

---

## 13. GitHub Spec Kit — Mandatory Development Workflow

This project uses **GitHub Spec Kit** as its official Specification-Driven Development (SDD) framework.

Spec Kit is the source of truth for feature requirements, technical planning, implementation tasks and development traceability.

Claude MUST use the established Spec Kit workflow for all significant features.

### 13.1 Constitution

The project constitution is located at:

`.specify/memory/constitution.md`

The constitution defines the engineering principles and mandatory constraints governing the entire project.

Claude MUST:

- Read and respect the constitution before planning or implementing features.
- Ensure that specifications and implementation plans comply with constitutional principles.
- Never silently bypass constitutional requirements.
- Request authorization when a requirement appears to contradict the constitution.
- Propose constitutional amendments when existing principles need to change.

The constitution takes precedence over individual implementation preferences.

### 13.2 Mandatory Spec Kit Commands

Use the official Spec Kit commands available in the repository.

The standard workflow is:

1. `/speckit.specify` — Define feature requirements and expected behavior.
2. `/speckit.clarify` — Resolve ambiguities and missing requirements.
3. `/speckit.plan` — Define the technical implementation plan.
4. `/speckit.checklist` — Generate applicable quality and requirement checklists.
5. `/speckit.tasks` — Break implementation into actionable tasks.
6. `/speckit.analyze` — Analyze consistency between specifications, plans and tasks.
7. `/speckit.implement` — Execute the approved implementation tasks.

Use `/speckit.constitution` only when creating or formally updating the project constitution.

Do not assume every command is installed or available. Inspect the repository's actual Spec Kit configuration and available commands before invoking them.

> **Installed in this repository** (as Claude Code skills under `.claude/skills/`, verified 2026-11-12): commands use a hyphen instead of a dot —
> `/speckit-specify`, `/speckit-clarify`, `/speckit-plan`, `/speckit-checklist`, `/speckit-tasks`, `/speckit-analyze`, `/speckit-implement`, `/speckit-constitution`, plus `/speckit-converge` (appends unbuilt work as new tasks after comparing code against spec/plan/tasks).
> Helper scripts: `.specify/scripts/bash/`. Feature specs are created under `specs/` (not yet present).

### 13.3 Feature Development Rules

Before implementing a significant feature:

- Ensure Git synchronization has been completed.
- Read the project constitution.
- Inspect the existing feature specifications.
- Identify related specifications and dependencies.
- Create or update the feature specification.
- Resolve relevant ambiguities.
- Generate the implementation plan.
- Generate implementation tasks.
- Analyze consistency.
- Obtain human approval before implementation when required.

Never start implementation based solely on a conversational description when a formal specification is required.

### 13.4 Specification Ownership

Every feature specification must have an identifiable human owner.

Developers must coordinate changes to specifications that affect shared modules, APIs, navigation, shared models or database structures.

Claude must not modify another developer's active specification without coordination.

Every `specs/NNN-short-name/spec.md` header (before the first `##`) MUST contain these lines, which
`scripts/branches.sh` parses (format: `specs/001-repo-governance-branching/contracts/spec-metadata.md`):

```markdown
**Feature Branch**: `feature/NNN-short-name`
**Owner**: <person>
**Status**: Draft | In Progress | Ready | Integrated | Abandoned
**Depends on**: none | NNN-short-name stacked|waiting [(integrated)], ...
**Contract**: none | Draft | Approved (YYYY-MM-DD, <approver>)
```

### 13.5 Specification and Implementation Boundaries

The specification defines WHAT the application must do.

The implementation plan defines HOW the feature should be implemented.

The tasks define WHICH development activities must be completed.

Claude must not:

- Invent requirements during implementation.
- Change acceptance criteria without authorization.
- Ignore approved technical constraints.
- Implement tasks outside the assigned feature.
- Mark incomplete tasks as completed.
- Modify unrelated specifications.

### 13.6 Kotlin Multiplatform Constraints

All Spec Kit implementation plans must explicitly consider the project's platform boundaries.

Plans must distinguish between:

- Portable Kotlin implementation.
- Android-specific implementation.
- Builder-managed iOS output.
- Builder-managed Desktop output.

Do not generate implementation tasks that require Claude to modify protected iOS or Desktop files.

If a proposed solution depends on Android-native APIs in portable Kotlin code, identify the portability issue during planning and propose alternatives before implementation.

### 13.7 Definition of Done

A feature cannot be considered complete until:

- Its specification is satisfied.
- Its implementation plan has been followed or approved deviations documented.
- All required tasks are completed.
- Acceptance criteria are verified.
- Relevant tests pass.
- Kotlin portability constraints are respected.
- Responsive requirements are validated.
- Documentation is updated where necessary.
- The final implementation is ready for human review.

Spec Kit is mandatory for feature development, not an optional documentation tool.

---

## 14. Local Data Source — SQLDelight

The application is frontend-only. Until a backend exists, **SQLDelight is the single local data
source**. The SQLDelight schema is where mock data and the relationships between tables are
defined. Mock data MUST NOT be scattered across ViewModels, Composables or hardcoded Kotlin lists.

> Status (2026-11-13): SQLDelight 2.4.1 installed by spec 002 (`specs/002-local-data-layer/`),
> database `QullqiDatabase`. Only the Android `SqlDriver` and `PinHasher` exist
> (`AndroidDatabaseFactory`, `Pbkdf2PinHasher`); the iOS and Desktop implementations are pending
> from the Builder. They are injected, not `expect`ed, so their absence does not break compilation.
>
> **Sample credentials** (synthetic seed): PIN `1234` for every sample user, SMS code `123456`,
> KYC outcome by the last digit of the document number: `0` → rejected, `5` → under review,
> anything else → approved. `LocalDataReset.reset(withSamples = false)` gives an empty device; in
> debug builds, long-press the splash wordmark to reset data with or without samples.
>
> Spec 003 added `account_preference` (biometrics enabled/prompted, last login). From spec 003 on,
> installed builds create the database: any further schema change needs a `.sqm` migration or an
> explicit pre-release reset note (§14.6).

### 14.1 Location

- Schema files (`.sq`) live in `shared/src/commonMain/sqldelight/com/codewave/qullqi/db/`, one
  file per table or aggregate (for example `Account.sq`, `Transaction.sq`).
- Migrations (`.sqm`) live in the same directory, named `<version>.sqm`.
- The generated database class and the `SqlDriver` factory are the only database-specific entry
  points. Generated code under `build/` MUST NOT be edited or committed.

### 14.2 Schema and relationships

- Every table MUST declare a primary key.
- Relationships between tables MUST be declared with `FOREIGN KEY ... REFERENCES` and an
  explicit `ON DELETE` policy. Relations MUST NOT be implied only by naming conventions.
- Many-to-many relations MUST use an explicit join table.
- Foreign-key enforcement MUST be turned on for every connection (`PRAGMA foreign_keys = ON`);
  SQLite disables it by default.
- Amounts MUST be stored as integers in minor units (e.g. cents) together with a currency code;
  floating-point amounts are forbidden.
- Dates MUST be stored as ISO-8601 text or epoch milliseconds, consistently across all tables.
- Indexes are added for columns used in joins or frequent filters.
- Queries MUST be named, typed SQLDelight queries. No raw SQL strings built in Kotlin.

### 14.3 Mock data

- Mock (seed) data MUST be defined as `INSERT` statements in the `.sq` files (or a dedicated
  `Seed.sq`), so the seed is created together with the schema and respects all foreign keys.
- Seed data MUST be synthetic: fictitious names, numbers and balances. No real personal,
  financial or account data.
- Seed data MUST be deterministic: fixed IDs and values, so tests and screenshots are
  reproducible.
- Seed data MUST cover the states the UI needs (e.g. empty lists, verified and unverified users,
  pending and failed transactions) as defined in each feature specification.
- Changes to seed data that other features depend on are shared-file changes (§9) and require
  coordination.

### 14.4 Architecture boundaries

- SQLDelight belongs exclusively to the data layer (`data/`). Repository implementations map
  generated SQLDelight types to domain models. ViewModels and UI MUST NOT import generated
  SQLDelight classes.
- Repository interfaces are the contract. When a backend is provided, it is added behind the same
  interfaces (as a remote source, with SQLDelight as cache or replaced), without changes to
  ViewModels or UI.
- Reactive reads use SQLDelight's coroutines extensions (`asFlow()` / `mapToList`) and are
  exposed as `Flow` from repositories.
- Data-layer tests run against an in-memory database with the same schema and seed.

### 14.5 Platform drivers

- SQLDelight needs one `SqlDriver` per platform: Android (`androidMain`), iOS (`iosMain`) and
  Desktop (`jvmMain`).
- `iosMain` and `jvmMain` are protected (§2, §6). The Android driver may be implemented in this
  scope; the iOS and Desktop drivers MUST be requested from the Builder or explicitly authorized,
  and the plan MUST state this.
- Schema, queries, seed data and migrations live in `commonMain` and MUST stay platform-agnostic.

### 14.6 Schema changes

- Before the first release, schema changes may edit `.sq` files directly and reset the local
  database; this MUST be stated in the specification.
- After a release (or once real user data exists), every schema change MUST include a `.sqm`
  migration, and the migration MUST be verified (`verifySqlDelightMigration`).
- Schema files are high-risk shared resources (§9): check history and coordinate before editing.

---

## 15. Branching and Dependent Branches

Defined by `specs/001-repo-governance-branching/`. Tooling: `scripts/branches.sh` (read-only) and
`.githooks/pre-commit`.

### 15.1 Branches and naming

- `develop` is the **integration branch**; `master` is the **stable branch** and only receives manual
  promotions from `develop` (§15.5).
- No direct commits on `develop` or `master`. All work happens on short-lived work branches.
- Work branch name: `<type>/<NNN>-<short-name>`, `type` ∈ `feature | fix | docs | chore | hotfix`,
  `NNN` = the spec number. Regex: `^(feature|fix|docs|chore|hotfix)/[0-9]{3}-[a-z0-9-]+$`.
- Each significant branch has exactly one spec with a named owner (§13.4 header).
- `scripts/branches.sh list` is the single view of specs, branches, owners, status and
  dependencies. There is no hand-maintained registry file.

### 15.2 Start a branch

One-time per clone: `git config core.hooksPath .githooks`.

1. `git status`. Uncommitted changes → stop and ask whose they are; never discard, reset or stash
   them automatically.
2. If a remote exists: `git fetch`. If none is configured, report "no remote: fetch not applicable".
3. `git switch develop && git merge --ff-only <remote>/develop` (when a remote exists).
4. Independent feature: `git switch -c feature/NNN-short-name develop`.
   Dependent feature: follow §15.3 first.
5. Set the spec header `**Status**: In Progress`.
6. Any conflict → STOP and report with the §3 "GIT CONFLICT DETECTED" format.

### 15.3 Dependent branches

A feature that needs another unfinished feature declares it in its spec header:
`**Depends on**: 002-local-data-layer stacked` (or `waiting`).

- **Stack or wait (hybrid rule)**: create the branch from the parent's branch (`stacked`) only if
  `scripts/branches.sh stack-ok <spec>` passes, i.e. every unintegrated parent has
  `**Contract**: Approved (YYYY-MM-DD, <approver>)` and a `## Contract` section listing the
  interfaces, models and data shapes dependents may rely on. Otherwise mark it `waiting` and do not
  start the branch until the parent is integrated.
- While stacked, rely only on the approved contract. Editing the parent's `## Contract` resets it
  to `Draft`; it must be re-approved and the dependent's owner informed before the dependent is
  updated.
- Update a stacked branch with `git merge <parent-branch>`. **Never rebase or force push.**
- When the parent is integrated: `git merge develop` into the dependent and mark the entry
  `(integrated)`, e.g. `002-local-data-layer stacked (integrated)`.
- Parent abandoned or redesigned: notify dependents' owners and update their specs before
  continuing.
- Cycles are rejected (`branches.sh` exits 2): extract the shared part into its own feature.
- Chains deeper than 2 levels require agreement of both developers (`branches.sh` warns).

### 15.4 Integrate into develop

1. Set the spec header `**Status**: Ready`.
2. `scripts/branches.sh check <branch>` must print `OK`. It fails if the name is invalid, the spec
   is not `Ready` (`docs/`, `fix/`, `chore/` and `hotfix/` branches amending an already
   `Integrated` spec are also accepted), has no owner, or any parent is not yet integrated (parents always go first).
3. Constitution quality gates pass (tests, compilation for authorized targets, responsive check).
4. If `check` printed `APPROVAL REQUIRED`, the other developer approves before merging. Otherwise
   the developer may integrate their own work. `PROTECTED PATH` requires the approval recorded in
   the plan.
5. As the last commit on the work branch, set `**Status**: Integrated` (never commit on `develop`
   directly).
6. `git switch develop && git merge --no-ff <branch>`. On conflict: STOP (§3).
7. Delete the branch (`git branch -d <branch>`) and tell the owners of any dependents.
8. Never force push or rewrite `develop`.

### 15.5 Promote and hotfix

- **Promotion to `master` is manual and performed only by the developers.** Claude MUST NOT
  suggest, recommend or list promotion as a next step, and MUST NOT run any command that updates
  `master` or creates release tags. Only when a developer explicitly asks for it in the current
  request may Claude perform it.
- Reference for the developers (manual):
  `git switch master && git merge --ff-only develop && git tag -a vMAJOR.MINOR.PATCH -m "<summary>"`.
- Feature branches are never merged into `master`.
- Hotfix: `git switch -c hotfix/NNN-short-name master`, fix, then `git merge --no-ff` into `master`
  (tag a PATCH version) and into `develop`. The merge into `master` and the tag follow the same
  manual-only rule.

---

## 16. CI/CD (GitHub Actions)

Workflows live in `.github/workflows/` (high-risk shared area). They only run once the
repository is pushed to GitHub.

### 16.1 Pull requests — `pr-check.yml`

- Runs on every pull request targeting `develop` or `master`.
- Clean compile only, **no APK**:
  `./gradlew clean :shared:compileAndroidMain :shared:compileKotlinJvm :androidApp:compileDebugKotlin :desktopApp:compileKotlin`.
- iOS targets cannot be compiled on Linux runners and are Builder-owned (§6).
- Run the same command locally before opening a pull request.

### 16.2 Develop release — `develop-release.yml`

- Runs on **`develop` after a pull request is merged** (trigger: push to `develop`). A guard job
  publishes only if the pushed commit is the merge commit of a pull request into `develop`;
  direct pushes and other commits produce no release.
- Builds `:androidApp:assembleRelease` from that `develop` commit, signs it with `apksigner` using
  repository secrets, and publishes it as a GitHub **pre-release** tagged `develop-build-<run>`.
  It never touches `master`.
- Required repository secrets (Settings → Secrets and variables → Actions):
  `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`. If any is missing the job fails. The keystore and passwords MUST NOT
  be committed.

### 16.3 Required GitHub settings (manual, repository admin)

Workflows cannot enforce approval by themselves. Protect `develop` with a branch rule:

- Require a pull request before merging, with at least 1 approval.
- Require the status check **"Compile (no APK)"** to pass, with the branch up to date.
- Block force pushes and deletions.

With this rule, every merge into `develop` comes from an approved pull request whose branch
passed the compile check, which is what triggers the develop release.

---

## 17. App Structure (M2)

- Android has **two activities** (spec 003, ARCHITECTURE.md §2): `AuthActivity` (launcher; system
  launch screen, splash, welcome, login, registration, PIN recovery, M3 KYC entry) and
  `MainActivity` (M4 main area). Screens are shared Compose; each activity hosts one flow root
  (`AuthFlow`, `MainFlow`). Switching activities uses the veil transition and clears the task.
- **M3** plugs into `AuthFlow(kycEntry = …)`; **M4** plugs into `MainFlow(content = …)`. Do not edit
  M2 screens or `MainActivity`'s lock handling from those milestones (contracts in
  `specs/003-access-registration/contracts/`).
- `designsystem/` and `navigation/` are **shared areas** reused by M4: use `Q.colors`, `Q.type`,
  `QSpacing`, `QRadius`, `QSize` only — no literal colors or sizes in screens (constitution VI).
- Re-authentication: cold start always goes through login; returning after > 5 minutes in the
  background shows `UnlockScreen` over MainActivity (`AppLock`).
