# KD-010 — Sync is log shipping. Three kinds of row, and only one can conflict

## Status
Accepted — 22 Sep 2026

## Context
Phase 9 makes a second device possible. The usual shape of this problem — replicating mutable rows between peers — brings vector clocks, conflict resolution and a merge UI nobody uses.

Keswa does not have that problem, because Phase 1 did not build it. Every document is append-only with a client-generated UUID (D3, and `cashi_pax` defect F2), so merging two devices is the union of two sets of rows whose keys were unique before they were written.

Reading the DAOs rather than assuming turned up three piles, not two. `sale`, `sale_return`, `shift`, `stock_receipt` and `stock_count` are *not* immutable: a sale is voided, a shift is closed, a receipt is posted.

## Decision
**Rows are classified once, in `core/sync/SyncTables.kt`, and merged by the rule their pile earns:**

| | Merge | Can conflict? |
|---|---|---|
| **Event** — movements, lines, payments, ledger entries | Union on the id; a second copy is ignored | No |
| **Document** — sale, return, shift, receipt, count | Union, and a terminal state beats a non-terminal one | No |
| **Record** — catalogue, customers, users, prices | Last write wins, ordered by the log | Yes, rarely |

A document's transition is single, monotonic, and already guarded on the current state in SQL (`AND status = 'COMPLETED'`), which is what makes applying it twice — or before the row it acts on has arrived — safe.

**One log, one cursor, and the cursor is a server-assigned sequence.** Not a timestamp: two rows in the same millisecond mean `>` skips one and `>=` replays forever, and a clock that steps backwards opens a window of rows no cursor will ever return. Neither failure announces itself.

**Records converge because the log is a total order every device reads identically.** No timestamp comparison, no device tie-break. A record with a local unsent edit is not overwritten by an incoming one — the local version is pushed instead and becomes the later entry, so the person who made the edit watches it survive.

**Three tables do not sync**: `held_sale` (one till's scratchpad, and the only real `DELETE` in the schema), `stock_on_hand` (a projection, rebuildable), `app_setting` (this machine's printer and device id).

## Consequences
There is no conflict-resolution code, because there is nothing to resolve in the two high-volume piles. Retry is free: pushing twice is pushing once, which is what lets KD-009 re-decide ADR-041 for this path.

Delivery may be partial and out of order. A row whose parent has not arrived fails its foreign key, is put back, and is retried on the next pass; the cursor stops before it rather than stepping over it. Nothing needs to know that a sale is a header plus lines plus payments plus movements.

A clash on a unique index other than the primary key — two devices inventing the same SKU while both offline — cannot be designed away. It is settled deterministically (**the smaller id wins**, the only rule needing no clock and no conversation) and the loser is kept in `sync_superseded`. Anything based on arrival order would diverge, because each device meets the two rows in the opposite order.

Rejected: last-write-wins by `updatedAt` (needs trustworthy clocks on shop-floor PCs, and not every record has the column); a per-table cursor (fifteen cursors that can each be individually wrong); tombstones (avoided entirely by not syncing the one table that deletes, except for records, where the outbox naming a row the table no longer holds *is* the tombstone).
