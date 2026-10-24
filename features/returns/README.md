# `:features:returns`

Goods coming back, and the money going out with them.

## A return is its own document

Not a negative `sale`, though that would have made net revenue fall out of a single `SUM`.

- A return carries facts a sale has no use for: the condition each garment came back in, the line
  it reverses, whether it was inside the window, and who approved it if it was not.
- The original sale keeps its own figures. Somebody who bought three and returned one bought
  three, and a receipt reprinted next year has to still say so.

Same reasoning that kept held carts out of `sale` in Phase 5. Net revenue is `sales − returns`,
written once in a use case, against a shape that stays honest.

## The two rules that cost money when they are wrong

**You cannot return more than was sold.** Sold minus already-returned, summed across *every*
completed return — because the two visits are days apart, and the failure mode is the same jacket
coming back three times against one receipt. Enforced in the use case and tested with the UI
bypassed.

**A no-receipt return refunds the lowest price that variant ever sold for.** Buying at a markdown
and returning at full price is the most common refund fraud in retail, and it costs exactly the
markdown every time. The shop refunds the least it was ever paid, not the most.

## Decisions worth knowing

**A damaged return writes two movements.** A `RETURN` bringing the garment in, then a `DAMAGE`
taking it out. Both happened: the shop took possession and then wrote it off. Recording only the
write-off leaves a refund with no corresponding receipt of goods, and nobody reading the ledger
later can explain where the money went. Recording only the return puts a ruined garment back on
the rail.

**The window is a setting; exceeding it is a permission.** Inside it, with a receipt, a seller can
take goods back. Outside it, or with no receipt, an admin approves in place — the same
re-authentication the till uses for discounts, feeding the same lockout counter. The permission
*is* the policy, and the approval goes on the record. What a policy must never be is a rule staff
route around by not using the till.

**The approving user is looked up, not trusted.** An id in a field is a claim the screen makes.

**An exchange is a return plus a sale.** Not a third document type — those are two things that
happened, and collapsing them loses both. `exchangeSaleId` links the pair, which is what lets
analytics tell a swap from a walk-out refund. Those are very different events for a shop.

**The refund settles one way.** `refundMethod` and `refundAmount` on the header rather than a
payment table: a refund goes back the way it came, and the cost of the general case is making
`payment` polymorphic over two document types — a migration on the one table that already holds
real money. Revisit if split refunds ever actually happen.

**A returned garment comes back at the cost it left at**, copied from the sale line's snapshot, so
a change of mind does not move the moving average (KD-008).

**Cash refunds come out of the drawer.** The Z-report subtracts them; card refunds never touch it.
A till that reads over after a refund is a till nobody trusts.

## Where the receipt QR finally earns its keep

Phase 3 put a QR on every receipt and Phase 5 filled it with the sale's **id**. This is what it was
for: scan it, and the sale and its returnable lines come straight up. A typed receipt number works
too, because a QR that will not scan is a Tuesday.

## Not here yet

Store credit and gift cards (a credit balance is a liability needing its own ledger — the same
problem shape as Phase 7's receivables, and it belongs there), restocking fees (no shop asked),
and a reason taxonomy (a dropdown of eight reasons produces eight thousand rows of "other").

The analytics half of Phase 8 — the dashboard that counts all this — is
`ai-plans/phase-8-analytics-plan.md`.
