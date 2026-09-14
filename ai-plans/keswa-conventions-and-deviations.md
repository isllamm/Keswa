# Keswa — Conventions & Deviations from `kmp_cashimobile`

> **Status: 📝 DRAFT — awaiting review**
> Foundation document. Every phase plan assumes this one is accepted.
> Baseline: `kmp_cashimobile` — `docs/CODE_GUIDELINES.md` v2.0, `docs/adr/` (43 ADRs), and
> `.agents/rules/rules.md` + `.agents/context/conventions.md`, which carry the operative detail.

Keswa inherits the Cashi KMP conventions wholesale. This records **only** what differs, and why.

---

## Part A — Inherited unchanged

| Area | Rule |
|---|---|
| Module hierarchy | `composeApp → features:* → core → (nothing)`. Forbidden: `core → features`, `features:A → features:B`. Multi-word features are **kebab-case** in Gradle, hyphen dropped in the package (`payment-receipt` → `features.paymentreceipt`) |
| Feature layout | `data/ · domain/ · presentation/ · di/` + `README.md`. **Golden module: `features/auth/`** — pattern-match against it |
| Naming | ADR-033 table. Plus two rules `rules.md` still enforces that ADR-033 dropped: `I{Name}` for general interfaces, and `Android{Name}`/`Ios{Name}` for platform impls |
| Domain purity | ADR-005: zero framework imports in `domain/`. ADR-021: ViewModels hold state and delegate — no business logic, no platform types |
| MVI | ADR-008/030: exactly three flows, `replay = 0, extraBufferCapacity = 1` on both SharedFlows. Central `onEvent()`. Dialogs effect-driven via `remember` — never booleans in state. All four sealed types in one `{Feature}UDF.kt` |
| Humble view | ADR-012: composables render state and forward events, nothing else. ADR-011: slots, not config flags. ADR-020: Material 3 only |
| Previews | ADR-027: every component previews **Loading, Error and Success** with mock data |
| Error layering | ADR-032: WebService throws raw DTO → Repository returns `Result<T>` → UseCase maps to a domain sealed class → ViewModel `.fold()`. Uses **`kotlin.Result`** plus the `core.error.Error` sealed hierarchy |
| DI | ADR-015 Koin. Repositories `single`; use cases and ViewModels `factory`. Constructor injection only |
| Concurrency | ADR-040: `supervisorScope + async/awaitAll` for concurrent work — never `coroutineScope + launch`. `viewModelScope.launch` in ViewModels. Never swallow `CancellationException` |
| Logging | ADR-029: `IPlatformProvider.log()` only. `println`, `Log.d` **and Kermit** are prohibited |
| Testing | ADR-019: `commonTest`, `[Class]Test.kt`, backticked sentence names, Given/When/Then, **hand-written fakes — no mocking framework** |
| Docs | ADR-042: default is **no comment**. No section dividers, no "native parity" notes, no LLM-context comments, no bare TODOs |
| Style | 4 spaces, ~120 cols, trailing commas, no wildcard imports |

### Copied verbatim from Cashi at Phase 0

- `core/error/Error.kt` + `SafeApiCall.kt`
- `core/coroutines/DispatcherProvider.kt` + `Cancellation.kt`
- `core/database/RoomDatabaseFactory.kt` — **minus the destructive-migration line** (KD-002)
- `.agents/` wholesale, edited for desktop

### Declared not applicable — greenfield, different backend

Listing these explicitly so their absence reads as a decision, not an oversight:

| ADR | Why N/A |
|---|---|
| ADR-002 Ring Fencing | Strategy for porting an existing native app |
| ADR-035 Migration Simplicity | Ditto — "keep signatures identical to the native app" |
| ADR-036 Strict Logic Parity | Ditto. This ADR is what produced Cashi's `Float` money bugs |
| ADR-041 No HTTP Retry | Justified solely by ADR-036 parity. **Re-decided in Phase 9** — a till on in-store wifi has a different failure profile |
| ADR-033 API Envelope | Specific to Cashi's `{status, code, message}` soft-200 backend |

> ⚠️ Cashi has **two ADR-033s** (naming conventions and api-envelope). Keswa numbers its ADRs
> `KD-001…` from scratch to avoid inheriting the collision.

---

## Part B — Deviations

### KD-001 — Money is `Money(Long)` piastres

**Cashi rule** (ADR-013): display money as `String`; do arithmetic with `BigDecimal` in the domain
layer; never `Float`/`Double`.

