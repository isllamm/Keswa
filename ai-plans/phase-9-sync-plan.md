# Phase 9 Plan (sync) — The Server, and Getting Two Devices to Agree

> **Status: ✅ BUILT — 22 Sep 2026**
> Depends on: Phase 1 (append-only ledger, client-generated UUIDs), Phase 4 (users, and the portable
> password hash Phase 6 pinned), Phases 5–8 (the documents there are to sync)
> Estimated: 8–10 days

**Phase 9 splits in two, the way Phase 8 did.** This plan is the first half — the server and the
sync engine. The web back office gets `phase-9-backoffice-plan.md` when it is next, because it has
nothing to render until the server has something in it.

## Goal

A second device. The till on the counter, the handheld on the shop floor and (next half) the owner
at home all see the same shop, and none of them stops working when the internet does.

---

## The decision the whole phase turns on

**Sync is log shipping, not replication.**

Phase 1 made this choice four phases before it was needed and every phase since has kept it: every
document in this app is append-only with a client-generated UUID. Nothing is ever edited — a
mistake is a compensating entry. So merging two devices is not a merge at all. It is a union of two
sets of rows keyed on UUIDs that were unique before they were written.

From that one property:

1. **There is no conflict resolution to write**, because there is no conflict. Appends commute.
   Stock on hand is `SUM(quantity)` whatever order the rows arrive in.
2. **Retry is free.** Pushing the same batch twice is the same as pushing it once — the second
   attempt upserts onto identical UUIDs. This is what `cashi_pax` defect F2 got wrong, and Phase 1
   fixed it before there was anything to sync.
3. **Delivery can be partial and out of order** without corrupting anything, which is what lets the
   engine be as simple as it is.

The work of this phase is therefore not "sync". It is the plumbing around sync: what to send, how
to know what has been sent, how a device proves who it is, and where the one genuinely mutable
corner of the schema is handled.

---

## Decisions

### 9a — Three kinds of row, and only one of them can conflict

The design rests on sorting the schema into piles and treating each differently. Reading the DAOs
rather than assuming produced **three** piles, not the two this plan first sketched — and the third
one is the interesting one.

| | **Events** | **Documents** | **Records** |
|---|---|---|---|
| Tables | `stock_movement`, `sale_line`, `payment`, `sale_return_line`, `customer_ledger_entry`, `stock_receipt_line`, `stock_count_line` | `sale`, `sale_return`, `shift`, `stock_receipt`, `stock_count` | `product`, `variant`, `category`, `colour`, `price`, `price_list`, `customer`, `app_user`, `location`, `assortment_pack*` |
| Mutable? | Never | Once, in one direction | Freely |
| Merge | Union on UUID | Union, and a terminal state beats a non-terminal one | Last write wins, loser recorded |
| Conflict possible? | **No** | **No** | Yes, and rarely |

**Documents are the pile the first draft of this plan missed.** A sale is not immutable: it can be
voided. A shift is opened and closed, a receipt and a count go `DRAFT → POSTED`. But every one of
those is a **single monotonic transition to a terminal state**, already guarded on the current state
in SQL (`AND status = 'COMPLETED'`), which is what makes it idempotent. Applying "a void beats a
completed sale" converges no matter which order the two rows arrive in, and applying it twice is
the same as applying it once. So documents merge with no conflict resolution either — they just
need a rule that is not plain union.

Events are the shop's history and there are thousands a day. Records are its configuration and
there are a handful of edits a week. The asymmetry is the point: the high-volume piles are the ones
that cannot conflict, so the expensive machinery is only ever pointed at the cheap one.

**Two tables sync at all, and one of them only after it is posted:**

- `held_sale` / `held_sale_line` **do not sync.** A parked basket is one till's scratchpad and it
  is deleted on resume. Syncing it would let two tills resume the same held sale and sell the same
  garments twice, and it is the only table in the schema with a genuine `DELETE`. Not syncing it
  removes the need for tombstones from the whole design.
- `stock_on_hand` **does not sync.** It is a projection of `stock_movement`, rebuildable locally
  (`rebuildProjection`), and syncing a cache alongside its source is how the two come to disagree.
- `stock_receipt` and `stock_count` sync **only once posted.** A draft is one person's unfinished
  work, and it is the other place the schema deletes rows. The trigger fires on the transition to
  `POSTED` and enqueues the header and its lines together.

**Records converge because the log is a total order that every device reads identically.**

