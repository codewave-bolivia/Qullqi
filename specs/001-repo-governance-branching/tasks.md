---

description: "Task list for Repository Architecture and Development Governance"
---

# Tasks: Repository Architecture and Development Governance

**Input**: Design documents from `/specs/001-repo-governance-branching/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. The plan defines `scripts/test-branches.sh` as this feature's test; scenarios
come from quickstart.md §1.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5 from spec.md)
- Include exact file paths in descriptions

## Path Conventions

Repository root: `CLAUDE.md`, `scripts/`, `.githooks/`, `specs/`. No Gradle module, Kotlin source
or Builder-owned path (`iosApp/`, `desktopApp/`, `shared/src/iosMain`, `iosTest`, `jvmMain`,
`jvmTest`) is touched by any task.

Shared scripting conventions for every script task: `#!/usr/bin/env bash`, `set -euo pipefail`,
no network, and **read-only toward Git** (never commit, merge, checkout, reset, stash, fetch or
push — contracts/branches-cli.md).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Put the work on its own branch and make spec 001 conform to the metadata contract.

- [X] T001 Follow CLAUDE.md §3 before starting: confirm the working tree state on `feature/000-constitution-config` (uncommitted `CLAUDE.md`, `.specify/memory/constitution.md`, `specs/` belong to the user — never discard/stash them), ask the user to commit or confirm, then create `feature/001-repo-governance-branching` from `develop` (or from `feature/000-constitution-config` if it is not yet merged into `develop`, recording that as a dependency) in the repository root
- [X] T002 Add the metadata header to specs/001-repo-governance-branching/spec.md per contracts/spec-metadata.md: keep `**Feature Branch**: \`feature/001-repo-governance-branching\``, keep `**Owner**: krypton612`, set `**Status**: In Progress`, add `**Depends on**: none` and `**Contract**: none`, all before the first `##` heading

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Script skeleton, spec parsing and test harness used by every story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 Create scripts/branches.sh (executable) with repo-root detection via `git rev-parse --show-toplevel`, subcommand dispatch for `list`, `check <branch>`, `stack-ok <spec-id>`, and a usage message (exit 64 on unknown subcommand)
- [X] T004 Implement spec discovery and header parsing in scripts/branches.sh: enumerate `specs/[0-9][0-9][0-9]-*/spec.md`, spec id = directory name, read fields `Feature Branch`, `Owner`, `Status`, `Depends on`, `Contract` from lines `**<Field>**: <value>` before the first `##`; missing `Owner` or `Status` (or `Owner` equal to `TODO…`) is an error, missing `Depends on`/`Contract` is a stderr warning treated as `none`; `Status` MUST be one of `Draft | In Progress | Ready | Integrated | Abandoned`
- [X] T005 Implement dependency parsing in scripts/branches.sh: `Depends on` is `none` or comma-separated `NNN-short-name [stacked|waiting]` optionally suffixed `(integrated)`; build the graph, detect cycles with depth-first traversal (exit 2 naming the cycle), warn on stderr when a chain is deeper than 2 ("requires agreement of both developers")
- [X] T006 [P] Create scripts/test-branches.sh (executable): create a temp dir with `mktemp -d` and `trap` cleanup, `git init` a fixture repo with `master` and `develop`, helpers `write_spec <id> <owner> <status> <depends> <contract>`, `expect_exit <code> <cmd…>`, `expect_output <pattern> <cmd…>`, print `ok <name>` per scenario and `ALL PASSED` at the end; invoke the real `scripts/branches.sh` by absolute path from inside the fixture repo; it MUST never touch the real repository

**Checkpoint**: Foundation ready — user stories can start.

---

## Phase 3: User Story 1 - Start an independent work branch safely (Priority: P1) 🎯 MVP

**Goal**: Anyone can start a correctly named branch from the latest integration state, and direct commits on shared branches are impossible.

**Independent Test**: Run scripts/test-branches.sh scenarios "commit on develop refused" and "bad branch name"; follow CLAUDE.md §15.1 on a clean clone and reach a correctly named branch in < 5 minutes.

### Tests for User Story 1

- [X] T007 [US1] Add scenarios to scripts/test-branches.sh: (a) with `core.hooksPath` pointing at the real `.githooks`, `git commit --allow-empty` on `develop` exits non-zero; (b) `scripts/branches.sh check wallet-screen` exits 1 with a naming error

### Implementation for User Story 1

- [X] T008 [P] [US1] Create .githooks/pre-commit (executable): if `git symbolic-ref --short HEAD` is `develop` or `master`, print "Direct commits on <branch> are not allowed (CLAUDE.md §15, FR-007). Create a work branch." and exit 1; otherwise exit 0
- [X] T009 [US1] Implement branch-name validation in scripts/branches.sh used by `check`: regex `^(feature|fix|docs|chore|hotfix)/[0-9]{3}-[a-z0-9-]+$`; on mismatch print the rule and exit 1
- [X] T010 [US1] Add CLAUDE.md §15 "Branching and Dependent Branches" with §15.1 "Branches and naming" (integration `develop`, stable `master`, no direct commits, `<type>/<NNN>-<short-name>`, one spec per significant branch with a named owner) and §15.2 "Start a branch": one-time `git config core.hooksPath .githooks`; steps reference §3 (status, never discard/stash, fetch if a remote exists — otherwise report "no remote: not applicable", `git merge --ff-only` on `develop`, create branch from `develop`); dirty tree or conflict → stop and report