**What Cashi actually does:** money is `String` at every layer — entity, domain model, UI model —
with a hand-written pure-Kotlin `formatAsMoney`/`shouldRoundUp` pair in `SubAccountUi.kt` doing
HALF_EVEN formatting on decimal digits. **`BigDecimal` appears nowhere in production code**, and no
bignum library is in the catalog. ADR-013's arithmetic clause is aspirational on an Android+iOS
project, where `java.math.BigDecimal` is unreachable from `commonMain`.

That works for Cashi because it *displays* server-computed amounts. ADR-013 rule 3 even says so
outright: *"the backend should be doing the calculation."*

**A till has no backend.** Keswa computes line totals, multi-line discounts, VAT extraction, margin,
tender splits and change due — on device, offline, in `commonMain`, on every keystroke.

**Rule:**

```kotlin
// core/domain/money/Money.kt
@JvmInline
value class Money private constructor(val piastres: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(piastres + other.piastres)
    operator fun times(quantity: Int) = Money(piastres * quantity)

    companion object {
        val ZERO = Money(0)
        fun ofPiastres(value: Long) = Money(value)
        fun parse(text: String): Money?    // exact decimal parse, never via Double
    }
}
```

- Integer arithmetic cannot drift. This serves ADR-013's **intent** — never lose a piastre — more
  strongly than `String` does, and unlike the BigDecimal clause it is actually implementable.
