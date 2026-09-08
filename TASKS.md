# Keswa — Task List

Tasks are sized for **one sitting (1–3h)**. `[2h]` is the rough estimate. Order within a phase is
mostly the order to do them in; `⚠️` marks a task that unblocks several others — do it early.

Docs: [architecture](docs/architecture.md) · [data model](docs/data-model.md) ·
[reporting](docs/reporting-and-export.md) · [sync](docs/sync-strategy.md) ·
[plan](docs/plan/README.md) · [ADRs](docs/adr/README.md) · [risks](docs/risks.md)

---

## Phase 0 — Foundation & Catalogue (~55h)

### Project setup
- [ ] ⚠️ Create the KMP project: Gradle version catalog, Kotlin/Compose MP versions, `:app:desktop` with a running window `[3h]`
- [ ] Add module skeletons: `:core:common`, `:core:ui`, `:domain`, `:data`, `:reporting`, `:export:api`, `:printing:api`, `:sync:contract` `[2h]`
- [ ] Write the `dependency-rules` Gradle convention plugin and wire it into `check` `[2h]`
- [ ] Add a failing-then-passing test proving a forbidden edge (`:feature` → `:data`) breaks the build `[1h]`
- [ ] Set up CI (build + test + dependency rules) `[2h]`

### Core primitives
- [ ] ⚠️ `Money` (Long minor + currency), arithmetic, mixed-currency rejection, largest-remainder allocator + tests `[3h]`
- [ ] `Ulid` generator with monotonic same-millisecond handling + tests `[2h]`
- [ ] `Clock` interface, `SystemClock`, `TestClock`; ban `System.currentTimeMillis()` via CI grep `[1h]`
- [ ] `AppResult` sealed type + error taxonomy `[1h]`
- [ ] `DeviceId`: generate on first run, persist in `keswa.conf` `[1h]`

### Database
- [ ] ⚠️ SQLDelight plugin, JVM driver, `AppPaths` for `%PROGRAMDATA%\Keswa`, PRAGMA setup `[3h]`
- [ ] ⚠️ Write `1.sqm`: **full Phase 1 schema** with the universal column contract, ledgers, `outbox_entry`, `sync_state`, `sync_conflict`, `audit_event`, `document_counter` `[4h]`
- [ ] Schema-hash test + migration-chain test `[2h]`
- [ ] `UnitOfWork`: single-writer dispatcher, transaction + audit + outbox in one place `[3h]`
- [ ] Seed data: tenant, store, default location, currency, retail price list, OWNER user `[2h]`
- [ ] Mirror the schema to `docs/sql/postgres.sql` `[2h]`

### Backup & durability
- [ ] `VACUUM INTO` backup with verification (`quick_check` + row counts) and `backup_log` `[3h]`
- [ ] Backup scheduling (on close, every 4h, pre-migration) + retention pruning `[2h]`
- [ ] Second backup target (USB/network path) configurable in Settings `[1h]`
- [ ] Guided restore flow: pick file → verify → snapshot current → swap → restart `[3h]`
- [ ] Startup `quick_check` with a blocking error screen offering restore `[2h]`

### UI shell & i18n
- [ ] ⚠️ App shell: RTL layout direction, theme, bundled IBM Plex Sans Arabic `[3h]`
- [ ] i18n setup: `strings.xml` + `values-ar`, locale switch in Settings, CI grep banning literals in `:feature:*` `[2h]`
- [ ] Navigation: sealed `Screen` + stack, sidebar, PIN unlock screen `[3h]`
- [ ] Shared components: data table, search field, money field, quantity stepper, confirm dialog `[3h]`

### Catalogue feature
- [ ] `:feature:catalog` module; product list with Arabic search on `name_sort` `[3h]`
- [ ] Product create/edit: names, category, brand, tax rate, active flag `[3h]`
- [ ] Category & brand management `[2h]`
- [ ] ⚠️ Option/variant matrix editor: define options, generate cross-product, untick combinations, 200-variant guard `[4h]`
- [ ] SKU generation rules + manual override; uniqueness validation `[2h]`
- [ ] Barcode entry per variant (manual), multiple codes, primary flag `[2h]`
- [ ] Variant price entry against the retail price list `[2h]`