This plan first said last-write-wins on `updatedAt`, tie-broken on `deviceId`. Building it showed
that to be both weaker and more work than necessary: `updatedAt` needs trustworthy clocks on
shop-floor PCs, not every record carries the column, and none of it is needed. Every device applies
the log in the same order, so "the later entry wins" already gives every device the same answer,
with no comparison at all.

One case does need care, and it is the one a person notices. A device with an **unsent local edit**
must not have it overwritten by an incoming one: the local row would be clobbered and then pushed
in its clobbered form, so the person who made the edit would watch it vanish with nothing to show
it happened. An arriving record whose row is pending in the local outbox is therefore skipped; the
push sends the local version, which becomes the later entry and wins everywhere.

The loser is written to `sync_superseded` rather than dropped — a shop that finds yesterday's price
back again needs to see that it was overwritten, and by which machine.

### 9b — The cursor is a server sequence, never a timestamp

Every row the server accepts is appended to one log and gets a monotonically increasing `seq`. A
device pulls `WHERE seq > lastSeen`.

A timestamp cursor is the obvious alternative and it is wrong twice over. Two rows in the same
millisecond mean `> lastSeen` skips one and `>= lastSeen` replays it forever. And a clock that
steps backwards — NTP correcting a drifting shop PC, or somebody fixing the date — silently creates
a window of rows that no cursor will ever return. Neither failure announces itself; both are
discovered weeks later as stock that does not add up.

A sequence is assigned by one machine, in one place, in arrival order, and has neither problem.

**One log and one cursor, not one per table.** A per-table cursor means fifteen cursors that can be
individually wrong, and a device half-way through a pull is in a state no test thought to construct.

### 9c — The server is a till that never sells

**Deviation.** The architecture plan's stack table says *Backend (Ph. 9) — Ktor + Postgres*. This
plan keeps Ktor and drops Postgres: the server stores its state in the **same Room/SQLite database
the till uses, from the same `:core` module**.

The reasoning:

1. **The schema already exists and is already migration-tested.** Eight versions of hand-written,
   tested `Migration` objects (KD-002) describe this shop's data. A Postgres server means a second
   schema, a second migration history, and a second definition of what a sale is — which drift the
   first time somebody adds a column to one and not the other.
2. **The materialised state is literally a shop's database.** Phase 8's analytics use cases are
   pure Kotlin over repository interfaces. On this server they run unchanged, which is most of the
   back-office half of Phase 9 already built.
3. **The load is a rounding error.** One shop, two to five devices, a few thousand rows a day.
   SQLite in WAL mode serves that with orders of magnitude to spare. Postgres is the right answer
   to a scale problem this project does not have and cannot honestly claim to be planning for.
4. **It is verifiable on a developer's machine** with nothing installed. Every check in the
   verification table below runs under `./gradlew allTests`. A Postgres server means the sync
   tests either need Docker or get replaced by tests against a fake — and a sync engine tested
   only against a fake is a sync engine that has never been tested.

**The exit, written down now rather than discovered later.** The durable artefact is the change
log, not the materialised tables — the tables are derived and can be rebuilt from the log at any
time. So the day a shop outgrows this, the migration is: stand up Postgres, replay the log into it,
point the devices at the new host. Nothing is lost because nothing was ever only in the tables.

Two tables the till does not have live in a **separate** `SyncLogDatabase` on the server — the log
and the device registry. Keeping them out of `KeswaDatabase` means the till's schema stays exactly
what it was, and the server's extra state has its own migration history.

### 9d — The outbox is written by triggers, and the log accepts any order

A device has to know which of its rows the server has not seen. Two ways to do that, and the
obvious one is wrong.

**Rejected: a `syncedAt` column on every syncable table.** It means an `UPDATE` on `stock_movement`,
and the append-only guard that every phase since Phase 1 has verified exists precisely to make that
impossible. A metadata column is still an `UPDATE`, and a rule with an exception is not a rule.

**Chosen: a `sync_outbox` table, populated by SQLite triggers.** The ledger tables are never touched
at all. One `AFTER INSERT` trigger per event table, plus `AFTER UPDATE` on the record tables, and an
outbox row appears without any repository knowing sync exists. Three traps come with it, all worth
writing down because each one is silent:

- **Room does not manage triggers.** They must be created in the v8 `Migration` *and* in a
  `RoomDatabase.Callback.onCreate`, or a fresh install syncs nothing and never says why. A gate in
  `check-gates.sh` asserts every syncable table has a trigger in both places.
