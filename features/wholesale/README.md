# `:features:wholesale`

The shop sells to other shops: on account, at trade prices, by the carton.

## Receivables are an append-only ledger, exactly like stock

This is the decision the whole phase turns on, and it was not a new one — Phase 1 already solved
this problem for stock, and it is the same problem.

An invoice is a debit, a payment a credit, a credit note a reversal. **The balance is
`SUM(amount)` and has no other definition.** There is no `balance` column, which is the point:
a stored balance is one careless `UPDATE` away from a number nobody can reconstruct.

Three things follow:

1. "Why does Nasr Textiles owe 14,200?" is a query that walks every entry.
2. Two devices merge by appending, with no conflict resolution — Phase 9 gets receivables sync for
   free, the same way it gets stock.
3. A mistake is a compensating entry, which is what an accountant expects and an auditor asks for.

## A wholesale invoice **is** a sale

Not a parallel document. Goods leaving on account are goods leaving: the same lines, the same
totals arithmetic, the same stock movements, the same void semantics. `sale` gained a nullable
`customerId`, `TenderMethod` gained `CREDIT`, and that was the whole of it.

Building a second document would have meant two places to get `Money.allocate` right, and
eventually two places where it differs.

**The receivable is written inside the sale's own transaction**, in `SaleRepositoryImpl`, for the
same reason the stock movements are: goods out and money owed are one event, and a shop that
recorded one without the other would be giving stock away.

## Payments are against the account, not the invoice

This is *why* receivables cannot just be "unpaid sales".

A customer pays 5,000 against four outstanding invoices, or on account against nothing in
particular. Forcing the shop to name an invoice at the moment the money arrives is a decision they
usually cannot make, and it is wrong as often as not. So allocation is oldest-first **in the ageing
report only**, and is never stored.

## The credit limit is a hard stop

`balance + this sale ≤ limit`, or the sale is refused. Not a warning — a limit that warns is a
limit that gets clicked through on a busy morning, and the busy morning is the entire reason it
exists.

Going over needs an admin's approval in place, through the same `ICredentialVerifier` the till and
returns use, and the approval is recorded on the ledger entry. An over-limit sale nobody can trace
is not a control.

**A limit of zero means cash only**, and that is the default. Trust should be granted deliberately,
not inherited from a blank field on a form.

## Trade prices are the price list that already existed

Phase 1 shipped `price_list` with a `WHOLESALE` type and nothing ever made one. A customer points
at a list; new customers land on the trade list, created on first use.

No per-customer discount percentage on top. A second pricing mechanism is how two people end up
quoting different prices for the same carton.

## An assortment pack explodes into lines

"A carton: 20 navy, 30 white, 10 beige, one price."

The pack is a catalogue definition. Adding one to a basket expands it into ordinary lines, with the
carton's price spread by `Money.allocate` weighted on each variant's own list price — so an
expensive garment carries more of the carton than a cheap one, and **the lines sum to exactly what
was quoted**. A carton whose lines add up to a pound less than the invoice is the wholesale version
of the classic POS bug, and a customer adding up their own invoice is how you find out.

The alternative — a sale line pointing at a pack rather than a variant — would mean a nullable
`variantId`, and then every stock query, analytics rollup and return in the app grows a special
case for a row that is not really an item. Stock moves per variant because stock *is* per variant.

Each expanded line remembers its `packId`, so a receipt can group the carton back together.

## Not here

No supplier ledger — payables are the mirror image and a genuinely separate feature. No statements
by email or PDF, because there is no server and no mail; a printed statement is Phase 9's problem
to improve on. No interest or late fees, which are a business decision with legal shape rather
than a feature.

**No Bluetooth transport**, which the Phase 7 outline had parked here under KD-005. Phase 6 added
the Android target and network printers already work on it, so Bluetooth is a solution waiting for
a shop that has asked. It can land the day one does.