### Stock
- [ ] Stock movement repository + `rebuildStockLevels()` `[3h]`
- [ ] Ledger↔level reconciliation property test `[2h]`
- [ ] Opening balance entry (emits `OPENING_BALANCE` movements with cost) `[2h]`
- [ ] Stock count session: draft → count → post as `COUNT_ADJUST` `[3h]`
- [ ] Stock on hand screen with location and category filters `[2h]`

### Reporting & export
- [ ] `:reporting` model: `ReportSpec`, `ColumnSpec`, `Cell`, `ReportResult`, `@Serializable` `[3h]`
- [ ] `SqlReportEngine` + `STOCK_ON_HAND` query and spec `[3h]`
- [ ] `STOCK_MOVEMENTS` and `PRICE_LIST_EXPORT` `[2h]`
- [ ] `ComposeTableRenderer` with sorting, column visibility, totals row `[3h]`
- [ ] ⚠️ `:export:xlsx` with POI: RTL sheet, numeric money cells, date serials, header styling, meta sheet `[4h]`
- [ ] Excel golden-file test (Arabic round-trip, numeric cells, RTL flag) `[2h]`
- [ ] Export UX: button, folder default, background progress, open-folder `[2h]`

### Import & packaging
- [ ] CSV/XLSX importer: column mapping, dry-run preview, rejected-rows report `[4h]`
- [ ] ⚠️ jpackage MSI: bundled JRE, app icon, version, `ProgramData` ACLs `[3h]`
- [ ] Upgrade test: install v1 → add data → install v2 → data intact `[2h]`
- [ ] Install on the shop PC and test Arabic rendering + Excel export there `[2h]`

### Wrap-up
- [ ] Write ADR-001…010 as decisions are made (not at the end) `[3h]`
- [ ] Verify `outbox_entry` has rows for every catalogue mutation `[1h]`
- [ ] Populate the real catalogue with the owner; post the opening count `[4h — with the owner]`
- [ ] Rehearse a restore on a second machine `[2h]`

---

## Phase 1 — Point of Sale (~95h)

### Domain
- [ ] Price resolution policy (customer → channel → default, min_qty) + tests `[3h]`
- [ ] Discount policy: line and document, percent/amount, role limits, largest-remainder allocation + tests `[3h]`
- [ ] Sale totals calculation with the `Σ(lines) == Σ(payments)` invariant test `[2h]`
- [ ] Return rules: link to original line, cumulative-quantity limit, time limit setting + tests `[3h]`
- [ ] Shift math: expected cash from `cash_movement` + tests `[2h]`

### Users & auth
- [ ] `Principal`, `AuthGateway`, `LocalAuthGateway` with Argon2id PIN hashing `[3h]`
- [ ] Lockout after failed attempts; `must_change_pin` flow `[2h]`
- [ ] User management screen (create, deactivate, reset PIN, assign role) `[3h]`
- [ ] `role_permission` seeding + `Principal.requires(permission)` helper `[2h]`
- [ ] Fast user switching at the till `[2h]`

### POS
- [ ] ⚠️ Design the keyboard flow on paper **with the cashier**; write down the key map `[2h — with the cashier]`
- [ ] POS screen layout: search, cart, totals panel, action bar `[4h]`
- [ ] Product search by SKU/name/manual barcode, add line, keyboard-only `[3h]`
- [ ] Line editing: quantity, price override (permissioned), line discount, remove `[3h]`
- [ ] Document discount + channel selector `[2h]`
- [ ] Held/parked sales: multiple DRAFTs, resume, discard `[3h]`
- [ ] Payment dialog: cash with tendered/change, card, transfer `[3h]`
- [ ] Split payments (multiple payment rows) `[2h]`
- [ ] ⚠️ Sale completion in one transaction: number, movements, payments, audit, outbox `[4h]`
- [ ] Sale search/history screen with filters `[3h]`
- [ ] Void a sale: reversing movements, opposite payments, reason, audit `[3h]`

### Returns & exchanges
- [ ] Return flow: find original by number/scan, select lines and quantities `[4h]`
- [ ] Signed-quantity return document + refund payment (OUT direction) `[3h]`
- [ ] Exchange: negative and positive lines in one document, settle the delta `[3h]`

