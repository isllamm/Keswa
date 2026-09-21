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
| 7 | Wholesale ⚠️ Q1 | outlined below |
| 8 | Returns, exchanges & analytics | `phase-8-analytics-plan.md` + below |
| 9 | Backend, sync, web back office | outlined below |
| 10 | ETA fiscal ⚠️ Q2 | outlined below |

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

## Phase 7 — Wholesale ⚠️ gated on Q1

**Only built if Q1 = wholesale + retail.** If Q1 = multi-tenant SaaS, this is replaced by tenancy
work folded into Phase 9.

**Contents:** customer records, price lists/tiers (tables exist from Phase 1), credit limits and
accounts receivable, invoices with terms, partial payments and statements, **assortment packs**
(a carton as one line at one price, exploding into per-variant movements).

**Hard decisions:** AR as its own append-only ledger (same problem shape as stock); an issued invoice
is never edited — credit note instead; Bluetooth transport for handheld use lands here (KD-005).

---

## Phase 8 — Returns, exchanges & analytics

**Analytics has its own full plan:** `phase-8-analytics-plan.md`.

**Goal:** the daily operations that aren't selling, plus the numbers the owner opens the app for.

**Contents:** returns via receipt-QR scan (Phase 3 put the QR there for exactly this), exchanges as
return + sale settling the difference, and the admin dashboard — colour performance, sell-through by
the admin's own category tree, busy hours, top movers.

**Hard decisions:** return window and policy enforcement; whether a no-receipt return is permitted
and at what price; restocking condition (sellable vs. damaged → different movement reasons, already
in the Phase 1 enum).

**Consumes from Phase 4:** `VIEW_SHOP_ANALYTICS` gates the dashboard, and `VIEW_COST_AND_MARGIN`
gates cost and margin figures *separately* — a seller may see units and revenue without seeing what
the shop paid.

---

## Phase 9 — Backend, sync, web back office

**Goal:** the "then online" half. Multi-device, multi-branch, owner-at-home reporting.

| # | Decision |
|---|---|
| 9a | **Sync is easy by construction** — the Phase 1 append-only ledger is commutative, so movements merge with no conflict resolution. Catalogue pulls with a version cursor; documents upsert idempotently on client-generated UUIDs (`cashi_pax` defect F2). |
| 9b | **KD-007 — desktop secure storage.** The ADR-038 gap comes due here: sync tokens are Tier 1 and there is no Keystore on desktop. macOS Keychain via JNA / Windows DPAPI / libsecret / documented passphrase key. **Decide before the first token is persisted.** Phase 4 dodged this by keeping sessions in memory; Phase 9 cannot. |
| 9c | **Re-decide ADR-041 (no HTTP retry).** Cashi banned retries purely for native parity (ADR-036), N/A here. A till on in-store wifi wants bounded retry with backoff on the sync path — but *not* on anything user-facing. |
| 9d | **Server-side identity.** Phase 4's users are local to one install. Multi-device means deciding whether accounts become server-owned, and how an offline till authenticates a user it has never seen. |
| 9e | Tenancy lands here if Q1 = SaaS. |

---

## Phase 10 — ETA fiscal integration ⚠️ gated on Q2

**Q2 decides how much of this exists.** If payment receipts come from a separate terminal, or the
slips are non-fiscal, the B2C half disappears.

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
