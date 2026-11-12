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

<!-- Maintain this tree using the actual repository structure. Last verified: 2026-11-12. -->

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
│       │   │   ├── App.kt            # Root Compose UI
│       │   │   ├── Greeting.kt
│       │   │   ├── GreetingUtil.kt
│       │   │   └── Platform.kt       # expect declarations
│       │   └── composeResources/drawable/
│       ├── commonTest/kotlin/com/codewave/qullqi/SharedCommonTest.kt
│       ├── androidMain/kotlin/com/codewave/qullqi/Platform.android.kt
│       ├── androidHostTest/kotlin/com/codewave/qullqi/SharedLogicAndroidHostTest.kt
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
│       ├── kotlin/com/codewave/qullqi/MainActivity.kt
│       └── res/                      # drawable, drawable-v24, mipmap-*, values/strings.xml
├── desktopApp/                       # Desktop (JVM) entry point                  [Desktop — do not modify]
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/codewave/qullqi/main.kt
├── iosApp/                           # Xcode project, Swift                       [iOS — do not modify]
│   ├── Configuration/Config.xcconfig
│   ├── iosApp/                       # iOSApp.swift, ContentView.swift, Info.plist, Assets.xcassets
│   └── iosApp.xcodeproj/
├── docs/references-views/            # UI reference designs and guidelines (read-only reference)
│   ├── ARCHITECTURE.md
│   ├── STYLEGUIDE.md
│   ├── UI_GUIDELINES.md
│   ├── Qullqi Pay.dc.html
│   ├── Qullqi Pay Auth.dc.html
│   └── _ds/                          # Design system bundle
├── .specify/                         # Spec Kit: constitution, templates, scripts, workflows
└── .claude/skills/                   # Spec Kit agent skills
```

Notes:
- No feature modules, navigation, DI or test directories beyond the template defaults exist yet.
- No Builder-managed directories exist in the repository yet. Build outputs (`**/build/`, `.gradle`, `.kotlin`) are gitignored.
- Tests: `commonTest` (portable), `androidHostTest` (Android JVM host), `jvmTest` and `iosTest` (platform-specific).

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
