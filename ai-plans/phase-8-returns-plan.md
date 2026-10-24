# Phase 8 Plan — Returns & Exchanges

> **Status: ✅ BUILT**
> The operational half of Phase 8. The other half is `phase-8-analytics-plan.md`, and this one
> comes first: the dashboard's return-rate KPI and `returnedUnits` rollup have nothing to count
> until returns exist.
> Depends on: Phase 5 (sales, the receipt QR), Phase 6 (cost snapshots), Phase 4 (permissions)
> Estimated: 5–7 days

## Goal

A customer brings something back, and the shop can take it back correctly: the right refund, the
stock in the right state, and a record that explains itself a year later.

---

## Why Phase 7 was skipped

Phase 7 is gated on **Q1** — wholesale + retail, or multi-tenant SaaS — and the outline says so:
*"Phase 7 and 10 can swap depending on when Q1 and Q2 are answered."* Phase 8 depends on neither
question. Building it now is following the sequence the project already wrote down, not
improvising around a blocker.

Q1 is still needed before Phase 7 or Phase 9's tenancy work. It is not needed here.

---

## Decisions

### 8a — A return is its own document, not a negative sale

`sale_return` and `sale_return_line`, referencing the original sale.

The alternative — a second row in `sale` with negative quantities and a `type` column — makes net
revenue fall out of `SUM(total)` for free, which is genuinely tempting. It is rejected for the same
reason held sales were kept out of `sale` in Phase 5:

- A return carries fields a sale has no use for: condition per line, the original line it reverses,
  whether it was inside the policy window, who authorised it.
- The original sale must keep its own figures intact. A customer who bought three and returned one
  bought three; a receipt reprinted next year has to still say so.
- `daily_sales_summary` in the analytics plan already has `returnedUnits` as its own column, so the
  two were always going to be counted separately.

Net revenue becomes `sales − returns` rather than a single sum. One join, written once, in a use
case — against a shape that stays honest.

### 8b — A damaged return writes **two** movements

A garment comes back damaged: the shop took possession of it and then wrote it off. Both halves are
true, and the ledger records both — a `RETURN` bringing it in, then a `DAMAGE` taking it out.

Writing only the write-off would leave a refund with no corresponding receipt of goods, and nobody
reading the ledger later could explain where the money went. Writing only the return would put a
ruined garment back on the shelf.

A sellable return writes one `RETURN` movement and goes back on the rail.

### 8c — An exchange is a return plus a sale, settling the difference

Not a third document type. The customer returns a medium and buys a large; those are two things
that happened, and modelling them as one loses both.

`sale_return.exchangeSaleId` links them, so a receipt can show the pair and the analytics can tell
an exchange from a walk-out refund — which matters, because one of those kept the money in the shop.

The difference settles in the ordinary way: the new sale is tendered as normal, with the return's
refund as one of its tenders where the shop owes money, or a straight refund where it does not.

### 8d — The refund is settled one way, not split

`refundMethod` and `refundAmount` sit on the header rather than in a payment table.

A refund in practice goes back the way it came — the card it was paid on, or cash. Split refunds
are vanishingly rare, and the cost of the general case here is making `payment` polymorphic over
two document types, which means a migration on the one table in the schema that already holds real
money.

*Reconsider if:* real use shows split refunds happening. The fix then is a `refund_payment` table,
not a change to `payment`.

### 8e — The policy window is a setting, and exceeding it is a permission

`returnWindowDays`, default 14.

- **Inside the window, with a receipt** — `REFUND_WITHIN_POLICY`, which a seller has.
- **Outside the window, or no receipt** — `REFUND_ANY`, which only an admin has, granted in place
  by the same re-authentication the till already uses for discounts.

The permission is the policy. A shop that wants a 30-day window changes a number; a shop that wants
to say yes to a good customer on day 40 gets the owner to approve it, and the approval is on the
record. What a policy must never be is a rule the staff route around by not using the till.

### 8f — A no-receipt return refunds the **lowest price that variant ever sold for**

Not the current price, and not the highest.

The scam this closes is buying at a markdown and returning at full price. It is not hypothetical —
it is the single most common retail refund fraud, and it costs exactly the markdown each time.

`MIN(lineTotal / quantity)` across that variant's sale lines. If it has never sold, there is no
price to refund at and the return needs a manual amount, which needs `REFUND_ANY` anyway.

### 8g — You cannot return more than was sold

Per line: `quantity sold − quantity already returned`. Enforced in the use case and asserted with
the UI bypassed, because the failure mode is a customer returning the same jacket three times
against one receipt.

