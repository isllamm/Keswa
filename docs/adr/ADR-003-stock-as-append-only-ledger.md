# ADR-003 — Inventory as an append-only ledger; weighted-average cost

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0

## Context
Stock quantity is the most contested number in a shop and the hardest thing to merge across devices.
It is also what the owner will check first. The costing method determines whether margin reports are
believable.

## Decision
**1. `stock_movement` is append-only.** Every change to stock is an insert with a positive quantity, a
`from_location_id` and a `to_location_id` (double-entry, using pseudo-locations `SUPPLIER`, `CUSTOMER`,
`ADJUSTMENT` where needed), a reason code, and a reference to the causing document. Rows are never
updated or deleted; a mistake is corrected by a reversing movement.

**2. `stock_level` is materialized, derived, and rebuildable.** It is updated in the same transaction
as the movement, and `rebuildStockLevels()` can recompute every row from the ledger. A property test
asserts ledger-sum == materialized-level over random operation sequences.

**3. Costing is weighted moving average**, maintained on inbound movements, with the integer division
remainder carried in `cost_rounding_minor`. `sale_line` snapshots `unit_cost_snapshot_minor` at sale
time so COGS never shifts retroactively.

**4. Negative stock is permitted**, surfaced as a warning. A shop blocked from selling an item it
physically holds will stop using the app.

## Alternatives considered
| Option | Rejected because |
|---|---|
| Mutable `stock.quantity` column | A true write-conflict under sync with no correct automatic resolution — LWW silently loses a sale's worth of stock. Also destroys the audit trail the owner needs. |
| Ledger without materialized levels | `SUM()` over the full ledger on every POS keystroke. Fine at 10k rows, unusable at 500k. |
| Materialized levels without a rebuild path | Any bug or partial restore leaves permanently wrong stock with no recovery. The rebuild is the reason a derived table is trustworthy. |
| PN-Counter CRDTs per variant | More machinery than the ledger, no audit trail, and still needs history for reporting. Strictly worse here. |
| **FIFO costing** | Requires cost layers, layer consumption on every sale, and layer merging under sync. For clothing — where the owner reasons in "what it cost me" — the extra complexity buys precision nobody asks for. Revisit only if an accountant requires it. |
| Standard/fixed cost | Margin reports become fiction as supplier prices move. |
| Blocking negative stock | The shop works around it by not recording the sale, which is worse than a wrong count. |

## Consequences
**Good:** stock merges are unions of inserts — **no conflict resolution needed for the most important
data in the system**; complete "why is stock 7" history; as-of-date reporting is reconstructible;
transfers, counts, damage and theft are all one mechanism.

**Costs:** more rows (a 200-line day ≈ 200 movements — trivial); every stock change must go through
`UnitOfWork`, never a direct `UPDATE` (enforced by a CI grep on `.sq` files); the rebuild must be fast
enough to run after a restore (it is: a single grouped scan).

**Consequence for the owner:** "adjust stock to 12" is not an operation. It becomes "count says 12,
system says 15, post a −3 `COUNT_ADJUST`". The UI must present this as an adjustment with a reason,
which is also better practice.
