#!/usr/bin/env bash
# Architectural gates, enforced in CI and runnable locally with:  ./scripts/check-gates.sh
#
# Each gate greps *code*, not prose: the plans and KDoc discuss these forbidden constructs by name,
# so a naive match would fail on documentation explaining why they are forbidden.
set -uo pipefail

FAILED=0
SRC=(core/src composeApp/src)
PROD=(core/src/commonMain core/src/desktopMain composeApp/src/commonMain composeApp/src/desktopMain)

# Drop comment lines (leading //, *, /*) so KDoc naming a banned construct does not trip the gate.
code_grep() {
  grep -rn --include="*.kt" "$1" "${@:2}" 2>/dev/null | grep -vE ':[0-9]+:\s*(//|\*|/\*)' || true
}

gate() {
  local label="$1" hits="$2" advice="$3"
  if [ -n "$hits" ]; then
    echo "FAIL: $label"
    echo "$hits" | sed 's/^/      /'
    echo "      -> $advice"
    FAILED=1
  else
    echo "pass: $label"
  fi
}

gate "no destructive migration (KD-002)" \
     "$(code_grep 'fallbackToDestructiveMigration' "${SRC[@]}")" \
     "the local database is the source of truth; ship a tested Migration instead"

gate "no println outside the logger (ADR-029)" \
     "$(code_grep 'println(' "${SRC[@]}" | grep -v 'DesktopPlatformProvider.kt' || true)" \
     "use IPlatformProvider.log()"

gate "commonMain free of java.* and android.* (KD-004)" \
     "$(code_grep '^import \(java\|android\)\.' core/src/commonMain composeApp/src/commonMain)" \
     "commonMain must compile for Android too, from Phase 6"

gate "no runBlocking in production code" \
     "$(code_grep 'runBlocking' "${PROD[@]}")" \
     "use a suspend function or an injected CoroutineScope"

gate "domain has zero framework imports (ADR-005)" \
     "$(code_grep '^import' core/src/commonMain/kotlin/com/alsoug/keswa/core/domain | grep -E 'androidx\.|compose|ktor|koin' || true)" \
     "the domain layer is pure Kotlin"

exit $FAILED
