# Phase 6 Plan — Receiving, Counts, Labels & the Android Target

> **Status: ✅ BUILT**
> Depends on: Phase 1 (ledger), Phase 3 (label printer, `Tspl`), Phase 4 (permissions), Phase 5 (money, transactions)
> Expands the outline in `phases-5-to-10-outline.md`.
> Estimated: 8–11 days

## Goal

Close the inventory loop. Stock comes in as well as out, at a known cost, and what the database
thinks is on the shelf can be checked against what is on the shelf.

---

## Deviations from the outline, stated up front

The outline listed **purchase orders**. This phase ships **goods receipts** instead.

A purchase order is a document you raise *before* the goods arrive, and its value is the
expected-versus-actual comparison when they do. That is supplier management, and it belongs with the
supplier and credit work in Phase 7. What a shop needs now is to record *what arrived and what it
cost* — the van is already outside. Receipts are half the schema and all of the value, and a PO
becomes an optional parent document later without changing a receipt's shape.

The outline also listed **spreadsheet import**. This phase ships the parser, the validation and the
import path, but **pasted text rather than a file dialog** — picking a file needs a new platform
bridge on two targets, and it is the least interesting part of the feature. Noted as unfinished
rather than quietly dropped.

---

## Decisions

### 6a — Cost is a moving weighted average — **ADR KD-008**

The phase's one irreversible decision, so it gets an ADR: `docs/adr/KD-008-moving-average-cost.md`.

FIFO needs a layer ledger replayed in order, which fights the commutative append-only design that
makes Phase 9's sync need no conflict resolution. Moving average does not.

Two things follow, and the second is what makes the decision survivable:

- Receiving recomputes `variant.costPiastres`, which becomes derived state.
- **Every movement carries a `unitCostPiastres` snapshot**, so the ledger describes its own cost
  basis. Phase 8 reads the snapshots rather than recomputing anything.

### 6b — A receipt is a draft until it is posted

Unpacking boxes takes twenty minutes and a phone call. So a receipt is built up as a `DRAFT`, and
one `POST` writes its movements, updates costs and closes it — in one transaction, the Phase 5 rule.

A `DRAFT` moves no stock. That is the point: an app that half-received a delivery because someone
walked away mid-box is worse than one that received nothing.

Posting is **idempotent by construction**: the status guard is in the `UPDATE`, exactly as Phase 5's
void is, so a double click cannot receive the same carton twice.

### 6c — Counts are blind, and that is not negotiable

A counter never sees the expected figure until the count is posted.

This is the whole reason to have the feature. A cashier who can see that the system expects 12 will
count until they get 12 — not dishonestly, but because the eye finds what it is told to look for —
and the discrepancy that would have told the owner something disappears. `StockCountLine.expected`
is null until posting fills it in, so there is nothing for a screen to leak.

Posting writes one `COUNT` movement per line that differs, carrying the variance and a note. Lines
that agree write nothing: a ledger entry for "nothing changed" is noise that makes the real ones
harder to find.

### 6d — Adjustments need a reason in words, not just an enum

`MovementReason` has `ADJUSTMENT` and `DAMAGE`, which say the category. "Three shirts water-damaged
in the stockroom" is the fact.

So `stock_movement` gains a nullable `note`. It is the difference between an audit trail and a list
of numbers, and the column is cheap.

### 6e — Labels print from the same document the receipt produced

A carton arrives, is received, and its contents need hang tags. Printing from the receipt's own
lines means the quantities are already right — nobody types "how many navy mediums did we get" a
second time and gets it wrong.

`Tspl` and the label spec are already in `:core` from Phase 3; this phase only adds the use case
that turns receipt lines into labels, and the batch-size question (one tag per piece, which is what
a shop actually wants, not one per line).

### 6f — `androidTarget()` lands here — KD-004

Handheld counting on the shop floor is the reason: walking a rail with a laptop is not a thing
anyone does twice.

This is the first time `commonMain` has a second consumer. The `java.*` ban has been gated in CI
since Phase 0 precisely so that this is a build-configuration exercise rather than an archaeology
one, and the gate has passed on every commit since.

What it needs:

| Piece | Where |
|---|---|
| `androidTarget()`, `compileSdk`, `minSdk` | every module's `build.gradle.kts` |
| `kspAndroid` alongside `kspDesktop` | `:core` |
| `getDatabaseBuilder(context)` | `core/androidMain` — `Context.getDatabasePath()`, which has no desktop analogue and vice versa |
| The credential hasher | shared, not copied — see *What changed* below |
| `AndroidReceiptRenderer` | `android.graphics.Canvas`; the same `MonoBitmap` out the other side |
| An activity and a manifest | `:composeApp/androidMain` |

**The hashing parameters are not a free choice.** A user created on the desktop till must sign in
on the handheld, so the algorithm, iteration count, key length and salt length have to match
exactly. Rather than trust two copies to stay in step, there is now one — see *What changed*.

### 6g — Import is validated as a whole, then applied as a whole

A spreadsheet with one bad row does not import 199 products and leave the operator guessing which
one failed. The parse produces either a list of rows or a list of problems, each with its line
number, and only a clean parse is applied.

Reusing the receiving path rather than writing a second creation path is the point of doing it at
all — otherwise import becomes the way to get data into the shop that skips every rule.

---

## Deliverables

### Schema v5

