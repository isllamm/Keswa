# Keswa — Sync Strategy

Phase 1 is 100% offline. This document specifies the decisions that must be made **now** so that
turning sync on later is an addition, not a rewrite — and states exactly what skipping each one costs.

Target backend: **Ktor + PostgreSQL, ~3 months out.** That timeline changes the plan materially; see §8.

---

## 1. The core insight

> Sync is easy for data that is **created** and hard for data that is **edited**.

So the model pushes as much of the business as possible into *append-only creation*:

| Data kind | Tables | Merge rule | Conflict possible? |
|---|---|---|---|
| **Ledgers** | `stock_movement`, `customer_ledger`, `payment`, `cash_movement`, `audit_event` | **Union of inserts** | **No** |
| **Documents** | `sale`, `sale_line`, `purchase_order`, `goods_receipt` | Immutable once `COMPLETED`; changes expressed as new documents (void, return) | Practically no |
| **Catalog** | `product`, `product_variant`, `barcode`, `price_list*`, `customer`, `supplier` | **LWW** on `(updated_at, origin_device_id)` | Yes — accepted |
| **Derived** | `stock_level`, `customer_balance` | **Never synced.** Recomputed locally from synced ledgers | No |
| **Local-only** | `device`, `sync_state`, `outbox_entry`, `backup_log` | Not synced | No |
| **Server-authoritative** | `document_counter`, `tenant`, `store`, `app_user` roles | Server wins | No |

Two shops selling the same shirt at the same moment produce two `stock_movement` inserts. Both are
kept, both are correct, the level is the sum. **No conflict resolution is needed for the data that
matters most.** That property is bought entirely in Phase 1 schema design and cannot be retrofitted
cheaply — see ADR-003.

---

## 2. Decisions that must be made in Phase 1

Ordered by cost of skipping, highest first.

### D1 — Client-generated ULID primary keys
**Decision:** every `id` is a 26-char ULID string generated on the device. No `AUTOINCREMENT` anywhere.
**Cost of skipping:** device A and device B both create sale `#1041`. Merging requires remapping every
primary key *and every foreign key that points at it*, across sales, lines, payments, movements, audit.
Receipts already printed carry the old number. This is the one mistake that is genuinely unrecoverable
without data loss. ULID over UUIDv4 because it sorts by creation time, which keeps SQLite B-tree
inserts sequential and makes `ORDER BY id` a usable proxy for `ORDER BY created_at`.

### D2 — `tenant_id` and `store_id` on every row
**Decision:** both columns on every table, indexed, always filtered in every query.
**Cost of skipping:** a full-table migration where you must *guess* which rows belonged to which store.
For a single-store dataset the guess is right; for the historical data of the first shop after store #2
opens, it is right by luck rather than design. Also: every query written without the filter is a
cross-tenant data leak the day the backend is multi-tenant. Adding the column is cheap; adding it to
5,000 lines of already-written queries is not.

### D3 — `updated_at` (UTC) + `deleted_at` + `revision` + `origin_device_id`
**Decision:** the four-column contract, written by `UnitOfWork`, never by hand.
**Cost of skipping:**
- no `updated_at` → no incremental pull; every sync is a full table download.
- no `deleted_at` → deletes don't propagate. A product deleted on the desktop **reappears** at the next
  pull, forever. Tombstones cannot be reconstructed after the fact — the row is gone.
- no `revision` → cannot distinguish "unchanged" from "changed back", and no optimistic locking.
- no `origin_device_id` → LWW has no deterministic tiebreaker when two clocks agree; different devices
  converge to *different* winners. Silent, permanent divergence.

### D4 — Append-only ledgers for stock and receivables
**Decision:** `stock_movement` and `customer_ledger` are insert-only; levels/balances are derived and
rebuildable.
**Cost of skipping:** a mutable `stock_level.qty` row edited on two devices is a true write-conflict
with no correct automatic resolution — LWW *loses a sale's worth of stock*. You would need per-row
CRDT counters, which is strictly more work than the ledger and loses the audit trail the owner needs
anyway. This decision is why sync is tractable at all.

