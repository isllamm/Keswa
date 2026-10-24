# Keswa — Clothing Retail POS: Architecture & Build Plan

> **Status: 📝 DRAFT — awaiting review**
> **Revised 14 Sep 2026.** This is the index document; the detail lives in the per-phase plans below.
> Conventions and deviations from `kmp_cashimobile`: **`keswa-conventions-and-deviations.md`** — read
> that before any phase plan.

## Document set

| Document | Covers |
|---|---|
| **this file** | The shape of the system and why — the decisions that span phases |
| `keswa-conventions-and-deviations.md` | KD-001…006: what differs from the Cashi conventions, and why |
| `phase-0-scaffold-plan.md` | Gradle, targets, DI, CI |
| `phase-1-domain-schema-plan.md` | `Money`, catalogue, category tree, stock ledger, migrations |
| `phase-2-catalog-plan.md` | Category management, colours, barcodes |
| `phase-3-hardware-plan.md` | ESC/POS, TSPL, transports, scanner |
| `phase-4-auth-plan.md` | Users, roles, PIN/password login, permissions |
| `phases-5-to-10-outline.md` | Sell, receiving, wholesale, returns, sync, fiscal |
| `phase-8-analytics-plan.md` | Admin sales analytics |

**Prototypes** (published, interactive):
- Whole app — https://claude.ai/code/artifact/359b5298-6ab4-4307-9335-9fd06d89f597
- Analytics console — https://claude.ai/code/artifact/db86f786-02d3-4803-82c6-ee281ee8a8b1

## What changed in this revision

Four claims in the first draft were wrong, corrected after reading the `kmp_cashimobile` ADRs and
codebase, and after the sizes/categories change:

| Was | Now | Why |
|---|---|---|
| SQLDelight | **Room KMP 2.8.4** | It is what `kmp_cashimobile` already uses, with `@ConstructedBy`, per-target KSP and exported schemas |
| `:core:domain` + `:core:data` + `hardware/*` modules | **A single `:core`** with internal packages | Matches Cashi, which holds fine at 16 features |
| MockK | **Hand-written fakes** | ADR-019 — the reference project uses no mocking framework |
| Size × colour variants | **Colour only**, plus an admin-defined category tree | Owner's decision, 14 Sep |

---

## 0. Open questions

| # | Question | Blocks | Status |
|---|---|---|---|
| **Q1** | Does the *shop* sell wholesale + retail, or is the *app* sold to other shops (multi-tenant SaaS)? | Phase 7 | **Open.** Narrower than first thought — a desktop app sold to many shops is *multi-instance*, each with its own local DB, so tenancy only bites the server in Phase 9 |
| **Q2** | "The receipt will not be payment receipts" — separate payment terminal, or non-fiscal slips? | Phase 10 | **Open.** Either way §5 is an isolated module |
| **Q3** | Printers already owned, or buying? | Phase 3 | **Open.** Network printers make ~80% of the hardware layer shared code |
| **Q4** | Is quantity ever fractional (fabric by the metre)? | Phase 1 | **Open.** `Int` today; a migration later |
| **Q5** | Offline admin password recovery — recovery code, second admin, or support unlock? | Phase 4 | **Open.** No server, no email; see the auth plan |

---

## 1. What we're building

A clothing retail system with two selling modes over one catalogue and one stock ledger:

| Mode | Customer | Document | Payment | Pricing |
|---|---|---|---|---|
| **Retail** | Walk-in person | Receipt | Immediate | Retail price |
| **Wholesale** | Another shop | Invoice | On account, terms, partial payments | Price tier |

Wholesale in garments has a wrinkle worth modelling early: **assortment packs** — a carton sold as
one line at one price, exploding into per-variant movements.

---

## 2. D1 — Desktop, not web

**Compose Multiplatform targeting JVM desktop for the till. Android second. No iOS.**

The browser cannot drive POS hardware:

