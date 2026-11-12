#!/usr/bin/env bash
# Work branches and spec dependencies (spec 001, contracts/branches-cli.md).
#
#   scripts/branches.sh list               all specs and work branches
#   scripts/branches.sh check <branch>     may <branch> be integrated into develop?
#   scripts/branches.sh stack-ok <spec>    may <spec> stack on its unintegrated parents?
#
# READ-ONLY: never commits, merges, checks out, resets, stashes, fetches or pushes.
# Specs are read from the current working tree. Requires Bash >= 4.
set -euo pipefail

ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"

INTEGRATION=develop
NAME_RE='^(feature|fix|docs|chore|hotfix)/[0-9]{3}-[a-z0-9-]+$'
STATUSES='Draft|In Progress|Ready|Integrated|Abandoned'
APPROVED_RE='^Approved \([0-9]{4}-[0-9]{2}-[0-9]{2}, .+\)$'
MAX_DEPTH=2

declare -A BRANCH OWNER STATUS DEPS CONTRACT COLOR DEPTH
SPECS=()
SPEC_ERRORS=0
CYCLE=""

usage() {
  sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 64
}
warn() { echo "warning: $*" >&2; }

# field <spec.md> <name>: value of "**<name>**: value" before the first "## " heading
field() {
  awk -v k="**$2**: " '/^## /{exit} index($0, k) == 1 {print substr($0, length(k) + 1); exit}' "$1"
}

load_specs() {
  local f id v
  for f in specs/[0-9][0-9][0-9]-*/spec.md; do
    [[ -e $f ]] || continue
    id=${f#specs/}
    id=${id%/spec.md}
    SPECS+=("$id")
    v=$(field "$f" "Feature Branch")
    BRANCH[$id]=${v//\`/}
    OWNER[$id]=$(field "$f" Owner)
    STATUS[$id]=$(field "$f" Status)
    DEPS[$id]=$(field "$f" "Depends on")
    CONTRACT[$id]=$(field "$f" Contract)
    if [[ -z ${OWNER[$id]} || ${OWNER[$id]} == TODO* ]]; then
      echo "error: $id: Owner is missing" >&2
      SPEC_ERRORS=1
    fi
    if [[ ! ${STATUS[$id]} =~ ^($STATUSES)$ ]]; then
      echo "error: $id: Status '${STATUS[$id]}' is not one of: $STATUSES" >&2
      SPEC_ERRORS=1
    fi
    if [[ -z ${DEPS[$id]} ]]; then warn "$id: no 'Depends on', treated as none"; DEPS[$id]=none; fi
    if [[ -z ${CONTRACT[$id]} ]]; then warn "$id: no 'Contract', treated as none"; CONTRACT[$id]=none; fi
  done
}

# resolve <id-or-number>: full spec id
resolve() {
  local s
  for s in "${SPECS[@]}"; do
    if [[ $s == "$1" || $s == "$1"-* ]]; then echo "$s"; return 0; fi
  done
  return 1
}

# deps_of <spec>: one "<parent> <stacked|waiting> <0|1 integrated>" line per dependency
deps_of() {
  local raw=${DEPS[$1]:-none} item p mode rest integ
  [[ $raw == none ]] && return 0
  IFS=',' read -ra items <<<"$raw"
  for item in "${items[@]}"; do
    read -r p mode rest <<<"$item" || true
    [[ -n ${p:-} ]] || continue
    integ=0
    [[ "${mode:-} ${rest:-}" == *"(integrated)"* ]] && integ=1
    [[ ${mode:-} == stacked || ${mode:-} == waiting ]] || mode=waiting
    echo "$p $mode $integ"
  done
}

has_ref() { git rev-parse -q --verify "refs/heads/$1" >/dev/null; }

# branch_merged <branch>: contained in develop and not merely identical to it (a fresh branch)
branch_merged() {
  [[ -n $1 ]] && has_ref "$1" && has_ref "$INTEGRATION" || return 1
  [[ $(git rev-parse "$1") != $(git rev-parse "$INTEGRATION") ]] || return 1
  git merge-base --is-ancestor "$1" "$INTEGRATION"
}

# parent_integrated <spec> <integrated-flag>
parent_integrated() {
  [[ $2 == 1 || ${STATUS[$1]} == Integrated ]] || branch_merged "${BRANCH[$1]}"
}

visit() {
  local id=$1 p mode integ d max=0
  COLOR[$id]=1
  while read -r p mode integ; do
    p=$(resolve "$p") || { warn "$id: depends on unknown spec '$p'"; continue; }
    if [[ ${COLOR[$p]:-0} == 1 ]]; then CYCLE="$id -> $p"; continue; fi
    [[ ${COLOR[$p]:-0} == 2 ]] || visit "$p"
    d=$((${DEPTH[$p]:-0} + 1))
    if ((d > max)); then max=$d; fi
  done < <(deps_of "$id")
  DEPTH[$id]=$max
  COLOR[$id]=2
}

check_graph() {
  local id
  for id in "${SPECS[@]}"; do
    [[ ${COLOR[$id]:-0} != 0 ]] || visit "$id"
  done
  if [[ -n $CYCLE ]]; then
    echo "error: dependency cycle: $CYCLE (FR-018)" >&2
    return 0
  fi
  for id in "${SPECS[@]}"; do
    if ((${DEPTH[$id]} > MAX_DEPTH)); then
      warn "$id: dependency chain depth ${DEPTH[$id]} > $MAX_DEPTH requires agreement of both developers"
    fi
  done
}

# classify <path>: protected > shared > authorized (CLAUDE.md §2 "Repository areas")
classify() {
  case $1 in
    iosApp/* | desktopApp/* | shared/src/iosMain/* | shared/src/iosTest/* | \
      shared/src/jvmMain/* | shared/src/jvmTest/* | build/* | */build/*)
      echo protected ;;
    *.gradle.kts | gradle/* | gradle.properties | CLAUDE.md | .specify/* | .claude/* | \
      scripts/* | .githooks/* | .github/* | */sqldelight/* | */designsystem/* | */navigation/* | \
      */domain/model/* | */domain/repository/*)
      echo shared ;;
    *) echo authorized ;;
  esac
}

git_state() {
  local b=$1 l=0 r=0
  if [[ -z $b ]]; then echo missing; return; fi
  if branch_merged "$b"; then echo merged; return; fi
  has_ref "$b" && l=1
  [[ -n $(git for-each-ref --format='%(refname)' "refs/remotes/*/$b") ]] && r=1
  case $l$r in
    11) echo local+remote ;;
    10) echo local ;;
    01) echo remote ;;
    *) echo missing ;;
  esac
}