### Shifts & cash
- [ ] Shift open with float; enforce "no sale without an open shift" `[2h]`
- [ ] Pay-in / pay-out / drop with reason `[2h]`
- [ ] Shift close: counted vs expected, variance reason, audit `[3h]`
- [ ] Z-report screen + print `[3h]`

### Receipts
- [ ] `ReceiptDocument` model in `:printing:api` `[2h]`
- [ ] A4/PDF renderer via PDFBox with Arabic and shop header settings `[4h]`
- [ ] Settings: shop name, address, phone, footer, logo `[2h]`
- [ ] Reprint from any sale `[1h]`

### Reports
- [ ] `SALES_SUMMARY` (day/week/month grains) `[3h]`
- [ ] `SALES_BY_PRODUCT` and `SALES_BY_CATEGORY` `[3h]`
- [ ] `SALES_BY_USER` and `PAYMENTS_BREAKDOWN` `[3h]`
- [ ] `SHIFT_RECONCILIATION` (Z-report) `[2h]`
- [ ] `RETURNS` and `AUDIT_TRAIL` `[3h]`
- [ ] Reconcile every report by hand against one real trading day `[3h]`

### Hardening
- [ ] Scripted power-off test during sale completion, 10 iterations `[2h]`
- [ ] Arabic error messages for every domain error `[2h]`
- [ ] Two-database merge test (sync assumption check, `sync-strategy.md` §6) `[3h]`
- [ ] Outbox completeness property test `[2h]`
- [ ] Cashier training + one week of parallel running with the notebook `[4h — in the shop]`

---

## Phase 2 — Purchasing & Customer Credit (~55h)

### Suppliers & purchasing
- [ ] Supplier CRUD + list `[2h]`
- [ ] Purchase order: header, lines, draft → ordered `[4h]`
- [ ] PO list with status and outstanding quantities `[2h]`
- [ ] Goods receipt against a PO, partial receipts `[4h]`
- [ ] Direct receipt without a PO `[2h]`
- [ ] ⚠️ Landed cost allocation by value + weighted-average cost update with rounding carry + tests `[4h]`
- [ ] Return to supplier `[3h]`
- [ ] `supplier_ledger` (A/P) entries on receipt and payment `[3h]`

### Customers & credit
- [ ] Customer CRUD, phone lookup, credit limit, price list assignment `[3h]`
- [ ] Customer selection at the POS `[2h]`
- [ ] `CUSTOMER_CREDIT` payment method → `customer_ledger` CHARGE `[3h]`
- [ ] Credit limit check + permissioned override with audit `[2h]`
- [ ] Payment against balance (partial, multiple), refund, adjustment, write-off `[4h]`
- [ ] `customer_balance` materialization + `rebuildCustomerBalances()` + reconciliation test `[3h]`
- [ ] Customer detail screen with ledger history `[3h]`

### Reports
- [ ] `CUSTOMER_BALANCES` with aging buckets `[3h]`
- [ ] `CUSTOMER_STATEMENT` (printable, Arabic) `[3h]`
- [ ] `PURCHASES_BY_SUPPLIER` and `SUPPLIER_BALANCES` `[3h]`
- [ ] `PROFIT_MARGIN` from cost snapshots `[3h]`
- [ ] `STOCK_VALUATION` and `LOW_STOCK` with reorder points `[3h]`
- [ ] Hand-verify cost and margin figures on 20 real products with the owner `[2h]`

---

## Phase 3 — Hardware & Speed (~40h)

