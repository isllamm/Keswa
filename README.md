# Keswa

Clothing retail store management. Kotlin Multiplatform + Compose Multiplatform.

**Phase 1 target:** a Windows desktop app running on one machine in the shop, 100% offline, with
Arabic-first RTL UI and Excel export. Cloud backend, mobile apps and multi-store come later — and
must be **additive**, not a rewrite.

Status: **planning complete, no code yet.**

---

## Documents

| Document | What it covers |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Module graph, dependency rules, commonMain vs desktopMain, Windows specifics (DB location, durability, backup, barcode, printing, packaging) |
| [docs/data-model.md](docs/data-model.md) | Universal column contract, all tables, ERD, migration discipline |
| [docs/reporting-and-export.md](docs/reporting-and-export.md) | Report query layer; Excel as one renderer among several |
| [docs/sync-strategy.md](docs/sync-strategy.md) | The Phase 1 decisions that make cloud sync possible later, and the cost of skipping each |
| [docs/plan/](docs/plan/README.md) | Phases 00–05: goal, scope, exit criteria, risks, effort |
| [docs/adr/](docs/adr/README.md) | 10 architecture decision records with rejected alternatives |
| [docs/risks.md](docs/risks.md) | Scored risk register; 3-months-vs-2-years design note |
| [TASKS.md](TASKS.md) | Checkbox task list per phase, ~1–3h per task |

## Non-negotiable constraints

- **IDs** — ULID strings generated client-side. No auto-increment anywhere. ([ADR-001](docs/adr/ADR-001-client-generated-ulid-ids.md))
- **Money** — integer minor units + currency code. No `Double`, ever. ([ADR-002](docs/adr/ADR-002-money-as-integer-minor-units.md))
- **Stock** — append-only `stock_movement` ledger; levels are derived and rebuildable, never edited. ([ADR-003](docs/adr/ADR-003-stock-as-append-only-ledger.md))
- **Every mutable row** carries `tenant_id`, `store_id`, `updated_at` (UTC), `deleted_at`, `revision`, `origin_device_id`.
- **Outbox + sync cursors** exist from the first migration, though nothing drains them until Phase 4. ([ADR-007](docs/adr/ADR-007-sync-semantics-outbox-cursors.md))
- **Offline is the default code path.** Nothing may assume a network call can succeed.
- **Excel export** lives in a JVM-only module behind a shared interface, so it never blocks iOS or Web. ([ADR-006](docs/adr/ADR-006-reporting-layer-excel-as-renderer.md))
- **Arabic-first RTL** with i18n from the first screen. No hardcoded strings.
- **Audit log** for every money- or stock-affecting action, with the acting user.
- **Local backup/restore** is a Phase 0 feature. One machine is a single point of failure. ([ADR-004](docs/adr/ADR-004-sqldelight-sqlite-durability-backup.md))

## Where to start

1. Read [docs/plan/README.md](docs/plan/README.md) for the phase overview and the timeline caveat.
2. Settle the four open questions in [docs/plan/phase-01.md](docs/plan/phase-01.md) and
   [docs/sync-strategy.md §8](docs/sync-strategy.md) before writing code.
3. Work top-down through [TASKS.md](TASKS.md) Phase 0.

> Assumptions are marked **ASSUMPTION:** throughout. Correct them as you learn the real answers.