- **Triggers fire on pulled rows too**, so a row arriving from the server would be enqueued straight
  back at it, forever. The apply path sets a flag in a `sync_control` table and every trigger is
  `WHEN NOT applying`. Belt and braces: the server also skips rows a device authored when serving
  that device's pull.
- **A batch can split a document.** A sale's header, lines, payments and stock movements are written
  in one Room transaction but there is no transaction id a trigger can see, and a 500-row batch can
  cut between them.

That last one is solved by not solving it. **The log accepts rows in any order, and no row is ever
applied before its parent.** A row whose foreign key cannot yet be satisfied fails, is put back, and
is retried on the next pass; the cursor stops before it rather than stepping over it. No transaction
ids, no two-phase anything, and partial delivery — the normal case on a shop's wifi — is correct by
construction rather than by care.

**What that does and does not promise**, stated precisely because the first draft of this plan
overclaimed it: a line can never arrive without its sale, and a movement can never arrive without
its variant. A sale *header* can briefly be present with lines still in flight, because nothing in
the schema says how many lines it should have. In practice the header, lines, payments and movements
are enqueued in one transaction and so are contiguous in the outbox, and a whole push batch is
applied in one transaction — so the split only happens at a batch boundary, and closes on the next
pass.

### 9e — The server authenticates devices. The shop still authenticates people

Phase 9's outline asked (9d) how an offline till authenticates a user it has never seen. It does not
have to, and the answer was built in Phase 6 for a different reason.

`app_user` is a **record** under 9a, so a user created at the counter syncs to the handheld like any
other record — including `passwordHash` and `salt`. Phase 6's `jvmCommonMain` `JvmPasswordHasher`
exists because a user created on desktop must sign in on Android, and its golden-hash test pins the
algorithm, iteration count, key length and salt length across both. That is exactly the property
that makes this work: the hash is portable, so the credential is portable, so **a device that has
synced can authenticate a person it has never seen, offline, with no server involved.**

So the server authenticates **devices**, not people:

- An admin generates a one-time enrolment code; the device exchanges it for a long-lived device
  token. The server stores only a hash of the token.
- Revocation is a row on the server. A stolen laptop stops syncing on the next attempt.
- The token grants **sync only** — push rows, pull rows. It is not an admin credential and cannot
  become one.

Rows carry the `userId` that Phase 1 stamps on them. The server records them; it does not re-check
them, because the device that wrote them already did, against the same user record.

### 9f — KD-007: on desktop, the filesystem is the trust boundary

KD-006 deferred this and said Phase 9 must decide it. Deciding it means first being honest about
what is actually being protected.

The device token sits in the application data directory. **Next to it sits `keswa.db`** — the shop's
entire trading history, every customer, every price, every cost and margin, in plaintext SQLite.
Anyone who can read the token file can read that. Encrypting one string with an OS keystore while
the database beside it is open is security theatre: it protects the least valuable thing in the
directory and lets the reader believe the problem is solved.

**Decision:**

- **Desktop** — the token is a file in the app data directory, `0600` on POSIX, under the user
  profile on Windows. No JNA, no three platform bindings for a secret worth less than the file next
  to it.
- **Android** — Keystore, because it is there, costs nothing, and the handheld is the device that
  actually gets left on a counter.
- **Both** — the token is device-scoped and server-revocable, which is the control that does the
  real work. The mitigation for a stolen token is that it can be turned off from the back office in
  seconds and its use is in the log.
- **If the threat model changes**, the answer is encrypting the whole database (SQLCipher), not a
  keystore for one string. Written down so that the next person to ask reaches for the right tool.

`ISyncTokenStore` follows KD-005 — an interface in `core/platform/`, bound per platform in Koin,
exposing what you can do (`store`, `read`, `clear`) and never a file handle.

ADR-022 (PCI-DSS) is re-examined here as KD-006 promised, and the finding is short: it assumes a
consumer phone the owner carries. A shop-floor till is a machine behind a shop's door, and Q2 took
card data out of scope entirely — the terminal is separate and Keswa prints non-fiscal slips. It
does not apply.

### 9g — KD-009: bounded retry, on the sync path and nowhere else

ADR-041 bans HTTP retry. The conventions document already flags it as inherited for a reason that
does not hold here: Cashi banned it purely for native parity (ADR-036), which is declared N/A.