| Need | Browser | Desktop (JVM) |
|---|---|---|
| Network printer (TCP :9100) | ❌ No raw sockets | ✅ `ktor-network` |
| USB printer | ⚠️ WebUSB — Chrome-only, fails once the OS driver claims the device | ✅ javax.print / usb4java |
| Bluetooth printer | ❌ Web Bluetooth is BLE-only; thermal printers are BT Classic | ✅ |
| Cash drawer kick | ❌ Needs raw ESC/POS bytes | ✅ |
| Arabic on receipts | ❌ No raster control ⇒ mangled text | ✅ Bitmap rendering (D8) |
| **ETA CAdES-BES signing (USB token / HSM)** | ❌ Needs a local bridge agent | ✅ Java `SunPKCS11` |
| Offline catalogue | ⚠️ IndexedDB, fragile | ✅ SQLite |
| Barcode scanner | ✅ HID keyboard works | ✅ |

Teams that try web end up shipping a native print agent on localhost — a desktop app *plus* a web app.

**Web still has a job:** the back office in Phase 9 (reports, catalogue, multi-branch), not the till.
Desktop and Android share ~90% of the code, so tablet tills later are nearly free.

**No iOS.** MFi blocks wired and Bluetooth-Classic peripherals, and PKCS#11 signing has no path.

### D1a — "Can we do web as well?" — yes, but not as the same app

Asked 14 Sep, after Phase 2. Recorded here because the answer is not obvious and the reasoning is
worth keeping.

**Verified blocker.** `androidx.room:room-runtime` publishes for `android`, `iosArm64`,
`iosSimulatorArm64`, `iosX64`, `jvm`, `linuxArm64`, `linuxX64`, `macosArm64`, `macosX64` — checked
against the resolved Gradle module metadata. **There is no `wasmJs` or `js` target.** A browser
cannot use this database at all, so a web build has no local persistence and must talk to a server.

What *does* cross to the web unchanged, if a web build is ever made:

| Layer | Web? | Why |
|---|---|---|
| `core/domain` — `Money`, models, repository **interfaces** | ✅ | Pure Kotlin; the ADR-005 gate keeps it that way |
| `features/*/domain/usecase` — every rule | ✅ | Depends on interfaces, not Room |
| Compose UI, ViewModels | ✅ | Compose Multiplatform targets Wasm |
| `core/database` — Room, entities, DAOs | ❌ | No web target, verified above |
| Printers, cash drawer, scanner, PKCS#11 | ❌ | §2 — unchanged |

So the repository interfaces are the seam: desktop implements them against Room, a web build would
implement them against HTTP. That is a Phase 9 concern, because it needs the server to exist.

### Why not web first, then desktop

Tempting — no installer, no per-machine setup, easier to sell to other shops. Rejected:

1. **Web-first is server-first.** With no local database in the browser, the backend, hosting,
   server-side auth and KD-007 all move to the front — months of infrastructure before the shop can
   sell a single garment.
2. **The till is the product.** Scan, charge, print, hand over. A browser can do none of the
   printing half (§2), so web-first means building everything *except* what the shop opens the app to do.
3. **A web till dies with the internet.** In a shop, that is not hypothetical.
4. **It discards the reason Phase 1 is shaped as it is.** The append-only ledger exists so offline
   tills merge without conflict resolution. Server-as-truth is a different system, not a staged
   version of this one.

**The split is not desktop vs. web. It is till vs. back office** — and the plan already has both:
desktop for the till, web for the back office in Phase 9 (reports, catalogue, multi-branch, the
owner checking takings from home). Neither of those touches hardware or needs to work offline.

**No action needed now.** `core/domain` is already pure and the CI gate keeps it pure, so promoting
it to its own module when a web build arrives is mechanical rather than a refactor. Splitting it
today would be speculative generality for a consumer that does not exist yet.


---

## 3. Domain model — the part that decides success

Detail in `phase-1-domain-schema-plan.md`. The cross-cutting decisions:

