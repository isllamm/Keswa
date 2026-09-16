# `:features:sell`

Scan → cart → tender → print → stock moves. The first part of the app that handles money.

## The two invariants everything else rests on

**A sale is all-or-nothing.** Header, lines, tenders and stock movements commit in one transaction
(`KeswaDatabase.inTransaction`). Room's own `@Transaction` is scoped to a single DAO and a sale is
not, so the boundary is drawn at the repository. `SaleLedgerTest` forces a failure mid-commit and
asserts nothing survives.

**The lines always sum to the header.** Every figure on the screen, the receipt and the row comes
from `CalculateBasketTotalUseCase`; nothing else adds up money. Order-level discounts and VAT are
both spread with `Money.allocate`'s largest-remainder split, so a receipt whose lines add to a
piastre less than the total printed beneath them cannot happen.

```
line gross     = unitPrice × quantity
line net       = gross − lineDiscount
subtotal       = Σ line gross
order discount = allocated across lines by line-net weight
line total     = line net − its share
total          = Σ line total        == subtotal − (Σ lineDiscount + orderDiscount)
tax            = total.taxIncludedAt(rate), then allocated by line-total weight
```

VAT is *extracted*, not added — Egyptian retail prices are tax-inclusive — and it defaults to zero,
because most small shops are not registered and printing a VAT line when you are not is a legal
problem rather than a cosmetic one.

## Decisions worth knowing

**Printing happens after the commit, never inside it.** A sale that committed but did not print is
reprintable. A sale that printed but did not commit is a customer holding a receipt for a
transaction the shop has no record of.

**A void is a negative sale, not an adjustment.** The reversal carries the same `SALE` reason with
the opposite sign, so `SUM(quantity) WHERE reason = SALE` is still units sold with no special case
for Phase 8's analytics to remember. The original row is never deleted or re-priced — it is marked
`VOIDED` with who did it and why.

**Split tender from day one.** `payment` is its own table, one row per tender. `amount` is what a
tender settles and `tendered` is what the customer handed over; they differ only for cash, and the
difference is change. Retrofitting this would have touched every payment path in the app.

**Approvals are looked up, not trusted.** A seller can have an admin approve a discount, a price
override or a void *in place* — the realistic flow in a shop. The approving user is recorded on the
line, and `CompleteSaleUseCase` re-reads that user's permissions before committing, so a bug in the
screen cannot smuggle an unapproved discount into the ledger. Re-authentication feeds the same
lockout counter as sign-in, or the approval dialog would be an unlimited password oracle.

**A void always needs a fresh credential**, even from an admin who is already signed in. That is
Phase 4's rule, and it is the one action at a till that destroys revenue.

**Held sales are not sales.** A parked cart lives in its own tables, so no revenue query ever needs
a status filter that someone will forget. Resuming restores the price that was *agreed* — a
customer told 180 does not come back to 200 because a markdown ended while they were choosing —
while stock and descriptions are re-read, because those are facts about now.

**Selling below stock is allowed, with a warning.** The stock figure is more often wrong than the
customer's hands. Refusing teaches the shop to work around the till; allowing it silently means the
discrepancy is never noticed.

**An unknown barcode is refused, not invented.** Creating catalogue entries at the till with a queue
waiting produces the junk nobody goes back and cleans up — and it needs `MANAGE_CATALOGUE`, which
the person scanning does not have. Search by SKU or name is the fallback; Phase 6's receiving flow
is where stock should acquire a barcode.

## Shifts

A seller opens a shift with a counted float and closes it with a count. **The expected figure is
shown only after closing** — a cashier who can see what the drawer should hold counts until it
agrees, and the discrepancy that would have told the owner something disappears.

`expectedCash` is written onto the shift at close rather than recomputed on demand, so a Z-report
reads the same next month as it did on the night. Expected cash is the float plus the *settled*
cash: change is already netted off each tender, and subtracting it again is the classic way to make
every till look short.

A sale does not require an open shift — an owner ringing something up outside one should not be
blocked — but the Z-report says how many such sales there were, because the answer should usually
be none.

## What this phase added elsewhere

Three gaps only visible once you try to sell something:

- **Nothing wrote `price`.** The tables shipped in v1; the catalogue set `cost` and never a selling
  price. `:features:catalog` gained price entry per variant.
- **Nothing seeded a location.** `stock_movement.locationId` is `RESTRICT`, so the first sale on a
  fresh install would have failed on a foreign key. `SeedShopUseCase` now seeds it, and a default
  RETAIL price list.
- **The receipt had no shop on it.** `ShopSettings` gained name, Arabic name, address and VAT rate.

`LockoutPolicy`, the salt codec and `TransportFactory` moved down into `:core` — two features now
need each of them, and `features:A` must never import `features:B`.

## Not here yet

Returns and exchanges (Phase 8, via the receipt QR this phase already prints), customers and
invoices (Phase 7, gated on Q1), any cost method beyond snapshotting the cost onto the line
(Phase 6's 6a needs its own ADR), and anything involving a server (Phase 9).
