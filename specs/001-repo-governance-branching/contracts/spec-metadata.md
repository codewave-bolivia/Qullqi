# Contract: Specification Metadata Header

Every `specs/NNN-short-name/spec.md` MUST contain these header lines (one per line, anywhere
before the first `##` heading). `scripts/branches.sh` parses them literally.

```markdown
**Feature Branch**: `feature/NNN-short-name`
**Owner**: <person>
**Status**: Draft | In Progress | Ready | Integrated | Abandoned
**Depends on**: none
**Contract**: none
```

## Fields

| Field | Required | Format | Notes |
|-------|----------|--------|-------|
| Feature Branch | yes | backticked branch name matching the naming regex | `type` may differ from `feature` |
| Owner | yes | free text, non-empty, not `TODO` | FR-009 |
| Status | yes | one of the five values | see data-model state machine |
| Depends on | yes | `none`, or comma-separated `NNN-short-name [stacked\|waiting]`, optionally suffixed `(integrated)` | e.g. `002-local-data-layer stacked, 003-design-tokens waiting (integrated)` |
| Contract | yes | `none`, `Draft`, or `Approved (YYYY-MM-DD, <approver>)` | only meaningful when other specs depend on this one |

## Contract section

A spec whose `Contract` is not `none` MUST include a `## Contract` section listing what dependents
may rely on (interfaces, models, data shapes). Editing that section MUST reset `Contract` to
`Draft` until re-approved.

## Compatibility

- Specs created before this contract (only 001) are updated by this feature.
- A missing `Depends on` or `Contract` line is reported by `branches.sh` as a warning and treated
  as `none`; a missing `Owner` or `Status` is an error.
