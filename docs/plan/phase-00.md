# Phase 0 — Foundation & Catalogue

> *"The shop's inventory lives in Keswa instead of a spreadsheet, and it is backed up."*

**Effort:** ~60h · **Calendar:** ~6 weeks @10h/wk

---

## Goal
Stand up the whole technical spine — modules, schema, migrations, i18n/RTL, packaging, backup — and
prove it by delivering one genuinely useful feature: the product catalogue with a size × colour
variant matrix, an opening stock count, and Excel export.

The shop stops maintaining an inventory spreadsheet by hand. That is a real win in week 6, and it
front-loads every decision that would be expensive to change later.

## In scope

**Infrastructure**
- Gradle KMP project, Clean Architecture module graph per `docs/architecture.md` §2, `dependency-rules` convention plugin
- `:core:common`: `Money`, `CurrencyCode`, `Ulid`, `Clock`, `DeviceId`, `AppResult`
- `:data`: SQLDelight, migration `1.sqm` containing the **full Phase 1 schema** including
  `outbox_entry`, `sync_state`, `sync_conflict`, `audit_event`, and all sync columns
- `UnitOfWork` — transaction + audit + outbox choke point
- Schema-hash test and migration-chain test in CI
- `:sync:contract` module with DTOs and `ChangeEnvelope` (compiled, unused)
- `docs/sql/postgres.sql` mirror of the schema
- **MVI base** in `:core:ui/mvi` (`MviStore`, State/Intent/Effect) per `docs/presentation-architecture.md`
- DI graph (Koin), `AppPaths`, `%PROGRAMDATA%\Keswa` layout, PRAGMA configuration
- Backup: `VACUUM INTO`, scheduling, verification, retention, guided restore
- jpackage MSI installer, bundled JRE, upgrade-preserves-data test
- Arabic-first i18n + RTL shell, bundled IBM Plex Sans Arabic, no-string-literal CI check
- PIN unlock screen as the **reference Contract/Store/Route/Screen** every later screen copies

**Features**
- Products: create/edit/deactivate, Arabic + English names, category, brand
- Options and variant matrix generation (Size × Colour), SKU generation, manual barcode entry
- Opening stock via `OPENING_BALANCE` movements; stock count session (draft → post)
- Stock on hand screen with location filter
- Reports: `STOCK_ON_HAND`, `STOCK_MOVEMENTS`, `PRICE_LIST_EXPORT` + `:export:xlsx`
- CSV/Excel **import** of the owner's existing spreadsheet (one-shot, with a dry-run preview)
- Single retail price list; a variant has a price
- Local user + PIN unlock (single OWNER user is enough here), `Principal` plumbed through

## Explicitly out of scope
Selling anything · payments · customers · suppliers · purchase orders · returns · shifts · barcode
scanner hardware · receipt printing · wholesale pricing · discounts · multi-user roles · any network
code · any sync execution · auto-update · product images

## Deliverables
1. Installed MSI running on the shop PC (or a representative Windows machine)
2. Catalogue populated with the shop's real products — **the owner does this, with you watching**
3. Opening stock count posted, `stock_level` reconciled against the ledger
4. Three working reports with Arabic Excel export
5. Automatic nightly backup to a second location, plus one **rehearsed restore**
6. `docs/` updated; ADR-001…010 written

## Exit criteria
- [ ] Owner can find any product by Arabic name or SKU in under 5 seconds
- [ ] `STOCK_ON_HAND` total matches a physical spot-check of 20 items
- [ ] Excel export opens in the shop's Excel with Arabic intact, RTL sheet, money as **numeric** cells
- [ ] Killing the app mid-edit loses at most the current form, never the database
- [ ] `PRAGMA quick_check` clean after a hard power-off during a stock count post
- [ ] Restore-from-backup performed successfully on a second machine
- [ ] `./gradlew check` green: dependency rules, schema hash, migration chain, domain tests, MVI base tests
- [ ] Every screen has a Contract; no repository is reachable from a composable; no SQLDelight type appears in any `State`
- [ ] `outbox_entry` contains rows for every catalogue mutation (verified by query, not by faith)

## Migration impact
Baseline. `1.sqm` is the schema everything else migrates from. **Get the universal column contract
right here** — every later phase adding a column is trivial, but adding `tenant_id` to 30 tables in
Phase 4 is not.

## Risks
| Risk | Mitigation |
|---|---|
| KMP/Compose Desktop build friction eats week 1–2 | Timebox to 8h; fall back to a plain JVM Compose module and add KMP source sets once green |
| Owner's spreadsheet is messier than expected | Build the importer with a dry-run preview and a rejected-rows report. Budget 6h; do it *with* the owner |
| Arabic font/RTL rendering surprises on Windows | Test on the real shop PC in week 2, not week 6 |
| Over-engineering the module graph | 8 modules maximum this phase. `:feature:*` beyond catalog can wait |
| MVI ceremony slows the first screens | The base is ~120 lines and written once. If it grows past ~200, stop and adopt Orbit instead ([ADR-011](../adr/ADR-011-mvi-unidirectional-presentation.md)) |
| jpackage/MSI eats a weekend | Do it in week 3, not week 6 — packaging failures are worse when discovered late |

## Notes
- **Do packaging early.** An app that can't be installed in the shop isn't a phase deliverable.
- The importer is throwaway code with a permanent value: it is how the shop's history gets in.
- Write ADRs as you make the decisions, not at the end. They take 20 minutes each in the moment.
