# Implementation Plan: Repository Architecture and Development Governance

**Branch**: `feature/001-repo-governance-branching` (not yet created; work currently on
`feature/000-constitution-config`) | **Date**: 2026-11-12 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-repo-governance-branching/spec.md`

## Summary

Formalize how the two developers organize the repository and branch their work, including
branches that depend on other unfinished branches.

Technical approach (see [research.md](research.md)):

- **Rules live in documentation**: `CLAUDE.md` gains the area classification table (§2) and a new
  §15 "Branching and Dependent Branches". No new governance file is needed.
- **Each spec declares its own metadata** (owner, status, branch, dependencies, contract status)
  in its header. No shared registry file → no merge conflicts on the registry itself.
- **One read-only script** `scripts/branches.sh` derives the single view of active branches
  (FR-014) from `specs/*/spec.md` + Git, detects cycles (FR-018) and answers "can this branch be
  integrated / stacked?" (FR-015, FR-016).
- **One local Git hook** `.githooks/pre-commit` blocks direct commits on `develop` and `master`
  (FR-007). Remote branch protection is added when a remote exists.
- **No history rewriting**: dependent branches are updated by merging, never rebasing, so no force
  push is ever required (FR-012, CLAUDE.md §3).

## Technical Context

**Language/Version**: Bash (POSIX-compatible subset + Bash 4 features), Git ≥ 2.30

**Primary Dependencies**: none beyond `git`, `grep`, `sed`, `awk` (present on Linux and macOS)

**Storage**: Markdown files in the repository (`specs/*/spec.md`, `CLAUDE.md`); Git refs

**Testing**: one self-contained check `scripts/test-branches.sh` that builds a throw-away Git
repository with fixture specs and asserts the script's output and exit codes

**Target Platform**: developer workstations (Linux, macOS); Windows via Git Bash

**Project Type**: repository governance / developer tooling for a KMP mobile+desktop app

**Performance Goals**: `scripts/branches.sh` answers in < 1 s for ≤ 50 specs

**Constraints**: read-only toward Git (never commits, merges, resets or pushes); no network
access; must work with no remote configured; no Kotlin, Gradle or Builder-owned files touched

**Scale/Scope**: 2 developers, tens of specifications, dependency chains ≤ 2 levels by default

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| I. Frontend-only, replaceable data sources | No data layer, repositories or SQLDelight work in this feature | PASS (N/A) |
| II. MVVM | No presentation code | PASS (N/A) |
| III. KMP portability | No Kotlin code; nothing added to `commonMain` | PASS (N/A) |
| IV. Platform boundaries / Builder | No task touches `iosApp/`, `desktopApp/`, `iosMain`, `iosTest`, `jvmMain`, `jvmTest`. New files: `scripts/`, `.githooks/`, docs | PASS |
| V. Collaborative integrity | Script is read-only; hook only *prevents* commits; updates by merge, no force push; conflicts stop work | PASS |
| VI. Responsive UI | No UI | PASS (N/A) |
| VII. Spec-driven delivery | Spec 001 owned by krypton612, clarified, checklist passing | PASS |
| Quality gates | `scripts/test-branches.sh` is the feature's test; Gradle gates N/A (no app code) | PASS |
| Shared-file coordination | Modifies `CLAUDE.md` (high-risk shared governance) → requires the other developer's approval at integration (FR-021) | PASS with action |

Portable vs Android-specific code: none. Repository interfaces / SQLDelight tables: none.
Responsive strategy: N/A.

**Post-design re-check (after Phase 1)**: unchanged — all PASS. The only shared-area change is
`CLAUDE.md`; no Builder-owned path appears in the design.

## Project Structure

### Documentation (this feature)

```text
specs/001-repo-governance-branching/
├── plan.md              # This file
├── research.md          # Phase 0: decisions and alternatives
├── data-model.md        # Phase 1: work branch, dependency, contract, area entities
├── quickstart.md        # Phase 1: validation scenarios
├── contracts/
│   ├── spec-metadata.md # Header fields every spec must declare
│   └── branches-cli.md  # Interface of scripts/branches.sh and the pre-commit hook
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks) — not created here
```

### Source Code (repository root)

```text
CLAUDE.md                      # MODIFY: §2 area classification table; new §15 branching rules
scripts/
├── branches.sh                # NEW: list | check <branch> | stack-ok <branch>
└── test-branches.sh           # NEW: self-check on a temporary fixture repository
.githooks/
└── pre-commit                 # NEW: refuse commits on develop/master
specs/001-repo-governance-branching/spec.md   # MODIFY: add metadata header (dogfooding)
```

**Structure Decision**: governance tooling lives in top-level `scripts/` and `.githooks/`, outside
`.specify/` (managed by Spec Kit, may be overwritten on upgrade) and outside all Gradle modules.
No application module is touched.

## Complexity Tracking

No constitution violations. Deliberately skipped (add when the trigger occurs):

| Skipped | Add when |
|---------|----------|
| Remote branch protection / PR templates | A shared remote (e.g. GitHub) is configured |
| CI job running `scripts/branches.sh check` | CI exists |
| Spec Kit `spec-template` override with metadata fields | More than 3 specs forget the header |
| Shared registry file | Never (derived view avoids registry merge conflicts) |