**Retry on sync, with exponential backoff and full jitter, capped at 5 attempts and ~60s.** The
property that makes retrying dangerous elsewhere — a request that might have had an effect before
the connection dropped — is the exact property this phase engineered away. Pushing twice is pushing
once. Full jitter rather than plain exponential because three devices in a shop that loses wifi
will otherwise retry in lockstep forever.

**Nothing user-facing retries.** A person waiting at a counter wants a failure and a button, not a
silent 30-second stall. The ban stands everywhere except the background sync path, which is the one
place where nobody is waiting.

### 9h — The server holds no business rules

It validates shape, authenticity and ownership. It does not check credit limits, recompute totals,
or decide whether a return is inside its window.

Those rules are use cases in `commonMain` and they already ran, on the device, before the row
existed. Running them again on the server means two implementations of `CreditPolicy`, which is one
implementation and one thing that used to be that implementation. The server's job is to be a
durable, ordered, authenticated place to put rows that are already correct.

The one thing it does enforce is the clock-skew guard from 9a, because that is a property of the
transport and not of the shop.

### 9i — Receipt numbers get a block per device, and the schema does not change

Phase 5 left this exact note on `SaleEntity`:

> `receiptNumber` is the human-facing sequence, unique so that two tills sharing one database in
> Phase 9 collide loudly rather than quietly issuing the same number twice.

It comes due now, and it is not a hypothetical: `nextReceiptNumber()` is
`SELECT MAX(receiptNumber) + 1 FROM sale`, so two tills selling at the same moment both allocate
413 and one of them is rejected by the unique index on the first sync. Day one, not eventually.
`sale_return.returnNumber` is identical.

**Rejected: a composite key on `(deviceId, receiptNumber)`.** It is the obvious fix and it costs
more than it looks. It means altering two shipped tables, rebuilding two unique indices, and — the
part that actually bites — the manual lookup a shop uses when a receipt is too crumpled to scan,
`byReceiptNumber(413)`, stops having one answer. That reaches into the returns screen for a problem
the customer standing at the counter did not cause.

**Chosen: each device sells from its own block of a million.** The device's ordinal comes from the
server at enrolment; block start is `ordinal × 1,000,000`, and `nextReceiptNumber()` takes the max
*within this device's block*. The first device to enrol takes ordinal 0, so a shop that has been
trading on one till sees its numbering continue undisturbed.

Nothing about the schema changes. The unique index stays exactly as Phase 5 wrote it and simply
stops being reachable, because blocks do not overlap. `byReceiptNumber` still has one answer. A
second till's receipts read `1000001` upward, which is unambiguous on paper without a formatting
change. A million sales per device is about four centuries of a busy shop.

**The other unique indices** — `app_user.username`, `variant.sku`, `variant(productId, colourId)` —
are records, and two devices genuinely can create the same one while offline. These cannot be
designed away, so they are resolved rather than prevented: the log accepts both, and **the row with
the smaller id wins**, with the loser written to `sync_superseded`.

Arbitrary, and deliberately so. It was planned as "the earlier `createdAt`, tie-broken on
`deviceId`", and building it showed why that is worse: it needs a column not every record has, and
anything keyed on arrival order **diverges**, because each device meets the two rows in the opposite
order. Comparing ids needs no clock, no sequence and no conversation, so two devices reach the same
answer without either knowing what the other decided. Rare, deterministic, and visible afterwards,
which is the most that can honestly be promised.

---

## Deliverables

### Schema v8 — till side

```
sync_outbox      seq INTEGER PK AUTOINCREMENT, tableName, rowId, op, enqueuedAt
sync_cursor      id PK ('default'), lastSeq
sync_superseded  id, tableName, rowId, previousJson, supersededAt, byDeviceId
sync_control     key PK, value                    ← the "applying" flag the triggers read
```

Plus triggers on every syncable table, and `deviceId` / `serverUrl` as rows in the existing
`app_setting`. No existing table changes shape — the first phase since Phase 1 where that is true,
and a direct consequence of 9d.

### Server — `SyncLogDatabase`

```
sync_change_log  seq INTEGER PK AUTOINCREMENT, tableName, rowId, payloadJson, deviceId, receivedAt
sync_device      id, name, tokenHash, enrolledAt, revokedAt
```

### `:server`