### D5 — Outbox table + per-table sync cursors, written from day one
**Decision:** `outbox_entry` and `sync_state` are in migration `1.sqm`. `UnitOfWork` writes outbox rows
in the same transaction as every mutation, from the first release. Nothing drains them yet.
**Cost of skipping:** on sync day you have no record of *what changed*, only current state — so the
first sync is a full upload of everything, with no way to distinguish "created offline" from "already
on the server", and no ordering. Worse: writing outbox entries is the code path most likely to have
subtle transaction bugs, and you would be debugging it for the first time on the day the shop goes
multi-device. Cost of including it now: one table, ~20 lines in `UnitOfWork`, and a few MB of rows.

### D6 — `server_seq` column reserved now
**Decision:** nullable `server_seq INTEGER` on every syncable table; `sync_state.last_pulled_server_seq`.
**Cost of skipping:** pulls must be ordered by `updated_at`, which is a *client* clock. A device with a
wrong clock (very common on a shop PC that has been off for a week) either re-downloads everything or
silently skips rows. Adding a server sequence later requires a full re-sync of all history and a
migration on every table. Adding the column now costs nothing until Phase 4.

### D7 — Human document numbers are not identity
**Decision:** `doc_number` = `<store_code>-<type>-<seq>` (`ST01-INV-000123`) from a per-store
`document_counter`; the PK stays the ULID.
**Cost of skipping:** two stores both print `INV-000123`. The owner's Excel reconciliation breaks, and
"find invoice 123" becomes ambiguous. Store-prefixing from day one makes the number globally unique by
construction, with no coordination.

### D8 — Stable string codes for every enum
**Decision:** `'CASH'`, `'RETURN'`, `'PURCHASE_RECEIPT'` — never ordinals, with `CHECK` constraints.
**Cost of skipping:** inserting a value into a Kotlin enum silently reinterprets every historical row
after deployment. Across two app versions syncing to one server, this corrupts data with no error.

### D9 — Idempotency keys on mutating operations
**Decision:** every mutation carries a client-generated `operation_id` (ULID) stored on the outbox
entry; the server treats `(device_id, operation_id)` as a uniqueness key.
**Cost of skipping:** the classic offline bug — the request succeeded, the response was lost, the client
retries, the shop records the same sale twice. Discovered at month-end when the till doesn't reconcile.

### D10 — Money as integer minor units
**Decision:** `Long` minor units + currency code. (Also ADR-002.)
**Cost of skipping:** two replicas computing `0.1 + 0.2` in `Double` and rounding at different points
produce totals that differ by cents and never converge. Sums across devices stop matching, and there
is no way to decide which is right.

### D11 — Explicit conflict policy per table, written down before it is needed
**Decision:** the table in §1 is checked into the repo and each table's policy is asserted in code
(`SyncPolicy.of(table)`), even though nothing reads it in Phase 1.
**Cost of skipping:** the policy gets invented in a hurry during the first outage, per table, by
whoever is on the keyboard. Inconsistent policy is worse than a bad one.

### D12 — Wire contract lives in `:sync:contract` from Phase 0
**Decision:** `@Serializable` DTOs, `ChangeEnvelope`, `PullRequest/Response`, `protocolVersion`, error
codes — a commonMain module the Ktor server will include as a Gradle dependency.
**Cost of skipping:** the server and client define the schema twice and drift. With the backend only
~3 months out, this is the cheapest module in the project and the highest leverage.

### D13 — Clock discipline
**Decision:** a `Clock` interface in `:core:common`; nothing calls `System.currentTimeMillis()` directly.
All timestamps UTC millis. The shop's local timezone lives on `store.timezone` and is applied only at
display and report-boundary time.
**Cost of skipping:** untestable time-dependent logic, and "today's sales" meaning different things on
different machines. Storing local time instead of UTC is a data-corrupting mistake at DST or timezone change.

### D14 — Soft delete everywhere, enforced
**Decision:** no `DELETE` statement in any `.sq` file except for local-only tables. Enforced by a CI grep.
**Cost of skipping:** see D3. Also, a deleted product referenced by a two-year-old sale line breaks
every historical report.

---

## 3. Protocol sketch (Phase 4 — specified now, built later)

