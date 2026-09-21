#!/usr/bin/env bash
# Architectural gates, enforced in CI and runnable locally with:  ./scripts/check-gates.sh
#
# Two things this script has to get right, both learned the hard way:
#   1. Grep *code*, not prose — the plans and KDoc name these forbidden constructs to explain why
#      they are forbidden, and a naive match fails on its own documentation.
#   2. Never pass by accident. An empty path list makes grep read stdin and report nothing, which
#      looks identical to success. Every list is checked before it is used.
#
# Written for bash 3.2, which is what macOS ships — no mapfile, no associative arrays.
set -uo pipefail

FAILED=0

# Discovered rather than listed, so a new feature module is covered the day it lands.
MODULES="core composeApp features"
SRC=$(find $MODULES -maxdepth 3 -type d -name src 2>/dev/null | tr '\n' ' ')
COMMON=$(find $MODULES -maxdepth 4 -type d -name commonMain 2>/dev/null | tr '\n' ' ')
PROD=$(find $MODULES -maxdepth 4 -type d \( -name commonMain -o -name desktopMain -o -name androidMain -o -name jvmCommonMain \) 2>/dev/null | tr '\n' ' ')
DOMAIN=$(find $MODULES -type d -path "*commonMain*/domain" 2>/dev/null | tr '\n' ' ')

require_paths() {
  if [ -z "$(echo "$2" | tr -d '[:space:]')" ]; then
    echo "FAIL: $1 — no source paths matched, so this gate would pass vacuously"
    FAILED=1
    return 1
  fi
  return 0
}

code_grep() {
  pattern="$1"; shift
  grep -rn --include="*.kt" "$pattern" "$@" 2>/dev/null | grep -vE ':[0-9]+:[[:space:]]*(//|\*|/\*)' || true
}

gate() {
  label="$1"; hits="$2"; advice="$3"
  if [ -n "$hits" ]; then
    echo "FAIL: $label"
    echo "$hits" | sed 's/^/      /'
    echo "      -> $advice"
    FAILED=1
  else
    echo "pass: $label"
  fi
}

if require_paths "destructive migration (KD-002)" "$SRC"; then
  gate "no destructive migration (KD-002)" \
       "$(code_grep 'fallbackToDestructiveMigration' $SRC)" \
       "the local database is the source of truth; ship a tested Migration instead"

  gate "no println outside the logger (ADR-029)" \
       "$(code_grep 'println(' $SRC | grep -v 'DesktopPlatformProvider.kt' || true)" \
       "use IPlatformProvider.log()"

  # Android's equivalent, gated from the day the target landed rather than after the first leak.
  gate "no android.util.Log outside the logger (ADR-029)" \
       "$(code_grep 'Log\.\(d\|i\|w\|e\|v\)(' $SRC | grep -v 'AndroidPlatformProvider.kt' || true)" \
       "use IPlatformProvider.log()"
fi

if require_paths "commonMain purity (KD-004)" "$COMMON"; then
  gate "commonMain free of java.* and android.* (KD-004)" \
       "$(code_grep '^import \(java\|android\)\.' $COMMON)" \
       "commonMain must compile for Android too, from Phase 6"
fi

if require_paths "no runBlocking" "$PROD"; then
  gate "no runBlocking in production code" \
       "$(code_grep 'runBlocking' $PROD)" \
       "use a suspend function or an injected CoroutineScope"
fi

# A credential in a log survives backups, support bundles and screenshares.
if require_paths "no credential logging" "$PROD"; then
  gate "no credential reaches the logger" \
       "$(code_grep 'log(.*\(secret\|password\|pin\|Pin\|Secret\|Password\)' $PROD)" \
       "never log a credential, at any level, masked or not (ADR-029, KD-auth)"
fi

if require_paths "domain purity (ADR-005)" "$DOMAIN"; then
  gate "domain has zero framework imports (ADR-005)" \
       "$(code_grep '^import' $DOMAIN | grep -E 'androidx\.|compose|ktor|koin' || true)" \
       "the domain layer is pure Kotlin"
fi

exit $FAILED