**Checkpoint**: US1 independently usable.

---

## Phase 4: User Story 2 - Work on a branch that depends on another unfinished branch (Priority: P1)

**Goal**: Dependencies are declared, visible in one view, stacking follows the hybrid rule, and children cannot be integrated before parents.

**Independent Test**: scripts/test-branches.sh scenarios "parent not integrated", "stack-ok Draft", "stack-ok Approved", "cycle"; `scripts/branches.sh list` shows all specs with owner, status and dependencies.

### Tests for User Story 2

- [X] T011 [US2] Add scenarios to scripts/test-branches.sh: (a) child `Ready` depending on parent `In Progress` not merged into `develop` → `check` exits 1 naming the parent; (b) `stack-ok` child whose parent has `Contract: Draft` → exit 1; (c) parent `Contract: Approved (2026-11-12, tester)` → exit 0; (d) A depends on B and B on A → `list` exits 2; (e) `list` output contains spec id, owner and status columns

### Implementation for User Story 2

- [X] T012 [US2] Implement `list` in scripts/branches.sh: one row per spec with columns `SPEC BRANCH OWNER STATUS DEPENDS ON GIT`, `GIT` ∈ `local | remote | local+remote | missing | merged` (merged = `git merge-base --is-ancestor <branch> develop`), plus rows `— <branch> — — — local (no spec)` for work branches without a spec; stderr warnings for branch without spec and for `In Progress` spec without branch; exit 0, or 2 on cycle/malformed required field
- [X] T013 [US2] Implement the parent rule in `check` in scripts/branches.sh: every parent in `Depends on` MUST be `Status: Integrated` or have its branch merged into `develop`; otherwise exit 1 listing the blocking parents (FR-016)
- [X] T014 [US2] Implement `stack-ok <spec-id>` in scripts/branches.sh: exit 0 iff every non-integrated parent has `Contract: Approved (YYYY-MM-DD, <approver>)`; otherwise exit 1 naming parents without an approved contract (FR-015)
- [X] T015 [US2] Add CLAUDE.md §15.3 "Dependent branches": declare `Depends on` with mode `stacked|waiting`; stack only if `scripts/branches.sh stack-ok` passes (parent `## Contract` approved), otherwise wait; while stacked rely only on the contract, contract edits reset it to `Draft`; update with `git merge <parent>` — never rebase or force push; after the parent is integrated run `git merge develop`, mark the entry `(integrated)`; parent abandoned → notify dependents and update their specs; cycles rejected; chains deeper than 2 need both developers' agreement; any conflict → stop with the §3 report
- [X] T016 [US2] Add a reference in CLAUDE.md §13.4 to the metadata header contract: every spec header MUST contain `Feature Branch`, `Owner`, `Status`, `Depends on`, `Contract` (link specs/001-repo-governance-branching/contracts/spec-metadata.md)

**Checkpoint**: US1 + US2 usable — the requested scope (work branches and dependent branches) is complete.

---

## Phase 5: User Story 3 - Know where code belongs and who owns it (Priority: P2)

**Goal**: Every path classifies as authorized, shared or protected from documentation, and `check` flags shared/protected changes.

**Independent Test**: classify the 10 sample changes in quickstart.md §2 using only CLAUDE.md §2 with 10/10 agreement; scripts/test-branches.sh scenarios "shared path" and "protected path".

### Tests for User Story 3

- [X] T017 [US3] Add scenarios to scripts/test-branches.sh: a `Ready` branch changing `gradle/libs.versions.toml` → `check` output contains `APPROVAL REQUIRED`; a branch changing `iosApp/x.swift` → output contains `PROTECTED PATH`; both keep exit 0 when no other rule fails

### Implementation for User Story 3

- [X] T018 [US3] Add the "Repository areas" table to CLAUDE.md §2 copying data-model.md verbatim: authorized `shared/src/commonMain/**`, `shared/src/commonTest/**`, `shared/src/androidMain/**`, `shared/src/androidHostTest/**`, `androidApp/src/**`; shared `*.gradle.kts`, `gradle/**`, `gradle.properties`, `CLAUDE.md`, `.specify/memory/**`, `scripts/**`, `.githooks/**`, `**/sqldelight/**`, `**/designsystem/**`, `**/navigation/**`, domain models, repository interfaces; protected `iosApp/**`, `desktopApp/**`, `shared/src/iosMain/**`, `shared/src/iosTest/**`, `shared/src/jvmMain/**`, `shared/src/jvmTest/**`, `**/build/**`; precedence `protected` > `shared` > `authorized`; per-class action; plus the layer rule `ui → presentation → domain ← data` referencing the constitution
- [X] T019 [US3] Implement path classification in scripts/branches.sh (`classify_path`, same patterns and precedence as T018) and, in `check`, list `git diff --name-only develop...<branch>` paths classified shared as `APPROVAL REQUIRED (FR-021): <path>` and protected as `PROTECTED PATH — Builder or written approval required: <path>`, without changing the exit code