**Shape:** push-then-pull, per-table cursors, batched, resumable. Not real-time. **ASSUMPTION:** a
30–60s poll interval and a manual "sync now" button are sufficient for a clothing shop; websockets
add operational complexity for no business value at this size.

```
POST /sync/push     { deviceId, protocolVersion, changes: [ChangeEnvelope] }
                 -> { accepted: [{operationId, serverSeq}], rejected: [{operationId, code, message}] }

POST /sync/pull     { deviceId, cursors: { "sale": 10432, "product": 8891, ... }, limit: 500 }
                 -> { changes: [ChangeEnvelope], cursors: {...}, hasMore: true }
```

```kotlin
@Serializable
data class ChangeEnvelope(
  val operationId: String,      // ULID, idempotency key
  val table: String,
  val entityId: String,
  val op: Op,                   // INSERT | UPDATE | DELETE(soft)
  val tenantId: String,
  val storeId: String,
  val updatedAt: Long,
  val revision: Int,
  val originDeviceId: String,
  val serverSeq: Long?,         // null on push, set on pull
  val payload: JsonObject,      // the row
)
```

**Order of operations on the client:**
1. Drain outbox in `created_at` order, batched by table dependency (parents before children).
2. Apply server responses: set `server_seq`, mark outbox `SENT`.
3. Pull per table in dependency order; apply by policy; advance cursors.
4. Rebuild `stock_level` / `customer_balance` for touched entities.
5. Record failures in `sync_conflict` and surface a badge — **never** block the POS.

**Foreign-key ordering:** parents (product → variant → barcode; sale → sale_line → payment) push and
pull in dependency order. Because IDs are client-generated, a child can be inserted before its parent
arrives — so the server accepts out-of-order inserts within a batch and defers FK validation to the end
of the transaction (`SET CONSTRAINTS DEFERRED` in Postgres). Decide this now; it shapes the endpoint.

**Auth:** the sync client sends a device JWT. Device registration (Phase 4) exchanges a one-time pairing
code for a device token bound to `(tenant, store, device)`.

**Backpressure:** batch size 500 rows or 1 MB, whichever first; exponential backoff with jitter; a
permanently failing entry is parked after N attempts and reported, not retried forever.

---

## 4. Conflict resolution in detail

| Situation | Resolution |
|---|---|
| Same product edited on two devices | LWW by `updated_at`; tie broken by lexicographic `origin_device_id`. Loser's version written to `sync_conflict` for review. |
| Product deleted on A, edited on B | **Delete wins** if `deleted_at > updated_at`, else edit wins and the row is undeleted. Recorded either way. |
| Same variant sold on two devices | Both movements kept. Stock may go negative — surfaced as an alert, not an error. This is real-world truth: the shop *did* sell two. |
| Price changed while an offline sale used the old price | Offline sale wins; it snapshotted its price. Nothing to resolve. |
| Same customer payment entered twice on two devices | Not automatically resolvable — two ledger entries, two different `operation_id`s. Surfaced in a **duplicate-payment review** report (same customer, same amount, within N minutes). Human decides. |
| Document counter collision | Impossible: counters are per-store and store-prefixed. |
| Clock skew | Server stamps `server_received_at` alongside client `updated_at`; if skew > 5 min, the device warns the user and logs it. |

**Never** resolve a money conflict silently. LWW is acceptable for a product name; it is not acceptable
for a payment. The design's job is to ensure money never *reaches* a conflict path — which §1 does.

---

## 5. What "sync-ready" does **not** mean

Phase 1 deliberately does **not** build:
- any HTTP client, retry policy, or background scheduler
- conflict UI
- device pairing
- server-side anything

Those are Phase 4. Building them earlier means maintaining unused code against an unbuilt server.
The claim of this document is narrower and stronger: **the schema, the ledger discipline, and the
mutation choke point are correct now, so Phase 4 adds code without changing data.**

---

## 6. Test the sync assumptions before there is a server

Cheap Phase 1–2 tests that de-risk Phase 4 for a few hours of work:

