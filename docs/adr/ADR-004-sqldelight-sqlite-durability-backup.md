# ADR-004 — SQLDelight/SQLite: migrations, durability, and backup

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0

## Context
One Windows machine in a shop with unreliable mains power holds all of the business's data. There is
no server and no second copy. The storage layer must survive power cuts and must be restorable by a
non-technical owner.

## Decision
**Library: SQLDelight over SQLite.** Typed Kotlin APIs generated from `.sq` files, multiplatform
drivers, first-class versioned migrations.

**Migrations:** every change is a new `.sqm` file; applied migrations are never edited. `1.sqm` exists
from the first commit and contains the *full* Phase 1 schema including sync scaffolding. CI enforces
(a) a **schema-hash test** — changing `.sq` without a migration fails the build — and (b) a
**migration-chain test** that migrates a fixture DB from every historical version to latest and
compares against a freshly created schema.

**PRAGMAs:** `journal_mode=WAL`, **`synchronous=FULL`**, `foreign_keys=ON`, `busy_timeout=5000`,
`wal_autocheckpoint=1000`. `PRAGMA quick_check` on every startup.

**Location:** `C:\ProgramData\Keswa\data\keswa.db` — shared across Windows accounts, never inside
`C:\Program Files`. Overridable via `KESWA_DATA_DIR` then `config\keswa.conf`.

**Backup:** `VACUUM INTO '<path>'` on app close, every 4h, and **before every migration**. Written to
the local `backups\` folder *and* an owner-configured second path (USB/network). Every backup is
verified by reopening it and running `quick_check` plus a row-count comparison, with the result
recorded in `backup_log`. Retention: 14 daily + 4 weekly + all pre-migration snapshots forever.
Restore is a guided in-app flow that snapshots the current DB aside before swapping.

## Alternatives considered
| Option | Rejected because |
|---|---|
| Room (KMP) | Younger multiplatform story, migration ergonomics weaker than SQLDelight's `.sqm`, and it hides the SQL that the reporting layer depends on. |
| Raw JDBC + hand-written mappers | Rewrites what SQLDelight generates, with no compile-time query checking, and no multiplatform path. |
| Realm / ObjectBox | Non-relational, weak ad-hoc query story for reporting, and a much harder mirror to PostgreSQL in Phase 4. |
| Embedded PostgreSQL / H2 | Heavy install footprint on a shop PC, no mobile/iOS path, and no benefit at this data size. |
| `synchronous=NORMAL` (the WAL default) | A power cut can lose the last committed transactions. A POS commits a few times per minute, so the fsync cost is negligible; a lost sale is not. **This is the single most important line in this ADR.** |
| File-copy backups of `.db`+`.wal`+`.shm` | Racy against a live writer; easy to produce a corrupt or inconsistent copy. `VACUUM INTO` is one statement and always consistent. |
| `%LOCALAPPDATA%` for the DB | Invisible to a second Windows account; the shop's other staff would see an empty database. |

## Consequences
**Good:** atomic sale commits mean a power cut yields a complete sale or no sale, never half of one;
migrations are exercised from day one rather than first attempted under pressure; a verified,
off-machine backup exists before the first real sale.

**Costs:** `synchronous=FULL` costs an fsync per commit (immaterial at POS write rates);
`VACUUM INTO` briefly doubles disk usage; the migration and schema tests are ~4h of Phase 0 work.

**Non-negotiable operational rule:** a backup that lives only on the failing machine is not a backup.
The second target is configured during installation, and the restore is **rehearsed** before go-live.