**Checkpoint**: US3 usable.

---

## Phase 6: User Story 4 - Integrate finished work into the shared line (Priority: P2)

**Goal**: Integration happens only after gates and the review rule, without rewriting history.

**Independent Test**: scripts/test-branches.sh scenarios "Ready independent branch" (exit 0) and "not Ready" (exit 1); follow CLAUDE.md §15.4 on a fixture branch.

### Tests for User Story 4

- [X] T020 [US4] Add scenarios to scripts/test-branches.sh: `Ready` independent branch with valid name → `check` exits 0; same branch with `Status: In Progress` → exit 1 stating status must be `Ready`

### Implementation for User Story 4

- [X] T021 [US4] Complete `check` in scripts/branches.sh: require a spec for `feature/` branches whose number matches a spec directory, `Owner` present, `Status: Ready`; aggregate all failures before exiting 1; cycle → exit 2
- [X] T022 [US4] Add CLAUDE.md §15.4 "Integrate into develop": `scripts/branches.sh check <branch>` passes; constitution quality gates pass; if `APPROVAL REQUIRED` appears, get the other developer's approval (self-integration otherwise); `git switch develop && git merge --no-ff <branch>`; set spec `Status: Integrated`; delete the branch; notify owners of dependents; never force push or rewrite `develop`

**Checkpoint**: US4 usable.

---

## Phase 7: User Story 5 - Promote a stable state (Priority: P3)

**Goal**: `master` only receives versioned promotions of `develop`; hotfixes are defined.

**Independent Test**: on a fixture repo, promote `develop` and verify `master` equals `develop` and carries a `vX.Y.Z` tag.

- [X] T023 [US5] Add CLAUDE.md §15.5 "Promote and hotfix": promotion is a team decision; `git switch master && git merge --ff-only develop && git tag -a vMAJOR.MINOR.PATCH -m "<summary>"`; feature branches are never merged into `master`; hotfix: `hotfix/NNN-name` from `master`, merge `--no-ff` into `master` (tag) and into `develop`

**Checkpoint**: All stories complete.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T024 Update the repository tree in CLAUDE.md §2 to add `scripts/` (branches.sh, test-branches.sh), `.githooks/` (pre-commit) and `specs/`, and refresh "Last verified" date
- [X] T025 Run `scripts/test-branches.sh` and confirm `ALL PASSED`; run `shellcheck scripts/*.sh .githooks/pre-commit` if available (report if not installed)
- [X] T026 Run the manual walkthrough in specs/001-repo-governance-branching/quickstart.md §2 on the real repository (`scripts/branches.sh list`, refused commit on `develop`, 10-change classification) and record results
- [X] T027 Set `**Status**: Ready` in specs/001-repo-governance-branching/spec.md, run `scripts/branches.sh check feature/001-repo-governance-branching`, and produce the CLAUDE.md §11 report noting that `CLAUDE.md` changes require the other developer's approval (FR-021)

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (T001–T002) → Foundational (T003–T006) → user stories → Polish.
- US1 and US2 are both P1; US2's `check` parent rule builds on T009 (name validation) only through
  the shared `check` function — do US1 first.
- US3 and US4 extend `check`; they can follow US2 in either order. US5 is documentation only and
  can be written any time after T010.

### Within each story

- Test scenario task first (it fails), then script implementation, then CLAUDE.md documentation.
- Tasks editing the same file are sequential: scripts/branches.sh (T003–T005, T009, T012–T014,
  T019, T021), scripts/test-branches.sh (T006, T007, T011, T017, T020), CLAUDE.md (T010, T015,
  T016, T018, T022–T024).

### Parallel Opportunities

- T006 (test harness) ∥ T003–T005 (branches.sh).
- T008 (.githooks/pre-commit) ∥ T009 (branches.sh).
- Any CLAUDE.md documentation task ∥ script tasks of the same story (different files).

## Parallel Example: User Story 2

```text
Developer/agent A: T012 → T013 → T014   (scripts/branches.sh)
Developer/agent B: T015 → T016          (CLAUDE.md)
Then: T011 scenarios run green
```

## Implementation Strategy

### MVP (US1 only)

T001–T010: hook + naming + start procedure. Already prevents direct commits on shared branches
and standardizes branch starts.

### Requested scope (US1 + US2)

Add T011–T016: dependent branches, hybrid stacking, single view of branches. This covers the
user's explicit request ("ramas de trabajo y ramas que dependen de otros").

### Incremental delivery

US3 (area classification) → US4 (integration) → US5 (promotion) → Polish. Each phase ends with
`scripts/test-branches.sh` green.
