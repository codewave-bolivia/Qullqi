# Feature Specification: Repository Architecture and Development Governance

**Feature Branch**: `feature/001-repo-governance-branching`

**Created**: 2026-11-12

**Status**: Integrated

**Owner**: krypton612

**Depends on**: none

**Contract**: none

**Input**: User description: "first spec Repository Architecture and Development Governance, ramas de trabajo y ramas que dependen de otros para poder funcionar."

## Context

Qullqi is built by two developers working at the same time, each possibly assisted by a different
AI agent. This specification defines the rules both follow so that work never overwrites the
other's: how the repository is organized, which branches exist, how a work branch is born, lives
and is integrated, and how a branch that depends on another unfinished branch is handled.

The actors are the two **developers** and their **AI agents**. The "users" of this feature are
therefore the development team; the outcome is a documented, enforceable working agreement and a
repository layout that supports it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start an independent work branch safely (Priority: P1)

A developer wants to start a new feature that does not depend on any other unfinished work. They
(or their agent) synchronize with the integration branch, create a work branch with the agreed
name, and begin working, confident that they start from the latest shared state and that no one
else's changes were lost.

**Why this priority**: every other workflow builds on this one. Without a safe, repeatable start,
parallel work causes overwrites and conflicts from the first day.

**Independent Test**: on a repository with up-to-date integration history, a developer follows
the documented procedure and ends with a correctly named work branch based on the latest
integration state and a clean working tree, without any existing change being discarded.

**Acceptance Scenarios**:

1. **Given** a clean working tree and an integration branch with new commits from the other
   developer, **When** the developer starts a feature, **Then** their work branch is created from
   the latest integration state and follows the naming convention.
2. **Given** uncommitted changes in the working tree, **When** the developer (or agent) tries to
   start a feature, **Then** the procedure stops, reports the changes and does not discard,
   reset or stash them automatically.
3. **Given** synchronization produces a conflict, **When** the procedure runs, **Then** all work
   stops and a conflict report is produced with branch, files, nature, impact and recommended
   action.

---

### User Story 2 - Work on a branch that depends on another unfinished branch (Priority: P1)

A developer needs to build a feature that cannot work without another feature that is still in
progress on a different branch (for example, a wallet screen that needs the local data layer still
being built). The dependency is declared explicitly, the dependent branch is created according to
the agreed strategy, and both developers can see what depends on what and in which order the
branches must be integrated.

**Why this priority**: this is the scenario explicitly requested. Without rules, dependent work
either waits idle or is built on unstable foundations and breaks when the parent changes.

**Independent Test**: create a parent branch and a dependent branch following the procedure;
verify the dependency is recorded where both developers can see it, the dependent branch cannot be
integrated before its parent, and changes to the parent are brought into the dependent branch
through the documented procedure.

**Acceptance Scenarios**:

1. **Given** feature B requires feature A, which is not yet integrated, **When** the developer
   starts feature B, **Then** B's specification records "depends on A" and B is created following
   the hybrid dependent-branch strategy: if A's specification already defines and has approved
   the contract B consumes (interfaces, models, data shapes), B is created from A's branch
   (stacked); otherwise B is not started until A is integrated.
2. **Given** B depends on A, **When** someone attempts to integrate B while A is not yet
   integrated, **Then** the integration is refused until A is integrated first.
3. **Given** B depends on A and A receives new changes, **When** the developer of B updates their
   branch, **Then** A's changes are brought into B and any conflict stops work and is reported,
   never resolved automatically.
4. **Given** A is integrated, **When** B is updated afterwards, **Then** B is based on the
   integration branch and no longer references A as a pending dependency.

---

### User Story 3 - Know where code belongs and who owns it (Priority: P2)

A developer or agent about to change something can determine, from documentation alone, which
module and layer the change belongs in, whether the area is in their authorized scope, whether it
is a high-risk shared area requiring coordination, and whether it is owned by the Builder.

**Why this priority**: it prevents the most common accidental damage (editing protected platform
files or shared files without coordination), but it depends on the branching rules being in place
first.

**Independent Test**: give a reviewer a list of hypothetical changes (a new screen, a new table, a
change to the iOS host, a new dependency); using only the documentation, they classify each as
"in scope", "shared — coordinate" or "protected — needs approval" with no ambiguous cases.

**Acceptance Scenarios**:

1. **Given** a planned change, **When** a developer consults the repository documentation,
   **Then** they can identify the target module and layer within 2 minutes.
2. **Given** a change to a protected platform area, **When** it is proposed, **Then** the
   documentation states it requires Builder involvement or explicit approval.