```
server/
├── SyncRoutes.kt          push, pull, enrol, health
├── DeviceAuth.kt          bearer token → device, or 401
├── LogStore.kt            append to the log, read a window by seq
├── Materialiser.kt        log → KeswaDatabase, whole documents only
└── ServerMain.kt          Ktor, Netty, config from env
```

```
POST /v1/enrol        { code, deviceName }        → { deviceId, token }
POST /v1/sync/push    { rows[] }                  → { accepted, rejected[] }
GET  /v1/sync/pull    ?since={seq}&limit={n}      → { rows[], nextSeq, hasMore }
GET  /v1/health
```

`:server → :core → (nothing)`. It depends on no feature module and contains no Compose. The module
hierarchy gains one top-level consumer alongside `composeApp`, which is the shape the rule already
allows.

### `core/sync/`

```
core/sync/
├── SyncEngine.kt          one pass: push the outbox, pull the log, apply
├── SyncScheduler.kt       periodic, backed off, never on the UI path
├── SyncApi.kt             Ktor client
├── protocol/SyncDtos.kt   the wire types, shared by both sides at compile time
└── RowCodec.kt            entity ↔ JSON, one place, generated per table
```

`ISyncTokenStore` joins the other capability interfaces in `core/platform/`, with `DesktopSyncTokenStore`
and `AndroidSyncTokenStore` bound in Koin per KD-005.

### ADRs

- **KD-007** — desktop secure storage, deciding 9f and discharging KD-006.
- **KD-009** — bounded retry on the sync path, re-deciding ADR-041 for Keswa.
- **KD-010** — sync is log shipping; events and records; one log, one cursor.

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No Postgres.** 9c, with the exit path written down.
- **No web back office.** Its own plan, its own half. It needs a server that already works.
- **No websockets or push.** A till that learns about a price change within 60 seconds is a till
  that is working correctly. Real-time costs a reconnection state machine and buys nothing a shop
  has asked for.
- **No multi-tenancy.** Q1 settled it on 21 Sep 2026. Each shop runs its own server.
- **No branch-to-branch stock transfers.** A real feature and a good one, but it is inventory, not
  sync, and it belongs in a phase that can give it a screen.
- **No server-side business rules.** 9h.
- **No deletion or tombstones.** Nothing in this schema is deleted — `isActive` was the convention
  from Phase 2 onward, which means the hardest part of sync in most systems does not arise.
- **No conflict resolution UI.** The conflict surface is two admins editing one product inside one
  minute. `sync_superseded` records it; a screen for it can wait until a shop reports one.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Idempotent push | Push a batch twice → one row, not two |
| 2 | Order independence | Two devices' movements applied in both orders → identical stock on hand |
| 3 | Orphan row | A movement whose variant has not arrived defers, then applies when it does |
| 4 | Cursor under concurrency | A row written mid-pull is returned by the next pull, never skipped |
| 5 | Clock skew | A device 3 days slow pulls everything; its future-dated record is refused |
| 6 | No ping-pong | Apply a pulled row → the outbox stays empty |
| 7 | Trigger coverage | Every syncable table has a trigger, in the migration *and* `onCreate` — gated |
| 8 | Fresh install | A database created from entities (not migrated) syncs — the `onCreate` trap |
| 9 | Append-only holds | The source guard still passes: no `UPDATE` on any ledger table |
| 10 | A week offline | 5,000 queued rows drain in order, in batches, and the totals match |
| 11 | Connection drops mid-push | No partial local state; the outbox is intact; retry completes |
| 12 | Retry is bounded | 5 attempts, jittered, then it stops and reports — it does not hammer |
| 13 | Revoked device | Refused with 401 and stops, without losing its outbox |
| 14 | Token at rest | Desktop file is `0600`; the token never reaches the logger |
| 15 | Portable credential | A user created on device A signs in on device B, offline, after one sync |
| 16 | Record LWW | Two edits converge to the same winner on both devices; the loser is in `sync_superseded` |
| 17 | Migration v7 → v8 | Seed v7 with trading history, migrate, assert intact and triggers present |
| 18 | Rebuild from log | Drop the materialised tables, replay the log, assert the shop is identical |
| 19 | Receipt blocks | Two devices sell simultaneously → no collision, and each block is contiguous |
| 20 | First device keeps its numbering | A shop trading at receipt 412 enrols and issues 413, not 1000001 |
| 21 | Document transition | A void on device A applied on device B before *and* after the sale itself → same result |
| 22 | Unique clash on a record | Same SKU created offline on two devices → earlier wins, loser in `sync_superseded` |
| 23 | Held sales stay local | A parked basket is never enqueued, and never arrives from the server |

