# Phase 0 Plan — Project Scaffold

> **Status: 📝 DRAFT — awaiting review**
> Depends on: `keswa-conventions-and-deviations.md`
> Blocked by: nothing. **This phase can start today.**
> Estimated: 1–2 days

## Goal

A `:composeApp` desktop window that launches, wired to an empty `:core`, building green in CI.
No features, no database, no UI beyond a placeholder. The point is that every later phase has a
correct foundation to land on.

## Why desktop-only at Phase 0

Adding `androidTarget()` now costs an Android SDK dependency, a manifest, AGP config and a second
KSP configuration — for a target not used until Phase 6. Starting `jvm("desktop")`-only keeps the
build graph small while the conventions settle. Per **KD-004**, `commonMain` is written from day one
as if Android already existed (no `java.*`, no `android.*`), so adding the target later is additive.

---

## Deliverables

### [NEW] `settings.gradle.kts`

```kotlin
rootProject.name = "keswa"

include(":composeApp")
include(":core")
```

Features are added per-phase. Following the Cashi hierarchy exactly: `composeApp → features:* → core`.

### [NEW] `gradle/libs.versions.toml`

Version catalog seeded from `kmp_cashimobile`'s, so the two projects stay in step. Carried over
verbatim where applicable:

| Library | Version | Note |
|---|---|---|
| kotlin | 2.3.0 | |
| compose-multiplatform | 1.9.3 | Cashi pins this deliberately — 1.10.0 needs a lifecycle alpha. Keep the pin and the comment. |
| koin | 4.1.1 | |
| ktor | 3.4.0 | `ktor-network` added in Phase 3 for printers |
| room | 2.8.4 | |
| sqlite (bundled) | 2.6.2 | Works on JVM — no change needed for desktop |
| ksp | 2.3.0 | |
| kotlinx-coroutines | 1.10.2 | |
| kotlinx-serialization | 1.10.0 | |
| kotlinx-datetime | 0.7.1 | |
| navigation-compose | 2.9.1 | |
| lifecycle-compose | 2.9.6 | |

Dropped from the Cashi catalog (not applicable): Firebase, camerax, camerak, moko-permissions,
coil, nsexceptionkt. **Kept for later:** `zxing-core` and `qrose` — Phase 3 needs them to generate
the receipt QR and the barcode bitmaps, and Cashi has already proven them in common code.

### [NEW] `build.gradle.kts` (root) + `core/build.gradle.kts` + `composeApp/build.gradle.kts`

`composeApp` target block:

```kotlin
kotlin {
    jvm("desktop")
    // androidTarget() added in Phase 6 — see KD-004

    sourceSets {
        commonMain.dependencies { implementation(projects.core) /* … */ }
        val desktopMain by getting {
            dependencies { implementation(compose.desktop.currentOs) }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.alsoug.keswa.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Keswa"
        }
    }
}
```

Package root: `com.alsoug.keswa` — mirroring `com.alsoug.cashi`.

### [COPY] Files lifted verbatim from `kmp_cashimobile`

These are solved problems. Copying them costs an hour and keeps the two codebases reviewable by the
same people.

| Source | Change on copy |
|---|---|
| `core/error/Error.kt` + `ServerMessageSanitizer.kt` | none |
| `core/error/SafeApiCall.kt` | none |
| `core/coroutines/DispatcherProvider.kt` + `Cancellation.kt` | none |
| `core/database/RoomDatabaseFactory.kt` | **drop the `fallbackToDestructiveMigration` line** (KD-002) |
| `.agents/` (rules, context, workflows) | edit Android/iOS references to desktop |

> `Error.kt`'s KDoc carries a constraint worth preserving verbatim: *error messages never contain
> sensitive data*. It applies to Keswa for ETA credentials in Phase 10.

### [NEW] `core/src/commonMain/.../platform/IPlatformProvider.kt`

Must exist before any other code — `println` is a 🔴 blocker (ADR-029) from the first commit, and
Kermit is prohibited too. Trimmed to Keswa's surface:

