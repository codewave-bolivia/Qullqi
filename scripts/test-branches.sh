#!/usr/bin/env bash
# Self-check for scripts/branches.sh and .githooks/pre-commit (spec 001).
# Runs in a throw-away repository under mktemp; never touches the real repository.
set -euo pipefail

REAL=$(cd "$(dirname "$0")/.." && pwd)
BR="$REAL/scripts/branches.sh"
HOOKS="$REAL/.githooks"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cd "$TMP"

FAILED=0
git init -q -b master
git config user.name tester
git config user.email tester@example.invalid
git commit -q --allow-empty -m init
git switch -q -c develop

# write_spec <id> <owner> <status> <depends> <contract>
# Specs stay untracked so they are visible from every checkout.
write_spec() {
  mkdir -p "specs/$1"
  local num=${1%%-*}
  cat >"specs/$1/spec.md" <<EOF
# Feature Specification: $1

**Feature Branch**: \`feature/$1\`

**Status**: $3

**Owner**: $2

**Depends on**: $4

**Contract**: $5

## User Scenarios & Testing
EOF
  : "$num"
}

# make_branch <name> [file...]: branch from develop with one commit touching the files
make_branch() {
  local b=$1; shift
  git switch -q -c "$b" develop
  local f
  for f in "$@"; do mkdir -p "$(dirname "$f")"; echo "$b" >"$f"; git add "$f"; done
  git commit -q --allow-empty -m "$b"
  git switch -q develop
}

run() { OUT=$("$@" 2>&1) && CODE=0 || CODE=$?; }

expect_exit() { # expect_exit <name> <code> <cmd...>
  local name=$1 want=$2; shift 2
  run "$@"
  if [[ $CODE == "$want" ]]; then echo "ok $name"; else
    echo "FAIL $name: exit $CODE, want $want"; echo "$OUT" | sed 's/^/    /'; FAILED=1; fi
}

expect_output() { # expect_output <name> <pattern> <cmd...>
  local name=$1 pat=$2; shift 2
  run "$@"
  if grep -q -- "$pat" <<<"$OUT"; then echo "ok $name"; else
    echo "FAIL $name: output lacks '$pat'"; echo "$OUT" | sed 's/^/    /'; FAILED=1; fi
}

# --- US1: start a branch safely -------------------------------------------
expect_exit "hook refuses commit on develop" 1 \
  git -c core.hooksPath="$HOOKS" commit --allow-empty -m direct
git switch -q -c feature/099-scratch
expect_exit "hook allows commit on work branch" 0 \
  git -c core.hooksPath="$HOOKS" commit --allow-empty -m ok
git switch -q develop
git branch -q -D feature/099-scratch
expect_exit "bad branch name rejected" 1 "$BR" check wallet-screen

# --- US4: integrate -----------------------------------------------------------
write_spec 010-indep alice Ready none none
make_branch feature/010-indep shared/src/commonMain/kotlin/A.kt
expect_exit "Ready independent branch passes" 0 "$BR" check feature/010-indep
write_spec 010-indep alice "In Progress" none none
expect_output "not Ready is rejected (message)" "must be Ready" "$BR" check feature/010-indep
expect_exit "not Ready is rejected (exit)" 1 "$BR" check feature/010-indep
write_spec 010-indep alice Ready none none
expect_exit "feature branch without spec rejected" 1 "$BR" check feature/015-nospec

# --- US2: dependent branches --------------------------------------------------
write_spec 011-parent bob "In Progress" none Draft
write_spec 012-child alice Ready "011-parent stacked" none
make_branch feature/011-parent shared/src/commonMain/kotlin/P.kt
make_branch feature/012-child shared/src/commonMain/kotlin/C.kt
expect_exit "child with unintegrated parent rejected" 1 "$BR" check feature/012-child
expect_output "child rejection names parent" "011-parent" "$BR" check feature/012-child
expect_exit "stack-ok refuses Draft contract" 1 "$BR" stack-ok 012
write_spec 011-parent bob "In Progress" none "Approved (2026-11-12, tester)"
expect_exit "stack-ok accepts Approved contract" 0 "$BR" stack-ok 012-child
git merge -q --no-ff feature/011-parent -m "merge parent"
expect_exit "child passes once parent merged into develop" 0 "$BR" check feature/012-child
expect_output "list shows owner and status" "alice.*Ready" "$BR" list
expect_output "list shows dependencies" "011-parent stacked" "$BR" list

# --- US3: repository areas ----------------------------------------------------
write_spec 013-gradle alice Ready none none
make_branch feature/013-gradle gradle/libs.versions.toml
expect_output "shared path needs approval" "APPROVAL REQUIRED" "$BR" check feature/013-gradle
expect_exit "shared path does not fail check" 0 "$BR" check feature/013-gradle
write_spec 014-ios alice Ready none none
make_branch feature/014-ios iosApp/x.swift
expect_output "protected path flagged" "PROTECTED PATH" "$BR" check feature/014-ios

# --- Cycles (last: a cycle makes list fail globally) ---------------------------
write_spec 020-a alice Draft "021-b waiting" none
write_spec 021-b alice Draft "020-a waiting" none
expect_exit "dependency cycle detected" 2 "$BR" list

if (( FAILED )); then echo "SOME TESTS FAILED"; exit 1; fi
echo "ALL PASSED"