```
stock_receipt        id, reference, supplierName, locationId, status, note,
                     createdAt, createdByUserId, postedAt, postedByUserId, totalCostPiastres
stock_receipt_line   id, receiptId, lineNumber, variantId, quantity,
                     unitCostPiastres, lineTotalPiastres
stock_count          id, locationId, status, note,
                     startedAt, startedByUserId, postedAt, postedByUserId
stock_count_line     id, countId, lineNumber, variantId, countedQuantity,
                     expectedQuantity (null until posted), varianceQuantity (null until posted)
```

Plus two nullable columns on `stock_movement`: `unitCostPiastres` (KD-008) and `note` (6d). Both
additive, both `NULL` for every row written before this phase — which is honest: those movements
genuinely had no cost basis recorded.

### `:features:inventory`

```
features/inventory/
├── domain/usecase/
│   ├── ReceiveStockUseCases.kt     create, add line, post, discard
│   ├── StockCountUseCases.kt       start, enter, post, adjust
│   ├── FindStockItemUseCase.kt     the stockroom's lookup — price optional, unlike the till's
│   ├── PrintHangTagsUseCase.kt
│   └── ImportCatalogueUseCase.kt   parse, validate, apply
├── presentation/screens/           receiving · count · adjust · importer
└── di/InventoryModule.kt
```

The KD-008 arithmetic itself lives in `core/domain/money/MovingAverage.kt`, beside `allocate` and
`taxIncludedAt`: the repository has to apply it inside the posting transaction, so it cannot sit in
a feature.

### Android target

`:core`, `:features:*` and `:composeApp` all gain `androidTarget()`, with the platform
implementations above and an `androidMain` Koin module.

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No purchase orders, no supplier records.** See the deviation note. Phase 7.
- **No transfers between locations.** `TRANSFER_IN`/`TRANSFER_OUT` exist in the enum from Phase 1,
  but a single-shop install has nowhere to transfer to. It lands with multi-branch in Phase 9.
- **No barcode label design editor.** One layout, defined in code. A shop that wants a different tag
  wants a different tag, not a layout engine.
- **No costed returns.** Returns are Phase 8; what a return does to average cost is decided there,
  with the rest of the return policy.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Moving average | 10 @ 100 then 10 @ 140 → 120. Table-driven, including the HALF_EVEN boundary |
| 2 | Average against no stock | On-hand 0 or negative → the receipt's cost wins outright |
| 3 | Posting is atomic | Fail mid-post → no movements, no cost change, receipt still `DRAFT` |
| 4 | Posting twice | Second post is refused; stock moved once |
| 5 | A draft moves nothing | Lines added, not posted → on-hand unchanged |
| 6 | Cost snapshot | Every movement a receipt writes carries the cost it was received at |
| 7 | Blind count | `expectedQuantity` is null on every line until the count is posted |
| 8 | Count variance | Counted 9 against 12 → one `COUNT` movement of −3; a line that agrees writes nothing |
| 9 | Permissions | `RECEIVE_STOCK` and `COUNT_STOCK` enforced in the use case, UI bypassed |
| 10 | Adjustment reason | The note reaches the movement and survives a read-back |
| 11 | Import validation | One bad row → nothing imported, and the problem names its line |
| 12 | Migration v4 → v5 | KD-002 harness: seed v4 with sales, migrate, assert everything intact |
| 13 | Hash parity | A known salt and secret hash identically on desktop and Android |
| 14 | `commonMain` purity | The Android target compiles — which is the real test of the `java.*` gate |

Check 7 is the one that decides whether the count feature is worth having at all.

## Definition of Done

- [x] Stock is received against a draft and posted atomically, updating cost by KD-008
- [x] Every movement carries its cost basis
- [x] Counts are blind, and post only the differences
- [x] Adjustments carry a reason in words
- [x] Hang tags print from a posted receipt, one per piece
- [x] A catalogue spreadsheet imports, or fails as a whole with line numbers
- [x] `androidTarget()` compiles, with matching password hashing proven by test
- [x] v4 → v5 migration test green
- [x] `./scripts/check-gates.sh` and `./gradlew allTests` pass

## What changed while building it

**A `jvmCommonMain` source set now holds the credential hasher.** Both targets are JVM by KD-004,
and `JvmPasswordHasher` is byte-for-byte the same on each. Writing it twice would mean a raised
iteration count on one target and not the other — and then nobody can sign in on the handheld. One
implementation, plus a golden-hash test pinning the algorithm, cost, key length and encoding, so
changing any of them is a deliberate act.

This is narrower than the `jvmCommonMain` that KD-004 *rejected*: that one was for money in shared
code, which would have foreclosed a non-JVM target. Platform bridges are per-platform by
definition, so a third platform would simply get a third implementation.

**Database-backed tests moved from `commonTest` to `desktopTest`.** `Room.inMemoryDatabaseBuilder`
needs a `Context` on Android, so the fixture could not stay shared. Worth doing anyway: the tests
left in `commonTest` — money, the moving average, scanning, the printing protocols — now genuinely
run against both targets, which is what shared tests are for.

**The shell grew a Stockroom hub.** Eight top-bar entries did not fit on a handheld, which is a
thing that only becomes obvious once there is a handheld.

**The `println` gate gained an `android.util.Log` sibling.** ADR-029 always meant both; only one
was enforceable before this phase.