- [ ] Buy the printer and scanner **before starting this phase** `[—]`
- [ ] ⚠️ `ScanBuffer` state machine in commonMain + unit tests `[3h]`
- [ ] Window-root key interception; scan works regardless of focus `[3h]`
- [ ] ⚠️ Arabic keyboard-layout handling via physical key codes; test with the Windows input language set to Arabic `[3h]`
- [ ] Configurable prefix/terminator/min-length in Settings `[1h]`
- [ ] Scan-to-add at the POS; scan-to-find in catalogue, count and receiving `[3h]`
- [ ] Barcode generation (CODE128, internal prefix) for products without one `[2h]`
- [ ] Label layout + printing to a label printer and to an A4 label sheet `[4h]`
- [ ] ⚠️ Receipt raster renderer: Java2D layout → 1-bit bitmap at printer dot width `[4h]`
- [ ] ESC/POS transport: `javax.print` RAW, `GS v 0`, feed, cut `[3h]`
- [ ] Printer selection + test print in Settings `[2h]`
- [ ] Cash drawer kick on cash sales only `[1h]`
- [ ] Arabic reader reviews a real printed receipt; fix layout `[2h]`
- [ ] Performance pass: profile reports and search with real data, add indexes `[3h]`
- [ ] `SALES_BY_HOUR` and `DEAD_STOCK` reports `[3h]`
- [ ] Manual update check: `latest.json`, download, SHA-256 verify, launch installer `[3h]`
- [ ] Diagnostics screen: logs, DB location, version, copy-diagnostics button `[2h]`

---

## Phase 4 — Backend & Sync (~90h)

### Server
- [ ] Ktor project, Postgres, Flyway; schema from `docs/sql/postgres.sql` `[4h]`
- [ ] `server_seq BIGSERIAL` + sync indexes per table `[3h]`
- [ ] Tenant/store/device provisioning + pairing codes `[4h]`
- [ ] Auth: user login → JWT with claims, refresh, device tokens `[5h]`
- [ ] `POST /sync/push` with idempotency on `(device_id, operation_id)` `[6h]`
- [ ] `POST /sync/pull` with per-table cursors and batching `[5h]`
- [ ] Deferred FK constraints within a batch `[2h]`
- [ ] Conflict application per the policy table, server side `[4h]`
- [ ] Server-side report endpoints for a chosen subset `[4h]`
- [ ] Deployment: VM/container, TLS, automated Postgres backups, monitoring `[6h]`
- [ ] Restore rehearsal for the server database `[2h]`

### Client
- [ ] `:sync:engine` skeleton: scheduler, state machine, status model `[4h]`
- [ ] Outbox drain with batching, backoff and resumability `[5h]`
- [ ] Cursor pull + apply, in FK dependency order `[5h]`
- [ ] Conflict handling → `sync_conflict` + review screen `[4h]`
- [ ] Rebuild materialized levels/balances for touched entities after a pull `[3h]`
- [ ] `JwtAuthGateway` replacing `LocalAuthGateway`; PIN retained as a local unlock `[4h]`
- [ ] Sync status UI: last sync, pending count, errors, manual sync `[3h]`
- [ ] Offline verification: every screen works with the network unplugged `[3h]`

### Verification
- [ ] Two-device convergence test suite `[4h]`
- [ ] 24h-offline replay test with 200 sales `[3h]`
- [ ] Tombstone and idempotency tests `[2h]`
- [ ] Initial historical upload, run overnight, verified against local totals `[3h]`
- [ ] Two-week shadow-mode run with the second device read-only `[2h setup]`
- [ ] Confirm **no local schema migration** was needed; note any that were `[1h]`

---

## Phase 5 — Multi-store, Mobile, Web (~85h)

### Multi-store
- [ ] Store #2 provisioning: locations, users, price lists, counter prefixes `[3h]`
- [ ] Store switcher + per-store scoping audit of every query `[4h]`
- [ ] CI grep asserting report queries filter `tenant_id` and `store_id` `[2h]`
- [ ] Two-store seeded leak test `[3h]`
- [ ] Stock transfer document: dispatch → `TRANSIT` → receive `[5h]`
- [ ] Transfer discrepancy handling and report `[3h]`
- [ ] Cross-store stock visibility at the POS `[3h]`
- [ ] Consolidated reports with a store dimension and an all-stores mode `[5h]`

### Mobile
- [ ] `:app:android` module, Android theme, RTL check `[4h]`
- [ ] `HttpReportEngine` + remote repositories `[5h]`
- [ ] JWT login on Android `[3h]`
- [ ] Owner dashboard: today's sales, cash, low stock, top products `[6h]`
- [ ] Report screens reusing `:feature:reports` unchanged — **verify no `:domain` change was needed** `[3h]`
- [ ] Play Store internal distribution `[3h]`

### Web
- [ ] ADR: Compose Wasm owner portal vs a separate customer-facing site `[2h]`
- [ ] Implement the chosen path (scope depends on the ADR) `[25h+]`