### D2 — Product / Variant, with colour as the only axis

```
Category (admin-defined tree)   T-shirts → Round neck → …
  └─ Product (style)            "Round-neck t-shirt"
       └─ Variant (SKU)         Round-neck t-shirt / Navy
                                ← barcodes, cost, price, STOCK live HERE
```

- Stock **never** lives on Product.
- **Many barcodes per variant** — the supplier's EAN-13 *and* your own printed code.
- The **category tree is admin-controlled and arbitrarily deep**, with a materialised `path` column
  so "everything under T-shirts" is an indexed prefix query rather than a recursive one. It drives
  the catalogue, the filter, and every analytics rollup.
- Colours are a **shared admin-managed list**, so "Navy" means the same thing shop-wide.

> Sizes were removed on 14 Sep. A generic attribute-axis model was considered and cut — it is unused
> complexity on every query today, and the ledger is keyed on `variantId` regardless, so re-adding an
> axis later is a contained migration.

### D3 — Stock as an append-only ledger

Never `UPDATE variant SET qty = ?`.

```
StockMovement(id, variantId, locationId, qty ±, reason, refType, refId, at, userId)
onHand = SUM(qty)   -- projection table, fully rebuildable from the ledger
```

Three payoffs at once:

1. **Audit trail** — "where did these 4 shirts go?" is a query.
2. **Conflict-free sync** — movements are commutative, so two tills selling offline merge with no
   resolution logic. This deletes most of Phase 9's hard work before it is written.
3. **Correct cost** — moving-average or FIFO is derivable, so margin is real.

### D4 — Money is `Money(Long)` piastres

See **KD-001**. Cashi's ADR-013 mandates `BigDecimal`, but there is no `BigDecimal` in KMP
`commonMain` and the reference project uses `String` throughout — which works there because it
*displays* server-computed amounts. A till computes its own totals, offline, on every keystroke.
Integer minor units are exact by construction. `Double`/`Float` on money stays a 🔴 blocker.

### D5 — Returns are first class

Clothing return rates are brutal. **Print a QR of the sale ID on every receipt** so a return is one
scan. A return writes a new document referencing the original; the original is never edited.

### D6 — Roles from the start

`ADMIN` / `SELLER`, with permissions **checked in the use case layer, not hidden in the UI**.
Sellers sign in with a PIN, admins with a password — see `phase-4-auth-plan.md` for why that split
matters. `VIEW_COST_AND_MARGIN` is separate from `VIEW_SHOP_ANALYTICS`.

---

## 4. Peripherals

Detail in `phase-3-hardware-plan.md`.

### D7 — Protocol in `commonMain`, transport behind a `:core` interface

Per ADR-018, printers are an **interface in `:core` bound through Koin** — the ADR's own scope list
names `PaymentTerminal`. `expect`/`actual` is reserved for what cannot be injected.

The protocols are pure byte generation and belong in `commonMain`:

```kotlin
object EscPos { fun raster(b: MonoBitmap): ByteArray; fun cut(): ByteArray; fun kickDrawer(): ByteArray }
object Tspl   { fun label(v: Variant, price: Money): ByteArray }
```

Tested against **golden byte arrays** — full coverage with no hardware attached.

### D8 — Buy network printers

A `TcpTransport` written once with `ktor-network` runs unchanged on desktop and Android. **Zero
platform code for the whole print stack.** USB and Bluetooth are deferred to Phase 7.

### D9 — Render receipts to a bitmap, always

Thermal firmware mangles Arabic — CP864/CP1256 confusion, no shaping, no ligatures. Render the
receipt with Compose's graphics APIs (which do Arabic shaping and bidi correctly) and send it as a
raster image. Correct Arabic, correct RTL, logo included, identical on every platform.

### D10 — Scanners need no driver

USB and Bluetooth scanners are **HID keyboards**. A focused capture with a timing heuristic
(< 30 ms between characters ⇒ scanner) is all it takes, and it is 100% shared code.

