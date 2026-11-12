# Data Model: Repository Architecture and Development Governance

Entities are stored as Markdown header fields and Git refs; nothing is persisted elsewhere.

## Work Branch

| Field | Source | Rules |
|-------|--------|-------|
| name | Git ref | `<type>/<NNN>-<short-name>`; `type` ∈ {feature, fix, docs, chore, hotfix}; regex `^(feature\|fix\|docs\|chore\|hotfix)/[0-9]{3}-[a-z0-9-]+$` |
| number (`NNN`) | name | equals the spec directory number when a spec exists |
| spec | `specs/NNN-*/spec.md` | exactly one per significant branch (FR-009) |
| owner | spec header `**Owner**` | non-empty, a person (FR-009) |
| status | spec header `**Status**` | see state machine below |
| parents | spec header `**Depends on**` | list of spec ids; empty = independent |

### Status state machine

```text
Draft ──clarified/planned──▶ In Progress ──gates pass──▶ Ready ──integrated──▶ Integrated
  │                              │                         │
  └────────── Abandoned ◀────────┴─────────────────────────┘
```

- `Ready` → `Integrated` only if every parent is `Integrated` (FR-016).
- A parent moving to `Abandoned` flags all its dependents (edge case).
- After `Integrated`, the Git branch is deleted (FR-023); the spec remains as history.

## Dependency

| Field | Rules |
|-------|-------|
| child | spec id of the dependent |
| parent | spec id it requires |
| mode | `stacked` (branch created from parent) or `waiting` (not started until parent integrated) |

- `stacked` allowed only when parent's Contract is `Approved` (FR-015).
- No cycles (FR-018). Depth > 2 → warning, needs agreement of both developers.
- When parent becomes `Integrated`, the child's entry is marked `(integrated)` and the child merges
  `develop` (FR-019).

## Contract

| Field | Rules |
|-------|-------|
| owner spec | the parent spec |
| content | `## Contract` section: interfaces, models, data shapes consumed by dependents |
| status | `Draft` or `Approved (YYYY-MM-DD, <approver>)`; any change → back to `Draft` (FR-015a) |

## Repository Area

| Field | Rules |
|-------|-------|
| path pattern | glob relative to repo root |
| class | `authorized` \| `shared` \| `protected` — exactly one (FR-002) |
| action | authorized: none; shared: check history + coordinate, approval at integration; protected: Builder or written approval in plan |

Initial classification (authoritative copy goes in CLAUDE.md §2):

| Pattern | Class |
|---------|-------|
| `shared/src/commonMain/**`, `shared/src/commonTest/**`, `shared/src/androidMain/**`, `shared/src/androidHostTest/**`, `androidApp/src/**` | authorized |
| `*.gradle.kts`, `gradle/**`, `gradle.properties`, `CLAUDE.md`, `.specify/**`, `.claude/**`, `scripts/**`, `.githooks/**`, `**/sqldelight/**`, `**/designsystem/**`, `**/navigation/**`, `**/domain/model/**`, `**/domain/repository/**` | shared |
| `iosApp/**`, `desktopApp/**`, `shared/src/iosMain/**`, `shared/src/iosTest/**`, `shared/src/jvmMain/**`, `shared/src/jvmTest/**`, `**/build/**` | protected |
| everything else (e.g. `specs/**`, `docs/**`) | authorized |

Rule precedence: `protected` > `shared` > `authorized` (e.g. `desktopApp/build.gradle.kts` is
protected).

## Conflict Report

Fields fixed by CLAUDE.md §3: current branch, target branch, conflicting files, nature, potential
impact, recommended action.
