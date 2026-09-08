# Keswa — Risk Register

Scored as **Impact × Likelihood**, both 1–5. Owner is you; the column names the person or artifact
that carries the mitigation. Reviewed at the end of every phase.

---

## Top risks

| # | Risk | I | L | Score | Mitigation | Early warning sign |
|---|---|---|---|---|---|---|
| R1 | **Timeline vs capacity.** ~420h of plan at 10h/week ≈ 10 months; a cloud backend is wanted in 3 | 5 | 5 | **25** | Ship Phase 0+1 to the shop first (~15 weeks), then the backend. Or redefine "online in 3 months" as contract + DDL + server scaffold. See `sync-strategy.md` §8 | Phase 0 slips past week 8 |
| R2 | **The one shop machine dies or is stolen.** Everything is on it | 5 | 3 | **15** | Backups from Phase 0 to a second location, verified automatically, restore **rehearsed** on a different machine before go-live. Keep a spare Windows machine identified | A backup verification failure ignored for more than a day |
| R3 | **Cashier abandons the app** because it is slower than the notebook | 5 | 3 | **15** | Keyboard-first POS designed with the actual cashier before coding; the 5-line-sale-in-40-seconds exit criterion; watch a real shift without helping | Cashier keeps a paper list "just for busy times" |
| R4 | **Trust collapse from one wrong number.** Owners do not give a second chance to a system that got the till wrong | 5 | 3 | **15** | Reconcile every report by hand against a real trading day before showing it. Invariant tests on sale totals. Ledger rebuild as a provable recovery | Owner starts re-checking totals in Excel |
| R5 | **Solo bus factor / motivation over 10 months** | 4 | 4 | **16** | Every phase ends usable, so the project has value even if it stops; ADRs and docs make a handover possible; keep phases short enough to feel finished | Two consecutive weeks with no commits |
| R6 | **Arabic on thermal printers** does not work as expected | 4 | 3 | **12** | Raster rendering (ADR-010) sidesteps codepages. Buy the printer and test in week 1 of Phase 3. A4/PDF receipts already work as a fallback | Printer arrives and the first raster test is deferred |
| R7 | **Scope creep once the shop starts using it.** Every trading day generates requests | 4 | 4 | **16** | A visible "next phase wishes" list; nothing enters the current phase unless it blocks a sale; phase docs state out-of-scope explicitly | The current phase gains its third "small addition" |
| R8 | **Barcode scanning breaks under an Arabic keyboard layout** | 3 | 4 | **12** | Read physical key codes, not typed characters; configurable prefix/terminator; explicit test with the Windows input language set to Arabic | Testing scanning only with an English layout |
| R9 | **Phase 4 needs local schema changes**, invalidating the "no rewrite" premise | 4 | 3 | **12** | Full column contract in `1.sqm`; outbox written from day one; `:sync:contract` in Phase 0; two-database merge test in Phase 1 | The first sync spike needs a column that doesn't exist |
| R10 | **Data entry never finishes.** The catalogue is only half in the app | 4 | 3 | **12** | Importer with dry-run preview built in Phase 0; do the first import *with* the owner; treat "catalogue populated" as a Phase 0 exit criterion, not a nice-to-have | Phase 1 starts with the catalogue below 80% complete |
| R11 | **Power cuts corrupt the database** | 5 | 2 | **10** | WAL + `synchronous=FULL`; single-transaction sale completion; `quick_check` on startup; scripted power-off test 10× as a Phase 1 exit criterion | Any `quick_check` failure at all |
| R12 | **Costs are wrong**, so every margin report is fiction | 3 | 4 | **12** | Capture real costs during the Phase 0 opening count; landed-cost allocation in Phase 2; show both average and last-purchase cost | Opening stock entered with guessed or zero costs |
| R13 | **Unsigned MSI blocked by SmartScreen or the shop's antivirus** | 3 | 3 | **9** | Test the installer on the actual shop PC in Phase 0; decide on a code-signing certificate before multi-store rollout | Install requires a workaround the owner can't repeat |
| R14 | **KMP/Compose Desktop friction** eats disproportionate time | 3 | 3 | **9** | Timebox the initial setup to 8h; fall back to a plain JVM Compose module and add KMP source sets once green — the module boundaries make this reversible | Week 2 of Phase 0 still has no running window |
| R15 | **Returns policy disputes** after launch (time limits, receipt required, refund method) | 3 | 3 | **9** | Write the policy into `phase-01.md` **with the owner** before coding it; make limits settings | The first argued return happens before the policy is written down |
| R16 | **Backend hosting/ops is unfamiliar work** | 3 | 3 | **9** | Budget 10h of Phase 4 for deployment, TLS, monitoring and a restore rehearsal; keep the server small and boring | Phase 4 planning has no deployment line item |
| R17 | **Currency/inflation:** amounts outgrow assumptions, or redenomination happens | 3 | 2 | **6** | `Long` minor units; `minor_unit_exponent` as data; no hardcoded formatting | Any total displayed wrongly at large magnitudes |
| R18 | **Cross-store data leak** from a query missing a `store_id` filter | 4 | 2 | **8** | CI grep over report `.sq` files; a two-store seeded test in Phase 5 | Any report that "works" without a store parameter |

---

## Risks accepted without mitigation

| Risk | Why accepted |
|---|---|
| The local database is **not encrypted at rest** | Threat model is staff misuse and attribution, not an attacker with physical access. Encryption adds key management the owner cannot operate. Revisit if the machine leaves the shop. |
| LWW can lose a catalog edit under sync | Acceptable for a product name; money and stock cannot reach a conflict path by design (ADR-007). |
| No automated UI tests before Phase 4 | Not affordable solo at 10h/week. Domain and data tests carry the correctness weight. |
| No real-time sync | A clothing shop does not need sub-minute propagation. Polling is simpler to operate. |
| No accounting/GL export in the plan | Ledgers hold the raw material; build it when an accountant actually asks. |

---

## What I would design differently: online in 3 months vs 2 years

The full version is in `sync-strategy.md` §8. In short:

**Because you said ~3 months, do these now** — write `:sync:contract` in Phase 0; keep
`docs/sql/postgres.sql` mirrored with every migration; provision real ULID `tenant_id`/`store_id`
values from the first row rather than `"default"`; make `ReportRequest`/`ReportResult` and all sync
DTOs `@Serializable` immediately; avoid building any local-only feature with no server story; settle
the auth boundary (`Principal`, permission codes as data) before the till goes live. And still
**resist building the sync engine early** — there is no server to test it against, and unused
protocol code rots.

**If it were 2 years**, I would skip `:sync:contract` and the mirrored DDL entirely (both would drift
past usefulness), defer the backend technology choice, allow a richer local-only model, and spend the
saved hours on shop-facing features — two years of a happier shop is worth more than an early protocol.

**What is identical in both worlds:** ULID keys, the universal column contract, append-only ledgers,
integer money, the outbox, and soft deletes. That they don't change with the timeline is the evidence
they are the right Phase 1 investments rather than a bet.

**The honest caution:** picking "3 months" and then running a 10-month plan is the most likely way
this project gets into trouble — not technically, but by measuring itself against a date it was never
going to meet. Pick option 1 or option 2 in `sync-strategy.md` §8 explicitly, write it down, and tell
the shop owner which one it is.
