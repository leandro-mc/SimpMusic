#!/usr/bin/env bash
# Runs ./gradlew with the given arguments (from the repository root) and, when it fails, republishes the part of its
# output that explains the failure as a GitHub annotation ("::error::").
#
# Why: annotations can be read through the GitHub API, while the raw job log is served from a storage host that some
# tools (and the AI agent that maintains this fork) cannot reach. Behaviour is otherwise identical to ./gradlew:
# same output, same exit code. Beatland's own file (D-042).
#
# Usage: bash scripts/beatland/ci-gradle.sh :composeApp:mpvSetupAll -PisFullBuild=false ...

set -o pipefail

LOG="$(mktemp)"
./gradlew --console=plain "$@" 2>&1 | tee "$LOG"
status=${PIPESTATUS[0]}

if [ "$status" -ne 0 ]; then
  # Prefer the block Gradle itself calls out; fall back to the last lines (e.g. when the wrapper could not start).
  summary="$(awk '/^\* What went wrong:/{f=1} f{print} /^\* Try:/{exit}' "$LOG" | head -n 40)"
  if [ -z "$summary" ]; then
    summary="$(tail -n 40 "$LOG")"
  fi
  summary="./gradlew $*"$'\n'"$summary"
  # Workflow commands need %, CR and LF escaped inside the message.
  summary="${summary//'%'/'%25'}"
  summary="${summary//$'\r'/'%0D'}"
  summary="${summary//$'\n'/'%0A'}"
  echo "::error title=Gradle failed::${summary}"
fi

exit "$status"
