# Phase 2 — Purchasing & Customer Credit

> *"Stock comes in through the app, and the shop knows who owes what."*

**Effort:** ~55h · **Calendar:** ~6 weeks @10h/wk

---

## Goal
Close the two loops Phase 1 left open: inbound stock with real costs, and receivables. After this,
Keswa knows what everything cost, so margin reporting becomes truthful.

## In scope

**Purchasing**
- Suppliers: details, payment terms, default currency
- Purchase orders: draft → ordered, lines with cost, expected date, destination location
- Goods receipt: full or **partial**, against a PO or direct, emitting `PURCHASE_RECEIPT` movements
- Landed cost: shipping/customs/other allocated across receipt lines by value, folded into unit cost
- Weighted-average cost maintenance on receipt, with rounding remainder carried
- Returns to supplier
- Supplier ledger (A/P) — same append-only shape as customer ledger

**Customers & receivables**
- Customer records, phone lookup, per-customer price list, credit limit
- Credit sales: `CUSTOMER_CREDIT` payment method writing a `customer_ledger` CHARGE
- Payments against balance (partial, multiple), refunds, adjustments, write-offs (permissioned)
- `customer_balance` materialized + rebuildable, with a reconciliation test
- Credit limit enforcement with a permissioned override that writes audit

**Reports**
`PURCHASES_BY_SUPPLIER`, `SUPPLIER_BALANCES`, `CUSTOMER_BALANCES` (with 0-30/31-60/61-90/90+ aging),
`CUSTOMER_STATEMENT` (printable, Arabic), `PROFIT_MARGIN`, `STOCK_VALUATION`, `LOW_STOCK`

## Explicitly out of scope
Barcode hardware · thermal printing · multi-location transfers · supplier price lists / cost history
UI · purchase approvals workflow · payment scheduling or reminders · SMS/WhatsApp to customers ·
accounting export (GL) · any network code

## Deliverables
1. A real purchase order raised, received partially, then completed, with correct landed cost
2. Margin report the owner recognises as correct for at least 20 real products
3. A/R aging report replacing the notebook page where debts are tracked
4. Printable customer statement in Arabic

## Exit criteria
- [ ] Average cost after a receipt matches a hand calculation on paper, including landed cost
- [ ] Partial receipt leaves the PO in `PARTIALLY_RECEIVED` with correct outstanding quantities
- [ ] Customer balance after a credit sale + partial payment + return matches a hand calculation
- [ ] Aging buckets match a manual review of the shop's actual outstanding debts
- [ ] `rebuildCustomerBalances()` reproduces every balance exactly from the ledger
- [ ] Margin report ties out: `revenue − Σ(unit_cost_snapshot × qty)` for a chosen week

## Migration impact
Additive. `supplier`, `purchase_order*`, `goods_receipt*`, `supplier_ledger`, `customer*` were in
`1.sqm`. Likely one migration for `cost_rounding_minor` and landed-cost allocation columns if the
Phase 0 schema didn't anticipate the allocation method.

## Risks
| Risk | Mitigation |
|---|---|
| Landed cost allocation disputes | Agree the method (by value) with the owner **before** coding; make it a setting |
| Cost history is wrong because Phase 0 opening stock had guessed costs | Capture a real cost during the Phase 0 count, even if approximate. Note the cutover date on the margin report |
| A/R turns into a debt-collection feature request | Out of scope is written above. Reminders are Phase 5+ |
| Weighted-average vs the owner's mental model (last cost) | Show both on the product screen: current average and last purchase cost. Cheap, and prevents a trust problem |