3. **Given** a change to a high-risk shared area, **When** it is proposed, **Then** the
   documentation requires checking recent history and coordinating with the other developer.

---

### User Story 4 - Integrate finished work into the shared line (Priority: P2)

A developer finishes a feature and wants to integrate it. The work passes the agreed quality gates,
is reviewed according to the agreed rule, and is integrated without rewriting shared history; the
other developer is aware of the change.

**Why this priority**: integration closes the loop of every feature, but it only matters once
branches exist.

**Independent Test**: take a finished feature branch, run the documented integration procedure,
and verify the gates were checked, the review rule was respected and shared history was not
rewritten.

**Acceptance Scenarios**:

1. **Given** a finished feature with passing quality gates, **When** it is integrated, **Then**
   the developer may integrate it themselves; approval by the other developer is required only
   when the change touches a high-risk shared area.
2. **Given** a feature whose gates fail, **When** integration is attempted, **Then** it is refused
   and the failure is reported.
3. **Given** any integration, **When** it completes, **Then** no shared history was rewritten
   (no forced overwrites of shared branches).

---

### User Story 5 - Promote a stable state (Priority: P3)

When the integration branch reaches a state the team considers stable, it is promoted to the
stable branch in a traceable way, so there is always a known-good version to demonstrate or hand
to the Builder.

**Why this priority**: useful for demos and Builder hand-off, but not required for day-to-day
parallel development.

**Independent Test**: promote the integration branch to the stable branch and verify the stable
branch contains exactly the promoted state and the promotion is identifiable.

**Acceptance Scenarios**:

1. **Given** the integration branch passes all gates, **When** the team decides to promote,
   **Then** the stable branch is updated to that state and the promotion is labeled with a version.
2. **Given** a feature branch, **When** someone attempts to integrate it directly into the stable
   branch, **Then** it is refused; only the integration branch is promoted.

### Edge Cases

- A parent branch is abandoned or deeply redesigned: dependent branches are flagged, their
  specifications updated, and their owners notified before continuing.
- Chains of dependencies (C depends on B, which depends on A): integration order follows the
  chain; chains deeper than two levels require explicit agreement of both developers.
- Circular dependencies (A depends on B and B depends on A): must be rejected when declared; the
  shared part is extracted into its own feature.
- Both developers need to change the same high-risk shared file at once: one change goes first;
  the other waits or both coordinate a single change.
- A developer's agent finds the working tree dirty with changes it did not make: it stops and asks
  instead of assuming ownership.
- No shared remote is configured yet: synchronization steps that require one are reported as
  not applicable rather than silently skipped.
- A hotfix is needed on the stable branch: handled as a short-lived branch from the stable branch
  that is integrated back into both the stable and integration branches.

## Requirements *(mandatory)*

### Functional Requirements

**Repository architecture**

- **FR-001**: The repository MUST document its actual structure (modules, layers, shared and
  platform-specific areas, tests, documentation) and keep it current when the structure changes.
- **FR-002**: Every area of the repository MUST be classified as exactly one of: *authorized
  scope*, *high-risk shared* or *protected (Builder-owned)*.
- **FR-003**: The documentation MUST define the layers of shared application code (domain, data,
  presentation, UI) and the allowed direction of dependencies between them.
- **FR-004**: Changes to protected areas MUST require Builder involvement or explicit written
  approval recorded in the feature's plan.
- **FR-005**: Changes to high-risk shared areas MUST require checking recent history and
  coordinating with the other developer before editing.

**Branches**

- **FR-006**: The repository MUST have one integration branch (`develop`) where finished features
  are combined, and one stable branch (`master`) that only receives promotions from the
  integration branch.
- **FR-007**: All work MUST happen on short-lived work branches; direct commits to the integration
  or stable branch MUST NOT be made.
- **FR-008**: Work branch names MUST follow `<type>/<NNN>-<short-name>`, where `NNN` matches the
  specification number when one exists and `type` is one of `feature`, `fix`, `docs`, `chore`
  or `hotfix`.
- **FR-009**: Each significant work branch MUST correspond to exactly one specification, and the
  specification MUST name its owner.
- **FR-010**: Before starting a work branch, the developer or agent MUST synchronize with the
  integration branch without discarding, resetting or automatically stashing existing changes.
- **FR-011**: Any synchronization conflict MUST stop all work and produce a conflict report;
  conflicts MUST NOT be resolved automatically.
- **FR-012**: Shared branches MUST NOT have their history rewritten (no forced overwrites).

**Dependent branches**

- **FR-013**: A feature that requires another unfinished feature MUST declare the dependency in
  its specification, naming the parent specification and branch.
