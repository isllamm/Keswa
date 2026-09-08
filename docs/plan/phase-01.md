# Phase 1 — Point of Sale

> *"The shop rings up every sale in Keswa, and the till reconciles at close."*

**Effort:** ~95h · **Calendar:** ~10 weeks @10h/wk

---

## Goal
Replace the notebook. A cashier sells all day using the keyboard, takes cash and card, handles returns
and exchanges, and closes the shift with a Z-report that matches the drawer.

This is the phase where Keswa becomes the system of record for money. Everything about it must be
boringly reliable.

## In scope

**Selling**
- POS screen: keyboard-first, search by SKU/name/manual barcode, add line, change qty, remove line
- Line and document discounts (amount or %), with a per-role limit and an override that writes audit
- Price resolution: retail + wholesale price lists, channel chosen per sale, price snapshotted on the line
- Held/parked sales (multiple concurrent DRAFT sales — customer goes to fetch another size)
- Sale completion in a single transaction: header + lines + payments + stock movements + audit + outbox
- Document numbering `ST01-INV-000123` from `document_counter`

**Money**
- Payments: cash (with tendered/change), card, bank transfer — **split payments as multiple rows**
- Void a completed sale (reversing movements + opposite payments, never a delete)
- Returns and exchanges as signed-quantity documents linked to the original sale, with the
  "cannot return more than was sold" rule enforced and tested

**Cash control**
- Shift open with float, pay-in/pay-out with reason, cash drop
- Shift close: expected vs counted, variance reason required, Z-report
- No sale may complete without an open shift

**Access**
- Multiple users, PIN unlock, roles OWNER/MANAGER/CASHIER, permissions as data
- Lockout after failed attempts; fast user switching at the till

**Output**
- A4/PDF receipt and invoice (PDFBox), Arabic, with shop header from settings
- Reports: `SALES_SUMMARY`, `SALES_BY_PRODUCT`, `SALES_BY_CATEGORY`, `SALES_BY_USER`,
  `PAYMENTS_BREAKDOWN`, `SHIFT_RECONCILIATION`, `RETURNS`, `AUDIT_TRAIL` — all Excel-exportable

## Explicitly out of scope
Barcode scanner hardware · thermal/ESC-POS printing · purchase orders · suppliers · customer credit
and A/R · loyalty · gift cards · tax calculation (rate stays 0) · multi-location transfers · any
network code · auto-update · dashboard/charts

## Deferred from your day-one list — and why
You marked **purchase orders**, **returns/exchanges**, and **customer credit** as day-one.

- **Returns/exchanges stay in Phase 1.** A shop cannot open a till without them, and they share the
  sale document, so they cost ~8h here versus ~20h retrofitted.
- **Purchase orders and customer credit move to Phase 2.** Both are ~55h combined. Adding them here
  makes the first usable release land at week 26 instead of week 16, with no till running in between.
  Phase 1 ships manual stock adjustments (already built in Phase 0) as the interim inbound path, and
  credit sales are simply refused until Phase 2 — which is the shop's status quo today.
  **If this is wrong — if the shop genuinely sells on credit daily and cannot operate without it —**
  say so and I will move `customer` + `customer_ledger` into Phase 1 (+~20h) and push
  `SALES_BY_USER`/`SALES_BY_CATEGORY` out instead.

## Deliverables
1. A cashier trained and selling on Keswa for a full trading day
2. Z-report matching the physical drawer count on three consecutive days
3. A4 receipt the owner is willing to hand a customer
4. Eight reports exporting to Excel
5. `docs/` and ADRs updated for any decision that changed

## Exit criteria
- [ ] A full trading day recorded with zero fallbacks to the notebook
- [ ] Shift close variance is 0 on a day with no known till errors, and explains itself when not
- [ ] Sale total = sum of payments, asserted by an invariant test **and** by a real day's data
- [ ] Return of 3 of 5 sold units succeeds; a 4th return of the same line is refused with an Arabic error
- [ ] Void reverses stock exactly — `rebuildStockLevels()` produces identical numbers before and after
- [ ] Power-off during sale completion leaves either a complete sale or no sale (test 10 times, scripted)
- [ ] Cashier can complete a 5-line cash sale in under 40 seconds without a mouse
- [ ] Every money- or stock-affecting action appears in `AUDIT_TRAIL` with the right user

## Migration impact
Additive only: `sale`, `sale_line`, `payment`, `shift`, `cash_movement`, `document_counter`,
`price_list*` tables were defined in `1.sqm`, so most of this phase is *code*, not schema. Expect
2–3 small migrations for things learned at the till (an extra note field, an index for a slow report).

## Risks
| Risk | Mitigation |
|---|---|
| **Cashier speed** — a slow POS gets abandoned for the notebook | Design the keyboard flow first, on paper, with the actual cashier. Measure the 5-line sale. This is the phase's real acceptance test |
| Discount/rounding disputes | Largest-remainder allocation, unit-tested with the owner's real examples in the test names |
| Returns rules argued after launch | Write the rules down in this doc *with the owner* before coding: time limit? receipt required? refund to original method only? **ASSUMPTION:** 14 days, receipt or document number required, cash refunds allowed |
| Scope creep from watching real usage | Keep a "Phase 2 wishes" list visible. Add nothing to Phase 1 that isn't blocking a sale |
| The one machine dies mid-phase | Backups already run from Phase 0 and have been rehearsed. Keep a second Windows machine capable of a restore |
| Trust collapse from one wrong total | Never ship a report you haven't reconciled by hand against a real day |

## Notes
- **Run in parallel with the notebook for the first week.** Not longer — parallel running erodes fast —
  but the first week catches everything the tests didn't.
- The single most valuable hour in this phase is watching the cashier use it without helping them.