```kotlin
enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

interface IPlatformProvider {
    val platformName: String
    val platformVersion: String
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}
```

Cashi's version also carries `shareImage`/`saveReceiptImage`/`openAppOrFallback`. Those are mobile
share-sheet concerns with no desktop analogue — omitted rather than stubbed.

### [NEW] `core/src/commonTest/.../coroutines/TestDispatcherProvider.kt`

Per **KD-003**. Cashi's `DispatcherProvider` KDoc references this type but **the file does not exist
in that repo** — which is why the hardcoded-dispatcher habit persisted. Keswa creates it up front so
the correct pattern is the path of least resistance from the first test.

### [NEW] `composeApp/src/desktopMain/.../DesktopPlatformProvider.kt`, `Main.kt`

`Desktop*` naming per KD-004, extending Cashi's `Android*`/`Ios*` convention.

### [NEW] `composeApp/src/commonMain/.../di/AppBootstrap.kt`

```kotlin
fun initKoin() {
    startKoin { modules(coreModule, platformModule) }
}
```

Feature modules are appended here as each phase lands — same as Cashi's `AppBootstrap.kt`.

### [NEW] `docs/` — carried over, not rewritten

- `docs/CODE_GUIDELINES.md` → a short pointer to the Cashi document plus `KD-001…006`, rather than
  a 615-line fork that will drift. **One source of truth.**
- `docs/adr/` → the six KD decisions in Cashi's ADR format, plus the explicit
  **not-applicable list** (ADR-002, 035, 036, 041, 033-envelope). Numbering restarts at KD-001 —
  Cashi has two ADR-033s and that collision is not worth inheriting.

### [NEW] CI

Mirror `kmp_cashimobile/docs/CI_GUARDRAILS.md`. Minimum gate for Phase 0:

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew allTests
```

---

## Best-Practice Notes

**Deliberately NOT in this phase:**

- **No `build-logic/` convention plugins yet.** `cashi-sdk` has them and they're right at that
  scale, but with two modules they are pure overhead. Introduce at Phase 6, when `:features:*`
  modules start repeating the same 40 lines of Gradle. Premature extraction here would lock in a
  shape before we know what repeats.
- **No Room yet** — Phase 1. Phase 0 stays buildable in minutes so the conventions get exercised
  before schema work starts.
- **No navigation graph** — one placeholder screen. `navigation-compose` is in the catalog but
  unused until Phase 2.
- **No `:core` split** into `:core:domain`/`:core:data`. Cashi uses a single `:core` with internal
  packages and it holds fine at 16 features. Matching it costs nothing and keeps the two projects
  navigable by the same people.

**Adjacent smell worth flagging:** `kmp_cashimobile` has five `.claude/worktrees/*` copies of
`RoomDatabaseFactory.kt` showing up in greps. Not Keswa's problem, but worth a `.gitignore` entry
here from the start so the same noise doesn't appear in this repo's searches.

---

## Verification Plan

| # | Check | Command / method |
|---|---|---|
| 1 | Desktop app compiles | `./gradlew :composeApp:compileKotlinDesktop` |
| 2 | App launches, window opens | `./gradlew :composeApp:run` |
| 3 | Koin graph resolves | `./gradlew allTests` — a Koin `checkModules()` test |
| 4 | No `println` | grep gate in CI: `grep -rn "println(" --include="*.kt" core composeApp` returns nothing |
| 5 | Packaging works | `./gradlew :composeApp:packageDmg` produces an installable artifact |

Check 5 matters more than it looks: desktop packaging problems (JVM bundling, signing, icons) are
much cheaper to discover now than at Phase 8 when there's a shop waiting for a till.

## Definition of Done

- [ ] `./gradlew :composeApp:run` opens a window on macOS
- [ ] `./gradlew allTests` green
- [ ] `IPlatformProvider` + `DispatcherProvider` exist and are Koin-registered
- [ ] `docs/adr/` contains KD-001…005
- [ ] CI runs compile + tests + the `println` gate on every push
- [ ] `packageDmg` produces a launchable app