### Hardware buying notes

- **Hang tags need thermal *transfer* (ribbon), not direct thermal.** Direct thermal fades in
  sunlight and dies against fabric — tags unreadable before the garment sells.
- Pick **one label-printer vendor family** — TSPL (TSC/Xprinter) or ZPL (Zebra) — and stay in it.
- Receipt printer: any ESC/POS with an Ethernet port.

---

## 5. Tax & compliance — isolated, optional module

**Gated on Q2.** Built as `:features:fiscal` behind an interface so it can be dropped or deferred.

Egypt runs **two separate regimes**, matching the two selling modes in §1:

| | B2B — other shops | B2C — persons |
|---|---|---|
| System | **e-Invoice** | **e-Receipt** |
| Signing | CAdES-BES (ITIDA) via **USB token or HSM** | e-seal certificate; POS registered with ETA |
| Note | USB token needs a local agent, suits low volume; HSM or cloud signing for automation | From 2026 the printed receipt must carry an **ETA QR** linking to the validated record |

### R1 — Offline-first vs. ETA submission

A POS must sell when the internet is down; ETA wants the receipt submitted. The reconciliation is the
**24-hour submission window** — sell offline, queue, submit within 24h. But **the QR depends on ETA
acceptance**, so a receipt printed offline cannot carry a valid one. Options, decided at Phase 10:

1. Block printing until submitted — unacceptable, kills offline selling.
2. Print without QR; deliver the compliant copy once accepted.
3. Print a provisional slip; deliver the fiscal receipt digitally.

⚠️ **Verify against the official ETA SDK docs before building.** The summaries here come from
secondary sources. This is the highest-uncertainty area in the plan.

---

## 6. Offline-first

**SQLite is the source of truth, not a cache.** The till never blocks on the network.

Sync (Phase 9) is hard in one place and trivial elsewhere:

- **Stock movements** — append-only, commutative ⇒ merge with no conflict resolution.
- **Catalogue** — pulled with a cursor; it is the one mutable pile and the only one that can conflict.
- **Documents** — client-generated UUIDs, idempotent upsert (`cashi_pax` defect F2: non-idempotent
  IDs create duplicates).

> **Built in Phase 9, and one line of this needed correcting.** The catalogue is not "server
> authoritative" — a till creates products too. It converges instead on the log's own order, which
> every device reads identically. The three-way split that actually fell out of the schema (events,
> documents, records) is KD-010.

Because the local DB is the source of truth, **`fallbackToDestructiveMigration` is a blocker** and
every schema change ships a tested migration — see **KD-002**.

---

## 7. Module structure

Following `kmp_cashimobile` exactly: `composeApp → features:* → core → (nothing)`.

```
keswa/
├── composeApp/             desktop (+ android from Phase 6) entry points, DI root, nav
├── core/
│   ├── domain/             money, models, repository interfaces — pure Kotlin
│   ├── data/               repository impls, mappers
│   ├── database/           Room: KeswaDatabase, entities, DAOs, migrations
│   ├── platform/           IPlatformProvider, IReceiptPrinter, IBarcodeScanner, IPasswordHasher
│   ├── printing/           EscPos · Tspl · ReceiptRenderer · TcpTransport (pure commonMain)
│   └── coroutines/         DispatcherProvider
└── features/
    ├── auth/  catalog/  sell/  inventory/  purchasing/
    ├── customers/  analytics/  settings/
    └── fiscal/             ETA — isolated, optional (§5)
```

Hard rule, inherited: **`:core/domain` depends on nothing.** No Compose, no Room, no Koin, no
resource IDs. Defects L1–L3 in the `cashi_pax` review were all violations of exactly this.

---

## 8. Stack

Seeded from `kmp_cashimobile`'s version catalog so the two projects stay in step.

