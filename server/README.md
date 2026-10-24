# `:server`

The shop's sync server. One process, two SQLite files, no ceremony.

`:server → :core → (nothing)`. It depends on no feature module and contains no Compose. The module
hierarchy gains a second top-level consumer alongside `:composeApp`, which is the shape the rule
already allowed.

## The server is a till that never sells

A **deviation** from the architecture plan's stack table, which says *Ktor + Postgres*. Ktor stayed;
Postgres did not. The server's materialised state is the same `KeswaDatabase`, built by the same
migrations from the same entities in `:core`.

Four reasons, in the order they mattered:

1. **One definition of what a sale is.** Eight versions of hand-written, tested migrations (KD-002)
   already describe this shop's data. A second schema is a second thing to keep in step, and they
   drift the first time somebody adds a column to one and not the other.
2. **Phase 8's analytics run here unchanged.** They are pure Kotlin over repository interfaces, so
   most of the back-office half of Phase 9 is already built.
3. **The load is a rounding error** — one shop, a few devices, a few thousand rows a day. SQLite in
   WAL mode has orders of magnitude to spare.
4. **It is testable on a laptop with nothing installed.** A Postgres server means sync tests need
   Docker or get replaced by tests against a fake, and a sync engine tested only against a fake has
   never been tested.

**The exit is written down rather than hoped for.** The durable artefact is the log, not the
materialised tables — they are derived and can be rebuilt from it at any time. The day a shop
outgrows this, the migration is: stand up Postgres, replay the log, point the devices at the new
host. `MaterialiserTest` throws the shop away and rebuilds it, so that is a fact and not a promise.

## Two files

| File | What it is | Back up |
|---|---|---|
| `sync-log.db` | The log and the device registry. Append-only, plain SQLite, no Room. | **This one.** |
| `keswa.db` | The shop, materialised from the log. Derived. | Rebuildable. |

The log is deliberately dull: two tables, plain SQL, no ORM and no schema-export directory. Whatever
replays it somewhere else one day only has to read two tables.

## What it does not do

**It holds no business rules** (9h). It validates shape, authenticity and ownership and stops there.
Credit limits, totals and return windows ran on the device, in `commonMain`, before the row existed.
Checking them again here would be a second implementation of `CreditPolicy` — which is one
implementation and one thing that used to be it.

**It does not authenticate people** (9e). It authenticates *devices*. `app_user` is a record, so a
user created at the counter syncs to the handheld with its password hash, and Phase 6's
`JvmPasswordHasher` already guarantees that hash is identical on both targets. A device that has
synced can sign in someone it has never seen, offline, with no server involved.

## Running it

```bash
KESWA_ADMIN_TOKEN=<a long random value> ./gradlew :server:run
```

It refuses to start without one. A server that mints its own admin token on each restart is a server
whose token changes under whoever wrote it down; one that defaults to a constant is one anybody on
the shop's wifi can enrol a device against.

| Variable | Default |
|---|---|
| `KESWA_ADMIN_TOKEN` | *required* |
| `KESWA_PORT` | `8080` |
| `KESWA_SERVER_HOME` | `~/keswa-server` |

## Enrolling a device

```
POST /v1/admin/enrolment-code   Bearer <admin token>   → { "code": "AB12CD34" }
POST /v1/enrol                  { code, deviceId, deviceName }  → { token, ordinal }
```

`ordinal` is what stops two tills issuing the same receipt number (9i): each device sells from its
own block of a million, and the first to enrol takes ordinal 0 so a shop already trading sees its
numbering continue undisturbed.

Revocation is `POST /v1/admin/revoke?deviceId=…`. It is the control KD-007 leans on — a stolen
laptop stops syncing on its next attempt — so it is tested rather than asserted.
