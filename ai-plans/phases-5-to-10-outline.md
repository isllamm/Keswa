# Phases 5–10 — Outline

> **Status: 📝 DRAFT — awaiting review**
> Deliberately lighter than Phases 0–4. Each gets its own full plan **when it is next**, not now.
> **Renumbered 14 Sep 2026** — `phase-4-auth-plan.md` was inserted, shifting everything after it by one.

## Current sequence

| # | Phase | Plan |
|---|---|---|
| 0 | Scaffold | `phase-0-scaffold-plan.md` |
| 1 | Domain & schema | `phase-1-domain-schema-plan.md` |
| 2 | Catalogue | `phase-2-catalog-plan.md` |
| 3 | Hardware | `phase-3-hardware-plan.md` |
| **4** | **Users, roles & login** | `phase-4-auth-plan.md` ← **new** |
| 5 | Sell flow | `phase-5-sell-plan.md` ✅ built |
| 6 | Receiving, labels, counts + Android | `phase-6-receiving-plan.md` ✅ built |
| 7 | Wholesale | `phase-7-wholesale-plan.md` ✅ built (Q1 = wholesale + retail) |
| 8 | Returns, exchanges & analytics | `phase-8-returns-plan.md` ✅ · `phase-8-analytics-plan.md` ✅ |
| 9 | Backend, sync, web back office | `phase-9-sync-plan.md` ✅ · back office outlined below |
| 10 | ETA fiscal | **all but removed** — Q2 = non-fiscal slips |

**Why auth went in at 4, not later:** Phase 1 stamps `userId` on every stock movement, and the ledger
is append-only by design. Ship selling before login and every sale in the shop's permanent history is
attributed to a placeholder that cannot be corrected afterwards.

## Why 7–10 are outlines

Phases 0–6 are detailed because they are ready to execute and their decisions are irreversible.
These are not:

1. **Phases 7 and 10 are gated** on Q1 and Q2. Detailing them now means writing two plans and
   discarding one.
2. **Each phase teaches the next one things.** Phase 5 alone turned up three gaps Phases 1–4 had
   left open, none of which a plan written in advance would have named.
3. Detailed plans written six phases early read as rigour and function as fiction.

---

## Phase 5 — Sell flow ✅

**Built. Full plan: `phase-5-sell-plan.md`,** which supersedes the sketch below. Kept for the
record, since the outline is where these decisions were first framed.

**Goal:** scan → cart → tender → print → stock movement. The first genuinely usable till.

