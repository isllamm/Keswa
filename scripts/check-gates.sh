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
MODULES="core composeApp features server"
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
       "$(code_grep 'println(' $SRC | grep -v 'DesktopPlatformProvider.kt' | grep -v 'ServerPlatformProvider.kt' || true)" \
       "use IPlatformProvider.log()"

  # Android's equivalent, gated from the day the target landed rather than after the first leak.
  gate "no android.util.Log outside the logger (ADR-029)" \
       "$(code_grep 'Log\.\(d\|i\|w\|e\|v\)(' $SRC | grep -v 'AndroidPlatformProvider.kt' || true)" \
       "use IPlatformProvider.log()"

  gate "no Kermit logger (ADR-029)" \
       "$(code_grep 'co\.touchlab\.kermit' $SRC)" \
       "use IPlatformProvider.log()"

  gate "no wildcard imports" \
       "$(code_grep '^import .*\.\*' $SRC)" \
       "use explicit imports rather than wildcard imports"
fi

# An exported schema for a version that has already shipped describes a database that exists in
# a shop. Changing one makes every migration test lie: the fixture it builds is a version that
# never existed, and the real upgrade fails on a live install instead.
#
# This bites by accident — add a column, compile once before bumping the version, and Room
# rewrites the current file in place. Git is the right tool for "this file is history".
#
# Vacuous in CI, where the tree matches HEAD. It earns its keep locally, at the moment somebody
# has just done it.
SCHEMA_DIR="core/schemas/com.alsoug.keswa.core.database.KeswaDatabase"
if [ -d "$SCHEMA_DIR" ]; then
  CURRENT=$(ls "$SCHEMA_DIR" | sed 's/\.json$//' | sort -n | tail -1)
  CHANGED=$(git diff --name-only -- "$SCHEMA_DIR" 2>/dev/null | grep -v "/$CURRENT\.json$" || true)
  gate "shipped schemas are immutable (KD-002)" \
       "$CHANGED" \
       "an exported schema below v$CURRENT has changed; restore it with git checkout -- <file>"
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

  gate "no GlobalScope in production code (ADR-040)" \
       "$(code_grep 'GlobalScope' $PROD)" \
       "use an injected CoroutineScope or viewModelScope instead of GlobalScope"
fi

# A credential in a log survives backups, support bundles and screenshares.
if require_paths "no credential logging" "$PROD"; then
  gate "no credential reaches the logger" \
       "$(code_grep 'log(.*\(secret\|password\|pin\|token\|Pin\|Secret\|Password\|Token\)' $PROD)" \
       "never log a credential, at any level, masked or not (ADR-029, KD-auth, KD-007)"
fi

# Room does not model triggers, so the outbox DDL has to run in two places: the v8 migration for a
# shop that upgrades, and onCreate for a machine installing fresh. Miss one and the app works
# perfectly and syncs nothing — the quietest failure in the project. Both read one generated list,
# and this is what says they still do.
TRIGGER_SOURCES="core/src/commonMain/kotlin/com/alsoug/keswa/core/database"
MISSING_TRIGGERS=""
for file in "$TRIGGER_SOURCES/migrations/Migrations.kt" "$TRIGGER_SOURCES/KeswaDatabaseFactory.kt"; do
  if [ ! -f "$file" ]; then
    MISSING_TRIGGERS="$MISSING_TRIGGERS
$file is missing"
  elif ! grep -q 'SYNC_TRIGGERS' "$file"; then
    MISSING_TRIGGERS="$MISSING_TRIGGERS
$file no longer installs SYNC_TRIGGERS"
  fi
done
gate "the outbox triggers are installed on upgrade and on a fresh database (KD-010)" \
     "$MISSING_TRIGGERS" \
     "both the migration and onCreate must run SYNC_TRIGGERS, or one kind of install syncs nothing"

# A ViewModel that writes a sentence has already chosen a language, several layers from anyone who
# could know which one the shop reads. Effects carry a `Message`; the screen, which is inside the
# theme, turns it into words.
VIEWMODELS=$(find features -path "*commonMain*" -name "*ViewModel.kt" 2>/dev/null | tr '\n' ' ')
if require_paths "no English in a ViewModel message" "$VIEWMODELS"; then
  gate "ViewModels name a message, they do not write one" \
       "$(code_grep 'Show\(Error\|Message\)(\"' $VIEWMODELS)" \
       "emit message { it.someString } instead of a literal — see core/designsystem/Message.kt"
fi

if require_paths "domain purity (ADR-005)" "$DOMAIN"; then
  gate "domain has zero framework imports (ADR-005)" \
       "$(code_grep '^import' $DOMAIN | grep -E 'androidx\.|compose|ktor|koin' || true)" \
       "the domain layer is pure Kotlin"

  gate "domain models avoid Float and Double (KD-001)" \
       "$(code_grep ':[[:space:]]*(Float|Double)' $DOMAIN | grep -vE '(\/\/|\*|\/\*)' || true)" \
       "use Money(Long) piastres for money amounts"

  gate "domain use cases preserve cancellation (ADR-040)" \
       "$(code_grep '\<runCatching[[:space:]]*{' $DOMAIN | grep -v 'Cancellable' || true)" \
       "use runCatchingCancellable in domain use cases to avoid swallowing CancellationException"

  gate "no direct clock calls in domain layer (ADR-015)" \
       "$(code_grep '(System\.currentTimeMillis|Instant\.now|Clock\.System\.now)' $DOMAIN)" \
       "inject a clock or pass timestamps to keep domain deterministic"
fi

if require_paths "injected dispatchers (KD-003)" "$PROD"; then
  gate "no hardcoded Dispatchers in production code (KD-003)" \
       "$(code_grep 'Dispatchers\.' $PROD | grep -v 'DispatcherProvider.kt' || true)" \
       "inject DispatcherProvider instead of referencing Dispatchers directly"
fi

MISSING_PREVIEWS=""
for screen in $(find features -path "*commonMain*/screens/*" -name "*Screen.kt" 2>/dev/null); do
  if ! grep -q '@Preview' "$screen"; then
    MISSING_PREVIEWS="$MISSING_PREVIEWS
$screen has no @Preview"
  fi
done
gate "all feature screens ship Compose previews (ADR-027)" \
     "$MISSING_PREVIEWS" \
     "every screen composable must ship previews for loading, empty/error, and success (ADR-027)"

MISSING_PROOFS=""
for feat in $(find features -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort); do
  if [ -z "$(find "$feat/src/desktopTest" -name "*Proofs.kt" 2>/dev/null)" ]; then
    MISSING_PROOFS="$MISSING_PROOFS
$feat has no visual regression proofs (*Proofs.kt)"
  fi
done
gate "all feature modules provide automated Skia visual regression proofs" \
     "$MISSING_PROOFS" \
     "every feature module must render automated visual snapshots into build/ui-proofs/ via desktopTest"

gate "no mocking frameworks (ADR-019)" \
     "$(grep -rn --include="*.kt" --include="*.kts" -E '(mockk|mockito|io\.mockk|org\.mockito)' $MODULES 2>/dev/null || true)" \
     "use hand-written fakes; mocking frameworks are prohibited"

GRADLE_FEATURE_DEPS=$(grep -rn 'project(":features:' features core 2>/dev/null || true)
gate "no cross-feature or core-to-feature dependencies (CODE_GUIDELINES)" \
     "$GRADLE_FEATURE_DEPS" \
     "features must not depend on other features; core must not depend on features"

exit $FAILED
