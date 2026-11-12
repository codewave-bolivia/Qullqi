# Research: Repository Architecture and Development Governance

No `NEEDS CLARIFICATION` remained in the Technical Context. The items below record the design
decisions taken and the alternatives rejected.

## R1. Where the single view of active branches lives (FR-014)

- **Decision**: derive it. Each `specs/NNN-*/spec.md` declares owner, status, branch, dependencies
  and contract status in its header ([contracts/spec-metadata.md](contracts/spec-metadata.md));
  `scripts/branches.sh list` joins that with local/remote Git branches.
- **Rationale**: a shared registry file edited by both developers on different branches is itself
  a guaranteed merge-conflict source — the exact problem the feature solves. Spec headers are
  only edited by the spec owner.
- **Alternatives**: `BRANCHES.md` registry table (conflicts on every feature start); issue
  tracker / project board (no remote yet, external service needs approval per CLAUDE.md).

## R2. How dependent branches are updated when the parent changes (FR-017, FR-019)

- **Decision**: merge, never rebase. `git merge <parent>` into the dependent while stacked; after
  the parent is integrated, `git merge develop` into the dependent and change its `Depends on`
  entry to integrated.
- **Rationale**: rebasing a pushed work branch requires a force push, forbidden by CLAUDE.md §3.
  After the parent lands in `develop`, merging `develop` makes the dependent's diff against
  `develop` contain only its own work — functionally the "re-base on the integration branch"
  FR-019 asks for, without rewriting history.
- **Alternatives**: `git rebase --onto develop <parent>` (clean history, needs force push);
  squash-merging parents (breaks the dependent's ancestry and causes duplicate conflicts).

## R3. Integration and promotion mechanics (FR-006, FR-020–FR-023)

- **Decision**:
  - Feature → `develop`: `git merge --no-ff <branch>` (local now, pull request once a remote
    exists), then delete the work branch.
  - `develop` → `master`: `git merge --ff-only develop` + annotated tag `vMAJOR.MINOR.PATCH`.
  - Hotfix: `hotfix/NNN-name` from `master`, merged `--no-ff` into `master` (tagged) and `develop`.
- **Rationale**: `--no-ff` keeps one identifiable merge commit per feature (traceability to its
  spec); `--ff-only` guarantees `master` only holds states that existed on `develop` (SC-007).
- **Alternatives**: squash merges (lose per-commit history, break stacked dependents — see R2);
  rebase-merge (rewrites commits).

## R4. Enforcement without a remote (FR-007, FR-012, FR-016)

- **Decision**: a versioned `.githooks/pre-commit` refusing commits on `develop`/`master`,
  activated once per clone with `git config core.hooksPath .githooks`; `scripts/branches.sh check
  <branch>` run before every integration (documented as a mandatory step for humans and agents).
- **Rationale**: lowest-cost enforcement that works offline. Server-side protection is stronger
  but needs a remote.
- **Alternatives**: `pre-merge-commit` hook to block out-of-order integration (fires only for
  non-fast-forward merges and cannot see intent reliably; the explicit `check` is simpler);
  husky/lefthook (adds a dependency with install scripts — needs approval per CLAUDE.md).

## R5. Contract approval for stacking (FR-015, FR-015a)

- **Decision**: the parent spec carries a `## Contract` section and a header field
  `**Contract**: Draft | Approved (YYYY-MM-DD, <approver>)`. `branches.sh stack-ok <child>`
  succeeds only if every non-integrated parent has `Approved`. A contract change resets it to
  `Draft` until re-approved.
- **Rationale**: makes the hybrid rule checkable by a script and visible in review.
- **Alternatives**: approval only in conversation (not verifiable); separate contract files
  (more files, same information).

## R6. High-risk shared areas requiring approval at integration (FR-021)

- **Decision**: `branches.sh check` lists changed paths that match the high-risk patterns
  (`*.gradle.kts`, `gradle/`, `gradle.properties`, `CLAUDE.md`, `.specify/memory/`,
  `**/sqldelight/**`, `**/designsystem/**`, `**/navigation/**`, `**/domain/model/**`,
  `**/data/repository/**` interfaces) and prints "approval required"; exit code stays 0 for this
  warning (approval is a human act), non-zero only for hard failures.
- **Rationale**: the rule is self-integration except for shared areas; the script makes the
  exception impossible to miss without pretending to verify a human approval.
- **Alternatives**: CODEOWNERS (needs a remote host); blocking exit code (would block legitimate
  approved merges with no way to record approval locally).

## R7. Cycle detection and chain depth (FR-018, edge cases)

- **Decision**: depth-first traversal over `Depends on` in `branches.sh`; a cycle is a hard error
  in `list` and `check`. Chains deeper than 2 produce a warning ("requires agreement of both
  developers").
- **Rationale**: matches the spec's edge cases with ~20 lines of Bash.