Checks 3, 6 and 8 are the ones that cost a weekend: a sale that arrives without its lines, a row
that bounces between two devices forever, and a fresh install that appears to work and syncs
nothing. Check 18 is the one that makes 9c's exit path a fact rather than a promise, and check 19
is the one that would otherwise take down a shop's second till on its first morning.

## Definition of Done

- [x] A second device enrols, syncs, and shows the same stock, sales and receivables
- [x] Events merge by union on UUID, with no conflict resolution anywhere in the code
- [x] Documents converge by a terminal state beating a non-terminal one, in either arrival order
- [x] Records converge by LWW, and the superseded value is kept
- [x] Two tills issue receipt numbers that cannot collide, without a schema change
- [x] Held sales and the stock projection stay local
- [x] The cursor is a server sequence, and the till never reads its own clock to decide what to pull
- [x] The outbox is trigger-driven; no repository knows sync exists; no ledger table is ever updated
- [x] Materialisation waits for whole documents
- [x] A device token is device-scoped, revocable, and stored per KD-007
- [x] A user created on one device can sign in on another, offline
- [x] Sync retries with bounded jittered backoff; nothing user-facing retries
- [x] KD-007, KD-009 and KD-010 written and accepted
- [x] v7 → v8 migration test green
- [x] `./scripts/check-gates.sh` and `./gradlew allTests` pass

## What changed while building it

**The schema was not two piles, it was three.** The plan's first draft sorted every table into
events and records. Reading the DAOs instead of assuming turned up five tables that are neither: a
sale is voided, a shift is closed, a receipt is posted. They are documents — written once, then one
monotonic step to a terminal state — and because that step is already guarded on the current state
in SQL, they merge with no conflict resolution either. 9a was rewritten before a line of it was
built.

**A trigger does not get to choose its own conflict algorithm.** `sync_outbox` was unique on
`(tableName, rowId)` so the trigger's `INSERT OR REPLACE` would coalesce a row edited five times
into one row to send. It does not work: SQLite discards a trigger body's conflict algorithm and
applies the one from the statement that fired it, so `OR REPLACE` silently became Room's `ABORT`.
The symptom was six `StockReceiptTest` failures — posting a receipt enqueues a header its own update
had already enqueued — and the cause was three layers away from them. The index is gone, duplicates
are allowed, and the push coalesces in Kotlin where the rule is visible. The duplicates turned out
to be load-bearing: a row edited *during* a push gets a higher `seq` than the watermark being
drained, which is exactly what makes that edit survive.

**Last-write-wins was deleted rather than implemented.** The plan called for LWW on `updatedAt` with
a `deviceId` tie-break and a 15-minute clock-skew guard. None of it was needed: every device applies
one log in one order, so the later entry already wins everywhere without a comparison. What *did*
need building was the opposite guard — an arriving record is skipped while the same row is pending
in the local outbox, or the person who just renamed something watches it vanish.

**The unique-clash rule had to change to work at all.** Planned as "the earlier `createdAt` wins".
Built as "the smaller id wins", because anything keyed on arrival order diverges — each device meets
the two rows in the opposite order — and not every record has a `createdAt`. The first
implementation also matched clashing columns one at a time, which found *any* variant of the same
product and deleted a row that had nothing to do with the clash. `SyncMergeTest` caught it on a
composite index within a minute of being written.

**The first migration since Phase 1 that changes no existing table.** 9d kept the outbox out of the
ledger and 9i gave each device a block of receipt numbers instead of adding a column to `sale`, so
v8 adds four tables and some triggers and touches nothing a shop already has.

**The gates earned their keep twice.** `check-gates.sh` caught the server printing a freshly minted
admin token to the console — an ADR-029 violation that was also a secret in a log file — which
became a `ServerPlatformProvider` and a server that refuses to start without `KESWA_ADMIN_TOKEN`
rather than inventing one per restart. A new gate now asserts that both the migration and `onCreate`
still install the triggers, because the failure mode there is an app that works perfectly and syncs
nothing.

**A mutex is not reentrant, and the test suite simply stopped.** `LogStore.append` finished by
calling `highWaterMark()`, which takes the same lock it was already holding. No error, no timeout —
the build just hung until it was killed. Worth recording because nothing about the code looked
wrong.