- **FR-014**: The team MUST be able to see, in a single place, all active work branches, their
  owners, their status and what each depends on.
- **FR-015**: A dependent branch MAY be created from its parent's branch (stacked) only when the
  parent's specification defines an approved contract that the dependent consumes (interfaces,
  models, data shapes). Without an approved contract, the dependent MUST NOT be started until the
  parent is integrated.
- **FR-015a**: While stacked, the dependent MUST rely only on the approved contract; any change to
  that contract in the parent MUST be re-approved and communicated to the dependent's owner
  before the dependent is updated.
- **FR-016**: A dependent branch MUST NOT be integrated before all of its parents are integrated.
- **FR-017**: When a parent branch changes, its dependents MUST be updated through the documented
  procedure, and the owner of each dependent MUST be informed.
- **FR-018**: Circular dependencies MUST be rejected at declaration time.
- **FR-019**: When a parent is integrated, each dependent MUST be re-based on the integration
  branch and its dependency record updated.

**Integration and promotion**

- **FR-020**: A work branch MUST pass the quality gates defined in the constitution before
  integration.
- **FR-021**: A developer MAY integrate their own work into the integration branch once the
  quality gates pass. Integration MUST be approved by the other developer when the change touches
  any high-risk shared area (build configuration, version catalog, navigation, dependency
  wiring, theme and design system, shared models, repository interfaces, database schema or seed
  data).
- **FR-022**: Promotion from the integration branch to the stable branch MUST be an explicit team
  decision, labeled with a version.
- **FR-023**: Work branches MUST be deleted after integration so the active-branch list reflects
  only work in progress.

### Key Entities

- **Integration branch**: the shared line where finished features are combined (`develop`).
- **Stable branch**: the known-good line, updated only by promotion (`master`).
- **Work branch**: a short-lived branch for one specification; has a type, number, short name,
  owner, status and zero or more parents.
- **Dependency**: a "requires" relation from a dependent work branch to a parent work branch;
  determines integration order and whether stacking is allowed.
- **Contract**: the part of a parent specification that a dependent consumes (interfaces, models,
  data shapes); must be approved before a dependent may stack on the parent.
- **Specification**: the document that defines a feature; has an owner and declares dependencies.
- **Repository area**: a part of the repository with a classification (authorized, shared,
  protected) and, when shared, an expected coordination step.
- **Conflict report**: the structured stop notice produced when synchronization fails.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero incidents of one developer's committed work being lost or overwritten by the
  other during the first three months of parallel development.
- **SC-002**: A developer or agent can start a new correctly named, synchronized work branch by
  following the documentation in under 5 minutes.
- **SC-003**: 100% of active work branches appear in the shared branch list with owner, status and
  dependencies.
- **SC-004**: 100% of dependent branches are integrated after their parents (no out-of-order
  integration).
- **SC-005**: Given 10 hypothetical changes, a reviewer classifies each as authorized, shared or
  protected using only the documentation, with 10/10 agreement with the documented rules.
- **SC-006**: 100% of synchronization conflicts are reported in the agreed format, with none
  resolved automatically.
- **SC-007**: The stable branch only ever contains states promoted from the integration branch.

## Clarifications

### Session 2026-11-12

- Q: Dependent-branch strategy? → A: Hybrid. Stack on the parent branch only when the parent's
  consumed contract is specified and approved; otherwise wait until the parent is integrated.
- Q: Review rule for integrating into `develop`? → A: Self-integration once quality gates pass;
  the other developer's approval is required only for changes to high-risk shared areas.
- Q: Specification owner? → A: krypton612.

## Assumptions

- `develop` is the integration branch and `master` the stable branch; this matches the current
  repository, where `develop` is ahead of `master`.
- The existing `feature/000-constitution-config` branch is the precedent for the naming
  convention.
- A shared remote repository will be added; until then, steps that require it are reported as not
  applicable.
- Two developers is the expected team size; rules do not need to scale to larger teams yet.
- The quality gates are those already defined in the constitution; this specification does not
  redefine them.
- Repository structure, ownership classification and Git synchronization rules already written in
  `CLAUDE.md` §2, §3 and §9 are the starting point; this feature formalizes and completes them,
  adding the missing rules for dependent branches, integration and promotion.
- Enforcement may combine documentation, checklists and, where the hosting platform allows,
  branch protection; the choice belongs to the plan.

## Dependencies

- Depends on: constitution v1.1.0 (`.specify/memory/constitution.md`).
- No dependency on other specifications (this is specification 001).
- Blocks: every later feature specification, which will use the branch and dependency rules
  defined here.