| Concern | Choice | Note |
|---|---|---|
| UI | Compose Multiplatform 1.9.3 | Version pinned deliberately upstream — keep the pin |
| DB | **Room KMP 2.8.4** + `sqlite-bundled` | `@ConstructedBy`, per-target KSP, exported schemas |
| Network | Ktor 3.4.0 + `ktor-network` | the latter drives printers |
| DI | Koin 4.1.1 | |
| Barcode / QR | `zxing-core`, `qrose` | already proven in Cashi's common code |
| Testing | kotlin-test, coroutines-test, **hand-written fakes** | ADR-019 — no mocking framework |
| Backend (Ph. 9) | Ktor + **SQLite**, not Postgres | Deviation, taken when Phase 9 was built — see `phase-9-sync-plan.md` §9c and `server/README.md`. The server reuses `:core`'s schema and migrations, so there is one definition of what a sale is; the log is the durable artefact, so replaying it into Postgres is the exit whenever a shop outgrows this |

---

## 9. Roadmap

| Phase | Deliverable | Note |
|---|---|---|
| **0** | Scaffold, CI, desktop window that launches | |
| **1** | `Money`, category tree, catalogue, stock ledger, migration harness | Foundation |
| **2** | Catalogue: categories, colours, barcodes | |
| **3** | **Hardware spike — print a real receipt and hang tag** | ⚠️ **De-risk early**; parallel with 2 and 4 |
| **4** | **Users, roles, PIN/password login** | Before selling — the ledger's `userId` is immutable |
| **5** | Sell flow: scan → cart → tender → print, plus shifts | First usable till |
| **6** | Receiving by colour, labels, blind counts, **+ Android target** | Closes the inventory loop |
| **7** | Wholesale: customers, price tiers, credit/AR, packs | ⚠️ Gated on Q1 |
| **8** | Returns, exchanges, **admin analytics** | |
| **9** | Ktor backend, sync, web back office, multi-branch | The "then online" half |
| **10** | ETA fiscal integration | ⚠️ Gated on Q2 |

Phases 3 and 4 both depend only on Phase 1 and share nothing but `:core` — two parallel tracks.

---

## 10. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | ETA offline/QR timing (§5) | Verify against official SDK docs before Phase 10. Isolated module limits blast radius |
| R2 | Arabic rendering on thermal hardware | D9 bitmap rendering, proven in Phase 3 on the real printer with real product names |
| R3 | Q1 answered "SaaS" after Phase 2 | Tenancy is a Phase 9 server concern; local schema is unaffected either way |
| R4 | Wrong label stock ⇒ unreadable tags | Thermal transfer + ribbon, tested against sunlight and fabric before bulk purchase |
| R5 | **KD-006 — no desktop secure storage** | ADR-038 has no JVM answer. Dodged through Phase 8 (sessions in memory, no tokens); **must be decided in Phase 9** |
| R6 | Adding Android at Phase 6 surfaces `java.*` in `commonMain` | KD-004 bans it from day one; CI grep gate from Phase 0 |
| R7 | Scope creep into an online store | Explicitly out of scope until after Phase 9 |

---

## Sources

- [Egypt E-Receipt Requirements: ETA Guide for 2026](https://invoicedataextraction.com/blog/egypt-e-receipt-requirements)
- [Egypt's E-Receipt System — Wafeq](https://www.wafeq.com/en-eg/tax-and-reporting/e-receipt-system)
- [Egypt B2C e-Receipt Mandate — Flick Network](https://www.flick.network/en-eg/egypt-b2c-e-receipt-mandate)
- [ETA E-Invoicing Egypt: FAQ & Integration Guide](https://orchidatax.com/eta-e-invoicing-egypt-faq/)
- [ETA — Digital Signature Format for E-Invoice (official PDF)](https://www.eta.gov.eg/sites/default/files/2021-09/Digital%20Signature%20Format%20V1.1_final_0.pdf)
- [e-Invoicing in Egypt — ClearTax](https://www.cleartax.com/eg/en/e-invoicing-egypt)