A return is **append-only like everything else**: no edit, no delete. A return made in error is
corrected by a compensating sale, or by voiding it — and voiding a return reverses its movements
exactly as voiding a sale does.

---

## Deliverables

### Schema v6

```
sale_return        id, returnNumber (unique), originalSaleId (null = no receipt), locationId,
                   userId, shiftId, status, reason, refundMethod, refundAmountPiastres,
                   subtotalPiastres, taxPiastres, occurredAt, exchangeSaleId,
                   authorisedByUserId, voidedAt, voidedByUserId, voidReason
sale_return_line   id, returnId, lineNumber, saleLineId (null = no receipt), variantId,
                   description, quantity, unitRefundPiastres, lineRefundPiastres,
                   condition (SELLABLE | DAMAGED), unitCostPiastres
```

`ShopSettings` gains `returnWindowDays` and `allowNoReceiptReturns`.

Return numbers are their own sequence, allocated inside the transaction with a unique index —
the same arrangement as receipt numbers, and for the same reason.

"What is still returnable" is a repository read rather than a use case of its own: it is one query
against `sale_line` and completed returns, and wrapping it would add a name without adding a rule.

### `:features:returns`

```
features/returns/
├── domain/usecase/
│   ├── ReturnUseCases.kt            find the sale, complete, void, link an exchange
│   ├── PrintRefundNoteUseCase.kt    after the commit, never inside it
│   └── LowestSoldPriceUseCase.kt    8f — in the same file as the rest
├── presentation/screens/returns/
└── di/ReturnsModule.kt
```

The till gains a **Returns** entry; the refund note prints on the same receipt printer.

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No store credit or gift cards.** A credit balance is a liability and needs its own ledger, which
  is the same problem shape as Phase 7's accounts receivable. It belongs there, once it is known
  whether Phase 7 is happening.
- **No restocking fee.** Easy to add, and no Egyptian clothing shop asked for one.
- **No return reason taxonomy.** A free-text reason, because a dropdown of eight reasons produces
  eight thousand rows of "other".
- **No effect on moving-average cost.** A returned garment comes back at the cost it left at — the
  line's snapshot — so the average does not move. Revisit only if returns ever become large enough
  to distort it.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Atomicity | Return header, lines, movements and refund commit together or not at all |
| 2 | Sellable return restocks | On-hand rises by the returned quantity, one `RETURN` movement |
| 3 | Damaged return does not | On-hand unchanged, **two** movements — `RETURN` then `DAMAGE` |
| 4 | Over-return refused | Return 2 of 2, then try 1 more → refused, UI bypassed |
| 5 | Window enforced | Day 15 with a seller session → `ForbiddenAccess`; with an admin approval → allowed |
| 6 | No-receipt price | A variant sold at 180 and later at 120 refunds at 120 |
| 7 | Exchange links both | Return and replacement sale reference each other; net cash is the difference |
| 8 | Void a return | Movements reverse, the return row stays and is marked voided |
| 9 | Cost snapshot | The return line carries the cost the sale line carried, not today's |
| 10 | Migration v5 → v6 | Seed v5 with sales and receipts, migrate, assert everything intact |
| 11 | Return numbers | Consecutive from one, unique index holds |

Check 4 and check 6 are the two that cost real money when they are wrong.

## Definition of Done

- [x] A receipt is found by QR or number, and its returnable lines are correct
- [x] Sellable and damaged returns write the right movements
- [x] Refunds settle in cash or card and are recorded
- [x] Over-return, the policy window and no-receipt pricing are all enforced in the domain
- [x] Exchanges link a return to its replacement sale
- [x] A return can be voided; nothing is ever edited or deleted
- [x] v5 → v6 migration test green
- [x] `./scripts/check-gates.sh` and `./gradlew allTests` pass

## What changed while building it

**Re-authentication moved into `:core` as `ICredentialVerifier`.** Returns is the second feature to
need an admin's approval in place, after the till's discounts and voids. Phase 5 moved
`LockoutPolicy` down for the same reason; this is the rest of that move. Each feature keeps a thin
use case giving the operation its own vocabulary, and the lockout counter stays shared with
sign-in — an approval dialog that never locks is an unlimited password oracle.

**The Z-report now subtracts cash refunds.** Not in the plan, and a real bug the moment returns
existed: `expectedCash` was float + cash taken, so any shop that refunded anything in cash read
over at close. Card refunds still never touch the drawer.

**Two test fixtures were sharing an id sequence by accident.** Constructing two repositories with
separate `SequentialIds` handed out the same ids and collided on `stock_movement.id` — the unique
index doing exactly its job. Production binds one `IdGenerator` singleton, and the fixtures now
match.
