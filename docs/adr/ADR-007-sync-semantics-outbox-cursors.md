# ADR-007 — Sync semantics, outbox and cursors from day one

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0 (schema) / 4 (execution)

## Context
Phase 1 is entirely offline, but a Ktor + PostgreSQL backend is planned within months. The question is
not "how do we sync" — that is Phase 4 — but "what must be true of the Phase 1 data so that sync is
possible without a rewrite".

## Decision
**1. A per-table conflict policy, decided now and written down:**

| Data kind | Policy |
|---|---|
| Ledgers (`stock_movement`, `customer_ledger`, `payment`, `cash_movement`, `audit_event`) | Insert-only. **Union merge. No conflict possible.** |
| Documents (`sale`, `purchase_order`, …) | Immutable once completed; changes are new documents (void, return) |
| Catalog (`product`, `variant`, `price_list`, `customer`, `supplier`) | Last-write-wins on `(updated_at, origin_device_id)`, loser recorded in `sync_conflict` |
| Derived (`stock_level`, `customer_balance`) | Never synced. Recomputed locally from synced ledgers |
| Server-authoritative (`tenant`, `store`, roles) | Server wins |

**2. `outbox_entry` and `sync_state` ship in migration `1.sqm`, and `UnitOfWork` writes outbox rows in
the same transaction as every mutation, from the first release.** Nothing drains them until Phase 4.

**3. The outbox stores a pointer** (`entity_table`, `entity_id`, `op`, `entity_updated_at`), not a
payload; the row is re-read at push time.

**4. `server_seq` (nullable) is reserved on every syncable table** so pulls can later be ordered by a
server sequence rather than an untrustworthy client clock.

**5. `:sync:contract` — the wire DTOs — is written in Phase 0**, because the server is only ~3 months
out and will consume it as a Gradle dependency.

## Alternatives considered
| Option | Rejected because |
|---|---|
| Add the outbox when sync is built | On sync day there is no record of *what* changed, only current state; the first sync becomes an undifferentiated full upload. Worse, the outbox is the code path most likely to have subtle transaction bugs, and it would be debugged for the first time on the day the shop goes multi-device. |
| Change-data-capture via SQLite triggers | Triggers can't see the acting `Principal` or the business intent, are invisible to Kotlin tests, and are easy to forget when adding a table. `UnitOfWork` already exists as the mutation choke point. |
| Full-row payloads in the outbox | Larger, and stale by the time it is sent. Pointer + re-read is always current; the lost intermediate states are exactly what LWW discards anyway. |
| Full-state sync (upload everything, server diffs) | Simple, and viable at one shop's data size — but no deletes, no ordering, no idempotency, and it degrades badly the moment there are two stores. |
| A CRDT library for everything | Solves conflicts the ledger design already avoids, at the cost of a foreign data model, no audit trail, and a much harder PostgreSQL mirror. |
| Ordering pulls by client `updated_at` | A shop PC with a wrong clock either re-downloads everything or silently skips rows. `server_seq` costs one nullable column now. |
| Defining the wire contract in Phase 4 | With a 3-month horizon, client and server would define the schema twice and drift. It is the cheapest module in the project. |

## Consequences
**Good:** the data that matters most — stock and money — cannot conflict, so most of "sync" reduces to
transport; the shop's entire history is already queued for the first upload, through the same tested
path as everyday changes; Phase 4 should require **no local schema migration** — that is its exit test.

**Costs:** one extra table write per mutation (~20 lines in `UnitOfWork`, a few MB of rows per year);
soft-delete-only discipline enforced by CI (no `DELETE` in `.sq` except local-only tables); ledgers
grow forever, which is what makes them useful and is trivially affordable at this scale;
`:sync:contract` is compiled but unused for months.

**Accepted risk:** LWW on catalog rows can lose an edit. Acceptable for a product name; the design's
job is to ensure money never reaches a conflict path, which the ledger policy achieves. Duplicate
customer payments entered on two devices are surfaced for human review, never merged automatically.
