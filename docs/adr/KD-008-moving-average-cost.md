# KD-008 — Cost is a moving weighted average, snapshotted onto every movement

## Status
Accepted — 16 Sep 2026

## Context
Phase 6 is the first time stock arrives at a price, so margin stops being hypothetical. The choice is FIFO or moving weighted average, and it cannot be changed retroactively without recomputing every historical figure the shop has looked at.

FIFO needs a second ledger: each receipt is a layer, each sale consumes layers in order, and the layers must be replayed in sequence. That is in direct tension with the Phase 1 design — an append-only movement ledger whose commutativity is exactly what makes Phase 9's sync need no conflict resolution. Two tills selling offline from the same stock would consume layers in an order neither of them knows.

## Decision
**Moving weighted average**, recomputed on receipt:

```
newCost = (onHand × oldCost + receivedQty × receiptCost) / (onHand + receivedQty)   [HALF_EVEN]
```

With on-hand at or below zero there is nothing to average against, so the receipt's own cost becomes the cost outright.

Every `stock_movement` carries a nullable `unitCostPiastres` **snapshot**, and every `sale_line` already carries one from Phase 5. The ledger therefore describes its own cost basis rather than depending on `variant.costPiastres`, which is only ever "the cost right now".

## Consequences
Cost is order-dependent within one till and independent of merge order across tills, because each movement carries the number that was true when it was written. Margin reporting in Phase 8 reads the snapshots and never recomputes.

`variant.costPiastres` becomes derived state, maintained by receiving. A correction is a compensating movement plus a re-run, never an edit of history.

Rejected: FIFO (needs layer tracking that fights the ledger design and the sync model); last-cost (simplest, but one unusual invoice distorts every margin after it); standard cost (needs a costing function nobody in a clothing shop will maintain).

Changing this later means recomputing `variant.costPiastres` from the ledger — possible, because the snapshots are there — but every report printed before the change will disagree with every report after it.