| # | Decision |
|---|---|
| 5a | **Transaction boundary.** Sale + lines + payments + stock movements commit in **one Room transaction**. Printing happens after, outside it (Phase 3's rule). |
| 5b | **Cart is domain state, not UI state.** `CalculateBasketTotalUseCase` per ADR-021 — a ViewModel must not compute totals. Uses `Money.allocate` for order-level discounts so lines reconcile to the header. |
| 5c | **Tender model.** Cash, card, and (Q2) possibly a Cashi/PAX terminal. Split tender and change due from day one — retrofitting split payment touches every payment path. |
| 5d | **Shift management** — deferred here from the auth plan. A seller opens a shift with a cash float and closes it with a count; sales attribute to a shift, and the Z-report is how till discrepancies get caught. It is a cash-handling feature, not an auth one, which is why it sits here. |
| 5e | **Held sales.** A customer goes to fetch another colour; the till must serve the next person. Park and resume. |

**Consumes from Phase 4:** the `SELL` permission, the signed-in `userId` on every movement, and
re-authentication for price overrides and voids.

**Left behind for Phase 6:** `LockoutPolicy`, the salt codec and `TransportFactory` now live in
`:core`, because two features each need them.

**Risks:** scanning an unknown barcode mid-sale (fast "create on the fly" path, or refuse?);
negative stock (in practice **allow with a warning** — the stock figure is more often wrong than the
customer's hands).

> **Both were settled in the build:** an unknown barcode is refused with a search fallback (5h), and
> negative stock completes with a warning returned from the use case (5i). Building it also turned
> up three gaps Phases 1–4 had left open — no selling price, no seeded location, no shop on the
> receipt — all closed in Phase 5.

---

## Phase 6 — Receiving, labels, stock counts + Android target ✅

**Built. Full plan: `phase-6-receiving-plan.md`,** which supersedes the sketch below and records
two deviations from it: goods receipts in place of purchase orders, and a pasted-text import in
place of a file dialog.

**Goal:** close the inventory loop. Stock comes in, not just out.

**Contents:** purchase orders, receiving by **colour quantity** ("100 t-shirts: 20 red, 30 blue"),
hang-tag printing from Phase 3's `Tspl`, blind cycle and full counts, adjustments with reasons.

| # | Decision |
|---|---|
| 6a | **Cost model.** Settled as moving weighted average — ADR **KD-008**, with a cost snapshot on every movement so the ledger describes its own basis. |
| 6b | **Count workflow.** Blind (counter cannot see expected) — the only kind that finds real discrepancies. |
| 6c | **`androidTarget()` is added here** (KD-004), for handheld counting on the shop floor. First time `commonMain` gets a second consumer — expect to find desktop assumptions. |
| 6d | Spreadsheet import for initial catalogue load, reusing validated receiving paths. |

**Risk:** adding Android late will surface `java.*` leakage in `commonMain`. KD-004 forbids it from
day one to keep this cheap; a CI grep gate from Phase 0 would make it cheaper still.

> **The gate paid for itself.** Six phases of `commonMain` compiled for Android with no source
> changes at all — the whole cost was build configuration. The only thing the second target
> actually moved was the *test* fixtures, because `Room.inMemoryDatabaseBuilder` needs a `Context`.

---

## Phase 7 — Wholesale ✅

**Q1 answered on 21 Sep 2026: wholesale + retail.** Built — full plan in
`phase-7-wholesale-plan.md`. Tenancy does not arise, so Phase 9's sync stays single-tenant.

Taken *after* Phase 8, because it was gated when Phase 8 was not.

**Contents:** customer records, price lists/tiers (tables exist from Phase 1), credit limits and
accounts receivable, invoices with terms, partial payments and statements, **assortment packs**
(a carton as one line at one price, exploding into per-variant movements).

**Hard decisions:** AR as its own append-only ledger (same problem shape as stock); an issued invoice
is never edited — credit note instead; Bluetooth transport for handheld use lands here (KD-005).

---

## Phase 8 — Returns, exchanges & analytics ✅

**Two halves, two plans, both built.** `phase-8-returns-plan.md` and
`phase-8-analytics-plan.md`. Returns came first because the dashboard's return-rate KPI has
nothing to count until returns exist.

**Taken out of order, deliberately.** Phase 7 is gated on Q1 and Phase 8 is gated on nothing, so
the sequencing note below was followed rather than waiting. Q1 is still needed before Phase 7 or
Phase 9's tenancy work.

**Goal:** the daily operations that aren't selling, plus the numbers the owner opens the app for.

**Contents:** returns via receipt-QR scan (Phase 3 put the QR there for exactly this), exchanges as
return + sale settling the difference, and the admin dashboard — colour performance, sell-through by
the admin's own category tree, busy hours, top movers.

**Hard decisions, all three now settled:** the return window is a setting and exceeding it is a
permission (8e); a no-receipt return is permitted with an admin, at the lowest price the variant
ever sold for (8f); and a damaged return writes *both* movements — `RETURN` then `DAMAGE` — because
the shop took possession and then wrote it off (8b).

**Consumes from Phase 4:** `VIEW_SHOP_ANALYTICS` gates the dashboard, and `VIEW_COST_AND_MARGIN`
gates cost and margin figures *separately* — a seller may see units and revenue without seeing what
the shop paid.

---

## Phase 9 — Backend, sync, web back office

**Splits in two, the way Phase 8 did.** The first half — the server and the sync engine — is built:
`phase-9-sync-plan.md`. The web back office gets its own plan when it is next, because it had
nothing to render until the server existed.

**Goal:** the "then online" half. Multi-device, multi-branch, owner-at-home reporting.

| # | Decision |
|---|---|
| 9a | ✅ **Sync is easy by construction** — and it was, though the schema turned out to hold *three* kinds of row rather than two. Events and documents cannot conflict; only the catalogue can, and it converges on the log's own order. KD-010. |
| 9b | ✅ **KD-007 — desktop secure storage**, and not by any of the four options listed here. The token sits beside a plaintext database, so the filesystem is the trust boundary on desktop and revocation is what does the real work. Android gets the Keystore. |
| 9c | ✅ **ADR-041 re-decided as KD-009** — bounded jittered retry on the sync path, nothing user-facing. Safe only because every push is idempotent. |
| 9d | ✅ **Server-side identity did not arise.** The server authenticates *devices*; the shop still authenticates people. `app_user` syncs like any record, and Phase 6's portable password hash — built for an unrelated reason — means a device that has synced can sign in someone it has never seen, offline. |
| 9e | Tenancy does not land: Q1 settled on wholesale + retail. |
| 9f | **New, and not foreseen here.** Two tills allocating `MAX(receiptNumber) + 1` both reach 413 on a busy morning. Each device now sells from its own block of a million — no schema change, and the unique index Phase 5 added to make this loud simply stops being reachable. |

---

## Phase 10 — ETA fiscal integration — ⚠️ **all but removed**

**Q2 answered on 21 Sep 2026: non-fiscal slips.** The B2C e-Receipt half disappears, and with it
**R1**, the offline/QR conflict that was the largest single unknown in the project: a receipt
printed offline cannot carry an ETA-validated QR, and now it does not need to. The e-seal material
that would have been Tier 1 data is gone too, which narrows KD-006's remaining scope in Phase 9 to
sync tokens alone.

What remains below is kept for the day a shop is obliged to issue B2B e-Invoices. Nobody should
start it without reading ETA's own SDK documentation — everything in §5 of the architecture plan
came from secondary sources.

**Contents:** ETA e-Invoice (B2B, CAdES-BES, PKCS#11 USB token or HSM) and/or e-Receipt (B2C, POS
registration, e-seal, QR on the printed receipt).

| # | Decision |
|---|---|
| 10a | **The offline/QR conflict** (architecture plan R1). The 24-hour submission window makes offline selling compatible with compliance, but a receipt printed offline cannot carry an ETA-validated QR. Print without QR and deliver the compliant copy on acceptance is the likely answer — **confirm against ETA's own SDK docs, not secondary sources.** |
| 10b | Signing device topology: a USB token needs a local agent and suits low volume; an HSM or ETA-approved cloud signing is required for automation. A **procurement decision with lead time** — raise it early even though the phase is last. |

**Risk — the largest single unknown in the project.** Everything in §5 of the architecture plan came
from secondary sources. Someone must read the official ETA SDK documentation and confirm before this
phase starts. Isolating `:features:fiscal` behind an interface (KD-005) keeps the blast radius to one
module.

---

## Sequencing notes

- **Phases 3 and 4 can run in parallel.** Hardware and auth share nothing but `:core`, and both only
  need Phase 1. Two people, two tracks, no merge pain.
- **Phase 7 and 10 can swap** depending on when Q1 and Q2 are answered.
- **Phase 9 can start earlier** if a second till is needed before wholesale — the ledger design means
  sync is not blocked by anything in 7 or 8.
- **10b has procurement lead time.** Ask about the signing device as soon as Q2 is settled, even
  though the work is last.