cmd_list() {
  local fmt='%-32s %-40s %-14s %-12s %-30s %s\n' id b st
  local -A spec_branch=()
  printf "$fmt" SPEC BRANCH OWNER STATUS "DEPENDS ON" GIT
  for id in "${SPECS[@]}"; do
    b=${BRANCH[$id]}
    [[ -n $b ]] && spec_branch[$b]=1
    st=$(git_state "$b")
    printf "$fmt" "$id" "${b:-—}" "${OWNER[$id]:-—}" "${STATUS[$id]:-—}" "${DEPS[$id]}" "$st"
    if [[ ${STATUS[$id]} == "In Progress" && $st == missing ]]; then
      warn "$id is In Progress but branch '${b:-?}' does not exist"
    fi
  done
  while read -r b; do
    [[ $b == "$INTEGRATION" || $b == master || -n ${spec_branch[$b]:-} ]] && continue
    printf "$fmt" "—" "$b" "—" "—" "—" "local  (no spec)"
    warn "branch '$b' has no spec"
  done < <(git for-each-ref --format='%(refname:short)' refs/heads)
  if [[ -n $CYCLE ]]; then exit 2; fi
  if ((SPEC_ERRORS)); then exit 2; fi
}

cmd_check() {
  local b=$1 fails=0 id="" num p mode integ path cls
  if [[ ! $b =~ $NAME_RE ]]; then
    echo "FAIL: '$b' does not match <type>/<NNN>-<short-name>, type: feature|fix|docs|chore|hotfix (FR-008)"
    fails=1
  else
    num=${b#*/}
    num=${num:0:3}
    id=$(resolve "$num") || id=""
  fi

  if [[ -n $id ]]; then
    if [[ -z ${OWNER[$id]} || ${OWNER[$id]} == TODO* ]]; then
      echo "FAIL: spec $id has no Owner (FR-009)"; fails=1
    fi
    if [[ ${STATUS[$id]} != Ready ]]; then
      echo "FAIL: spec $id Status is '${STATUS[$id]}'; it must be Ready to integrate"; fails=1
    fi
    while read -r p mode integ; do
      local rp
      rp=$(resolve "$p") || { echo "FAIL: parent '$p' has no spec"; fails=1; continue; }
      if ! parent_integrated "$rp" "$integ"; then
        echo "FAIL: parent $rp is not integrated into $INTEGRATION; integrate it first (FR-016)"
        fails=1
      fi
    done < <(deps_of "$id")
    if [[ -n $CYCLE && $CYCLE == *"$id"* ]]; then exit 2; fi
  elif [[ $b == feature/* && $b =~ $NAME_RE ]]; then
    echo "FAIL: feature branch '$b' has no spec specs/$num-* (FR-009)"
    fails=1
  fi

  if has_ref "$b" && has_ref "$INTEGRATION"; then
    while read -r path; do
      [[ -n $path ]] || continue
      cls=$(classify "$path")
      case $cls in
        shared) echo "APPROVAL REQUIRED (FR-021): $path" ;;
        protected) echo "PROTECTED PATH — Builder or written approval required: $path" ;;
      esac
    done < <(git diff --name-only "$INTEGRATION...$b")
  else
    warn "branch '$b' or '$INTEGRATION' not found locally; changed paths not checked"
  fi

  if ((fails)); then exit 1; fi
  echo "OK: $b may be integrated into $INTEGRATION (after any approval listed above)"
}

cmd_stack_ok() {
  local id p mode integ rp blocked=0
  id=$(resolve "$1") || { echo "FAIL: unknown spec '$1'"; exit 1; }
  while read -r p mode integ; do
    rp=$(resolve "$p") || { echo "FAIL: parent '$p' has no spec"; blocked=1; continue; }
    parent_integrated "$rp" "$integ" && continue
    if [[ ! ${CONTRACT[$rp]} =~ $APPROVED_RE ]]; then
      echo "FAIL: parent $rp contract is '${CONTRACT[$rp]}'; must be 'Approved (YYYY-MM-DD, <approver>)' to stack (FR-015)"
      blocked=1
    fi
  done < <(deps_of "$id")
  if ((blocked)); then exit 1; fi
  echo "OK: $id may stack on its unintegrated parents"
}

case ${1:-} in
  list) [[ $# -eq 1 ]] || usage; load_specs; check_graph; cmd_list ;;
  check) [[ $# -eq 2 ]] || usage; load_specs; check_graph; cmd_check "$2" ;;
  stack-ok) [[ $# -eq 2 ]] || usage; load_specs; check_graph; cmd_stack_ok "$2" ;;
  *) usage ;;
esac
