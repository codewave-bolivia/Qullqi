# Contract: `scripts/branches.sh` and `.githooks/pre-commit`

## `scripts/branches.sh`

Read-only. Never commits, merges, checks out, resets, stashes, fetches or pushes. Runs from any
directory inside the repository.

### `scripts/branches.sh list`

Prints one row per spec plus one row per work branch without a spec:

```text
SPEC                          BRANCH                                OWNER       STATUS       DEPENDS ON                  GIT
001-repo-governance-branching feature/001-repo-governance-branching krypton612  In Progress  none                        local
—                             feature/000-constitution-config       —           —            —                           local  (no spec)
```

- `GIT` ∈ `local`, `remote`, `local+remote`, `missing`, `merged` (merged into `develop`).
- Warnings (stderr): branch without spec, spec without branch while `In Progress`, chain depth > 2,
  missing optional fields.
- Exit: `0` OK (warnings allowed); `2` cycle detected or malformed required field.

### `scripts/branches.sh check <branch>`

Pre-integration check into `develop`. Fails (exit `1`) if any of:

- branch name does not match the naming regex;
- branch has a spec whose `Owner` or `Status` is missing, or `Status` is not `Ready`;
- any parent in `Depends on` is not `Integrated` *and* not merged into `develop` (FR-016);
- a dependency cycle involves the branch (exit `2`).

Always prints (exit unaffected):

- changed paths vs `develop` that are **shared** → `APPROVAL REQUIRED (FR-021)`;
- changed paths that are **protected** → `PROTECTED PATH — Builder or written approval required`.

### `scripts/branches.sh stack-ok <spec-id>`

Exit `0` if every non-integrated parent of `<spec-id>` has `Contract: Approved (...)`; otherwise
exit `1` and name the parents without an approved contract (FR-015).

## `.githooks/pre-commit`

- Activation (once per clone): `git config core.hooksPath .githooks`.
- Refuses the commit (exit `1`) when the current branch is `develop` or `master`, printing the
  branch rule. Merge commits created by `git merge` are not affected (pre-commit does not run for
  them), so integrations still work.
- Bypass (`--no-verify`) is forbidden by CLAUDE.md §15 except for the documented promotion step,
  which uses `--ff-only` and creates no commit.
