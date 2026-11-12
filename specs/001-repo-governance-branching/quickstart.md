# Quickstart: Validate Repository Governance

## Prerequisites

- Git ≥ 2.30, Bash.
- Repository cloned; hooks activated: `git config core.hooksPath .githooks`.

## 1. Automated check

```bash
scripts/test-branches.sh
```

Expected: every scenario prints `ok`, final line `ALL PASSED`, exit `0`. It runs in a temporary
repository and never touches the real one.

Covered scenarios (map to spec):

| Scenario | Expected | Spec |
|----------|----------|------|
| commit on `develop` | refused by hook | FR-007 |
| `check` on a `Ready` independent branch | exit 0 | US4 |
| `check` on a child whose parent is not integrated | exit 1, names parent | FR-016, US2-2 |
| `stack-ok` with parent contract `Draft` | exit 1 | FR-015 |
| `stack-ok` with parent contract `Approved` | exit 0 | FR-015 |
| A depends on B, B depends on A | exit 2 | FR-018 |
| branch touching `gradle/libs.versions.toml` | `APPROVAL REQUIRED` printed | FR-021 |
| branch touching `iosApp/` | `PROTECTED PATH` printed | FR-004 |
| bad branch name `wallet-screen` | exit 1 | FR-008 |

## 2. Manual walkthrough on the real repository

1. `scripts/branches.sh list` → spec 001 appears with owner `krypton612`; `feature/000-...`
   appears as "no spec". (SC-003)
2. `git switch develop && git commit --allow-empty -m test` → refused. (FR-007)
3. Classify the 10 sample changes below with CLAUDE.md §2 only; compare to the answers. (SC-005)

| Change | Expected class |
|--------|----------------|
| New screen in `shared/src/commonMain/.../ui/` | authorized |
| New table in `shared/src/commonMain/sqldelight/` | shared |
| Edit `iosApp/iosApp/ContentView.swift` | protected |
| Add library to `gradle/libs.versions.toml` | shared |
| Edit `shared/src/androidMain/.../Platform.android.kt` | authorized |
| Edit `shared/src/jvmMain/.../Platform.jvm.kt` | protected |
| Change theme colors in `designsystem/` | shared |
| Edit `desktopApp/build.gradle.kts` | protected |
| New test in `shared/src/commonTest/` | authorized |
| Edit `.specify/memory/constitution.md` | shared |

4. Time a fresh start (SC-002): from a clean clone, follow CLAUDE.md §15 "Start a branch" and
   reach a correctly named branch in < 5 minutes.