1. **Two-database merge test** — run two in-memory DBs, apply disjoint operations, merge via the
   documented policy, assert convergence and that stock/balance rebuilds match. This finds ledger
   design flaws while they are still free to fix.
2. **Outbox completeness property test** — for a random sequence of mutations, assert that replaying
   only the outbox against a fresh DB reproduces the same materialized state.
3. **Tombstone test** — delete, merge, assert the row does not resurrect.
4. **Idempotency test** — apply the same envelope twice, assert no duplicate.

These are pure Kotlin in commonMain and need no infrastructure.

---

## 7. Migration path when the backend arrives

| Step | Work |
|---|---|
| 1 | Mirror the SQLite schema to Postgres DDL (mechanical — types were chosen to be portable: TEXT, INTEGER millis, no SQLite-isms) |
| 2 | Add `server_seq BIGSERIAL` and `(tenant_id, table, server_seq)` indexes server-side |
| 3 | Tenant + store + device provisioning endpoints |
| 4 | Implement `/sync/push` + `/sync/pull` against `:sync:contract` |
| 5 | Initial upload: drain the accumulated outbox. **This is why the outbox runs from day one** — the shop's whole history uploads through the same tested path as everyday changes. |
| 6 | Swap `LocalAuthGateway` → `JwtAuthGateway`. `Principal` is unchanged, so no call site moves (ADR-008). |
| 7 | Enable the sync scheduler behind a setting; run one store in shadow mode for two weeks before trusting it. |

**No local schema change is required by any of this.** That is the test of whether Phase 1 succeeded.

---

## 8. If you go online in 3 months vs 2 years

You answered **~3 months**. Here is what that actually changes.

### Because it is 3 months (do these now)
| Do | Why |
|---|---|
| Write `:sync:contract` in Phase 0, not Phase 4 | The server will consume it; defining it late means defining it twice |
| Keep the Postgres DDL mirrored in the repo from Phase 0 (`docs/sql/postgres.sql`), updated with every migration | A 3-month gap is short enough that drift is small and cheap to prevent, and it forces portable type choices |
| Provision `tenant_id`/`store_id` with *real* server-shaped values from day one (ULIDs, not `"default"`) | Avoids a rekeying migration at first sync |
| Use `kotlinx.serialization` for `ReportRequest`/`ReportResult` and all sync DTOs immediately | One annotation now vs a refactor later |
| Do not build local-only features with no server story (e.g. filesystem-path-based settings that can't be shared) | Every such feature becomes a Phase 4 special case |
| Decide auth boundaries now: `Principal`, permission codes as data | Server auth then replaces a gateway, not a design |
| **Resist** building sync itself early | Still no server. Unused code rots. |

### If it were 2 years instead
| Would change |
|---|
| Skip `:sync:contract` in Phase 0; keep only the column contract and outbox. Two years of protocol guesses would be wrong anyway. |
| Skip the mirrored Postgres DDL — it would drift beyond usefulness. |
| Consider a heavier local model (e.g. richer denormalization, local-only conveniences) since it has two years to pay off before the server constrains it. |
| Revisit the backend choice entirely at that point; committing to Ktor+Postgres now would be premature. |
| Invest the saved hours in shop-facing features instead — two years of a happier shop is worth more than an early protocol. |

**The columns, the ledgers, and the outbox are identical in both worlds.** That is the tell that they
are the right Phase 1 investments: they are not a bet on a timeline.

### The honest risk in your answer
Sync in 3 months at ~10h/week means Phase 0 + Phase 1 + a backend in ~120 hours. That is not
achievable (see `docs/plan/` for hour estimates). The realistic options:
1. **Recommended:** ship Phase 0 + Phase 1 to the shop (~150h, ~15 weeks), then build the backend.
   Cloud lands around month 6–7, with a real dataset to sync and a shop that is already benefiting.
2. Redefine "online in 3 months" as "`:sync:contract` written, Postgres DDL mirrored, server repo
   scaffolded" — achievable, and genuinely de-risks Phase 4.
3. Cut Phase 1 scope hard (no purchase orders, no customer credit) to reach a backend sooner — but
   then the backend syncs a POS the shop can't fully run on, which helps no one.

Option 1 or 2. Not both halves of neither.
