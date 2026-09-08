# ADR-001 — Client-generated ULID identifiers

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0

## Context
Keswa starts as one offline machine and must later merge data from multiple devices and stores into a
shared PostgreSQL database. Identity must be assignable without any coordination, because there is no
server to ask when a sale is rung up.

## Decision
Every primary key is a **26-character ULID string**, generated on the device at row creation.
No `AUTOINCREMENT`, no database-assigned identity, anywhere — including lookup and settings tables.

Human-facing document numbers (`ST01-INV-000123`) are a **separate, non-identity** column produced by
a per-store counter.

## Alternatives considered
| Option | Rejected because |
|---|---|
| `INTEGER PRIMARY KEY AUTOINCREMENT` | Two devices generate the same id. Merging requires remapping every PK *and every FK*, across sales, lines, payments, movements and audit rows. Receipts already printed carry the old number. Effectively unrecoverable. |
| UUIDv4 | Works, but random ordering scatters B-tree inserts (slower writes, worse locality) and gives no time ordering. ULID is the same 128 bits with a time prefix. |
| UUIDv7 | Functionally equivalent and standardised; ULID chosen for its shorter, case-insensitive, human-copyable Base32 text form. Would be an acceptable substitute. |
| Composite `(device_id, local_seq)` | Two columns on every FK, awkward joins, and still needs a device registry before the first row exists. |
| Server-assigned ids with a local temp id | Requires a server. There isn't one for months, and offline-first means there may not be one at the moment of sale. |

## Consequences
**Good:** offline creation with zero coordination; merges are unions; ids are stable from creation and
safe to print, log and reference; sortable by creation time, so `ORDER BY id` approximates `ORDER BY created_at`.

**Costs:** 26-byte keys instead of 8 (a few MB per year — irrelevant); indexes are larger; ids are not
human-friendly, which is exactly why `doc_number` exists; ULID generation needs a small commonMain
implementation (~60 lines) with monotonic handling of same-millisecond generation.

**Enforcement:** CI greps `.sq` files for `AUTOINCREMENT`. One occurrence fails the build.