- Lossy operations get explicit names: `percentage(basisPoints)` rounding HALF_EVEN (parity with
  Cashi's existing semantics), and `allocate(weights)` using **largest-remainder** so split
  discounts always sum back to the whole.
- Room stores `INTEGER` via a `MoneyConverter`.
- `Double`/`Float` on money stays a 🔴 blocker.

**Alternative considered and rejected:** with no iOS target, `java.math.BigDecimal` *is* reachable
from a `jvmCommonMain` intermediate source set shared by desktop and Android, which would satisfy
ADR-013 literally. Rejected because it permanently forecloses any non-JVM target, allocates in the
basket-recalculation hot path, and still requires the same rounding and allocation policy. If a
reviewer insists on the ADR's letter, this is the fallback — the decision is reversible at Phase 1
and nowhere after.

> ⚠️ Do **not** copy Cashi's money handling by example. These are live ADR-013 violations:
> `CalculateFeesUseCase.kt:15` (`amount.toFloat()`), `FormSubmissionPipeline.kt:167,412,413`,
> `PaymentConfirmationViewModel.kt:100,115`, `PaymentFlowData.kt:14` (`val amount: Float`),
> `SubAccountDtos.kt:35` (`val amount: Double`).

### KD-002 — Real migrations. `fallbackToDestructiveMigration` is a blocker

**Cashi rule** (`RoomDatabaseFactory.kt:23`): `.fallbackToDestructiveMigration(dropAllTables = true)`
at schema version 108, with no `Migration` objects registered anywhere. Correct there — the server
owns the truth. Their own KDoc concedes *"replace in production."*

**In Keswa the local database IS the source of truth** for the whole of Phases 0–8. Dropping tables
destroys the shop's sales history, stock ledger and receivables, with nothing to re-fetch from.

ADR-025 is silent on migrations, so this **strengthens** it rather than contradicting it.

**Rule:**
- Every schema change ships a hand-written `Migration`. `exportSchema = true` (Cashi already exports
  to `core/schemas/`).
- `fallbackToDestructiveMigration` is a 🔴 blocker — CI grep gate.
- Every migration gets a test: open schema N, insert a row, migrate, assert it survived intact.
- Keep Cashi's `// <version>: <what changed>` inline comment convention on the `version` line.

### KD-003 — Use the `DispatcherProvider` that already exists

Not a new invention — `core/coroutines/DispatcherProvider.kt` is already in Cashi, and its KDoc
says to inject it *"so tests can substitute `TestDispatcherProvider`."*

The deviation is only that **`CODE_GUIDELINES.md`'s ViewModel template contradicts it**, showing
`viewModelScope.launch(Dispatchers.IO)` hardcoded. That template is what
`cashi_pax/ai-plans/request-money-best-practice-architecture-plan.md` logs as defect **T2** —
hardcoded dispatchers blocked virtual time, tests went flaky, and the "fix" was a `runBlocking` +
`CompletableDeferred` workaround that existed only to work around the design.

**Rule:** inject `DispatcherProvider` everywhere, including ViewModels. `TestDispatcherProvider`
does not exist in Cashi — **Keswa creates it in Phase 0.**

### KD-004 — Targets: `jvm("desktop")` then `androidTarget()`. No iOS

Desktop-first, Android from Phase 6, **no iOS** (architecture plan §2 — MFi blocks the peripherals,
and ETA signing needs PKCS#11).

**Rule:**
- `commonMain` must assume neither platform. Cashi's ban on `android.*` extends to **`java.*`** —
  it would compile on desktop and break Android subtly.
- Platform implementations are named `Desktop{Name}` (matching the `desktopMain` source-set name),
  extending Cashi's `Android{Name}`/`Ios{Name}` convention.
- `expect`/`actual` file suffix convention: `Foo.kt` → `Foo.desktop.kt` / `Foo.android.kt`.
- Room KSP is configured per target — add `kspDesktop` alongside Cashi's `kspAndroid` block.
- `DatabaseBuilder` follows Cashi's pattern: **not `expect`/`actual`**, but a same-named
  `getDatabaseBuilder()` per source set. `DatabaseBuilder.desktop.kt` needs a desktop path strategy
  (`user.home` / XDG / `%APPDATA%`) — `Context.getDatabasePath()` has no analogue.
- Ktor engine: `ktor-client-java` or CIO in place of okhttp/darwin.
- Previews use the JetBrains `org.jetbrains.compose.ui.tooling.preview.Preview`.

### KD-005 — `IReceiptPrinter` / `IBarcodeScanner` follow ADR-018 — interface + DI, not `expect`/`actual`

**Correction to an earlier draft of this document.** Cashi's dominant platform mechanism is an
**interface in `:core` bound through Koin** (~20 of them in `core/platform/`), with `expect`/`actual`
reserved for things that cannot be expressed as an injected object — Compose `Modifier` extensions,
Ktor engine construction, the Room constructor (9 in total).

ADR-018's scope list explicitly names **`PaymentTerminal`** — direct precedent for a printer.

**Rule:**
- `IReceiptPrinter`, `ILabelPrinter`, `IBarcodeScanner`, `IPrinterTransport` live in
  `core/platform/`, bound per-platform in a Koin module.
- **Capability-based, no raw handle leakage** — ADR-018's stated principle is *"exposes what you can
  DO, not what the platform HAS"* and *"no raw context exposure."* So no `javax.print`,
  `android.hardware.usb`, or serial handles crossing the interface.
- **Protocol generation is pure `commonMain`, not platform code.** `EscPos`, `Tspl` and the bitmap
  renderer emit `ByteArray` with zero platform dependencies — ordinary testable Kotlin, covered by
  **golden-byte tests** with no hardware attached.
- `TcpTransport` is written once in `commonMain` with `ktor-network`. Platform code is needed only
  for USB and Bluetooth, deferred to Phase 7+.

### KD-006 — Desktop secure storage needs its own decision (ADR-038 has no JVM answer)

**Cashi rule** (ADR-038): Tier 1 data — tokens, balances, account numbers, PII — goes in
`ISecureTokenStorage`, backed by Android Keystore (AES-256-GCM) or iOS Keychain/Secure Enclave.

**JVM desktop has neither.** This is the hardest of the inherited conflicts and there is no
inheritable answer.

Keswa's Tier 1 set is smaller than Cashi's but real: ETA credentials and e-seal material (Phase 10),
sync tokens (Phase 9), and cashier PINs.

**Rule:** Phase 0 defers this; **Phase 9 must produce a `KD-007` ADR** choosing among macOS Keychain
via JNA, Windows DPAPI/Credential Manager, libsecret on Linux, or a documented passphrase-derived
key. Until then, **no Tier 1 data is persisted** — which is true through Phase 8 by construction,
since there is no server and no fiscal integration.

ADR-022 (PCI-DSS) likewise assumes a mobile threat model; a shop-floor till machine is a different
one and gets re-examined in the same phase.

---

## Part C — Open questions

| # | Question | Blocks |
|---|---|---|
| **Q1** | Wholesale+retail, or multi-tenant SaaS? | Phase 7 only — see below |
| **Q2** | "Receipt will not be payment receipts" — separate payment terminal, or non-fiscal slips? | Phase 10 only |
| **Q3** | Printers already owned, or buying? | Phase 3 |
| **Q4** | Is quantity ever fractional (fabric by the metre)? | Phase 1 — `Int` vs scaled integer |

**Q1 is narrower than first assessed.** A desktop app sold to many shops is *multi-instance*, not
multi-tenant — each shop runs its own local database. Tenancy only bites the **server** in Phase 9.
Phases 0–6 proceed either way.
