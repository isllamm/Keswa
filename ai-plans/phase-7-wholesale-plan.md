# Phase 7 Plan — Wholesale, Credit & Receivables

> **Status: ✅ BUILT — Q1 answered 21 Sep 2026: wholesale + retail**
> Depends on: Phase 5 (sales, the totals math), Phase 6 (cost), Phase 1 (the price-list tables)
> Estimated: 7–9 days

## Goal

The shop sells to other shops: on account, at trade prices, by the carton — and knows at any moment
who owes what.

---

## The decision the whole phase turns on

**Receivables are an append-only ledger, exactly like stock.**

It is the same problem shape, and Phase 1 already solved it. An invoice is a debit, a payment is a
credit, a credit note is a debit reversal; the balance is `SUM(amount)`. From that one choice:

1. **The audit trail is trustworthy** — "why does Nasr Textiles owe 14,200?" is a query that walks
   every entry, not a number somebody once typed.
2. **Two devices merge without conflict resolution**, because appends commute. Phase 9 gets
   receivables sync for free, the same way it gets stock.
3. **A balance can never be edited.** A mistake is a compensating entry, which is what an
   accountant would expect and what an auditor would ask for.

The alternative — a `balance` column on the customer, updated in place — is one careless `UPDATE`
away from a number nobody can reconstruct.

---

## Decisions

### 7a — A wholesale invoice **is** a sale, with a customer and a `CREDIT` tender

Not a parallel document type.

Goods leaving the shop on account are goods leaving the shop: the same lines, the same totals
arithmetic, the same stock movements, the same void semantics. Building a second document that does
all of that again would mean two places to get `Money.allocate` right, and eventually two places
where it differs.

So `sale` gains a nullable `customerId`, and `TenderMethod` gains `CREDIT`. A credit tender settles
the sale at the till *and* opens a receivable in the same transaction.

What it does **not** reuse is the payment side. That is 7b.

### 7b — Payments are against the **account**, not the invoice

This is why receivables cannot just be "unpaid sales".

A customer pays 5,000 against four outstanding invoices, or on account against nothing in
particular. Modelling payment as a `payment` row on one sale forces the shop to decide which
invoice the money belongs to at the moment it arrives — which is a decision they usually cannot
make, and which is wrong as often as not.

So payments are ledger entries against the customer. Allocation to specific invoices is a
*reporting* view (oldest-first by default), not a stored fact.

### 7c — An issued invoice is never edited. A credit note instead

Same rule as the sale it is, and the same rule as every other document in this app.

A credit note is its own ledger entry referencing the invoice, and where goods come back it is a
Phase 8 return that *also* writes the credit. One event, two ledgers — stock and money — which is
exactly what the two ledgers are for.

### 7d — The credit limit is checked in the domain, and it is a hard stop

`balance + this invoice ≤ creditLimit`, or the sale is refused.

Not a warning. A credit limit that warns is a credit limit that gets clicked through on a busy
morning, and the whole point of it is the morning somebody is too busy to think. Going over needs
an admin's approval in place — the same `ICredentialVerifier` the till and returns already use, and
the approval goes on the record.

A limit of zero means **cash only**, which is the right default for a customer nobody has decided
to trust yet.

### 7e — Trade prices are the price list that already exists

Phase 1 shipped `price_list` with a `WHOLESALE` type and nothing ever created one. A customer points
at a list; the till resolves prices from the customer's list rather than the default when one is
selected.

No new pricing mechanism, no per-customer discount percentages layered on top. A second discount
system is how two people end up quoting different prices for the same carton.

### 7f — An assortment pack explodes into lines, not into a new kind of line

"A carton: 20 navy, 30 white, 10 beige, one price."

The pack is a catalogue definition. Adding one to a basket **expands it into ordinary basket
lines**, with the pack's price spread across them by `Money.allocate` weighted on each variant's own
list price. Each line carries the pack it came from, so a receipt can group them.

What this avoids is a nullable `variantId` on `sale_line` — which would mean every stock query,
every analytics rollup and every return in the app growing a special case for a row that is not
really an item. Stock moves per variant because stock *is* per variant.

### 7g — Statements and ageing are reports over the ledger, not tables

`0–30 / 31–60 / 61–90 / 90+` days, computed from each debit's due date against today. Nothing
stored, nothing to fall out of step.

Due dates come from the customer's terms (`net 30` by default) and live on the ledger entry, because
the entry is the thing that is due.

---

## Deliverables

### Schema v7

```
customer               id, name, nameAr, phone, taxId, priceListId, creditLimitPiastres,
                       paymentTermsDays, isActive, createdAt, updatedAt
customer_ledger_entry  id, customerId, entryType, amountPiastres (signed), refType, refId,
                       occurredAt, dueAt, userId, note          ← append-only
assortment_pack        id, name, nameAr, pricePiastres, isActive
assortment_pack_line   id, packId, variantId, quantity
```

Plus two nullable columns: `sale.customerId` and `sale_line.packId`. Additive, as always.

`TenderMethod` gains `CREDIT`. `MovementReason` is untouched — goods leaving on account leave for
the same reason they leave for cash.

### `:features:wholesale`

```
features/wholesale/
├── domain/usecase/
│   ├── CustomerUseCases.kt          create, update, search, statement, trade list
│   ├── CreditUseCases.kt            the limit, the receivable, payments, credit notes
│   └── AssortmentPackUseCases.kt    define a pack, expand one into lines
├── presentation/screens/customers/  the list, the account, ageing and history in one
└── di/WholesaleModule.kt
```

**One screen, not two.** The statement was planned as its own screen and is a panel on the
customers screen instead: an account's balance, its ageing and its history are what somebody came
to the customer to see, and a second screen to reach them is a second click for no information.

The credit limit itself lives in `:core` as `CreditPolicy` — pure, and shared by the till (which
*stops* on it) and the customer screen (which *shows* it), so the two cannot drift.

The till's `CompleteSaleUseCase` takes the customer and the approval; the receivable is written
inside `SaleRepositoryImpl`'s own transaction, alongside the stock movements and for the same
reason.

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No tenancy.** Q1 settled it: this is one business selling both ways. Each shop still runs its
  own local database, and Phase 9's sync stays single-tenant.
- **No supplier ledger.** Payables are the mirror image and a genuinely separate feature. Receiving
  in Phase 6 records what stock cost; what the shop owes its own suppliers is not this phase.
- **No statements by email or PDF.** There is no server and no mail. A printed statement on the
  receipt printer is Phase 9's problem to improve on.
- **No Bluetooth transport.** The outline parked it here (KD-005), but Phase 6 added the Android
  target and *network* printers already work on it. Bluetooth is a solution looking for a shop that
  has asked, and it can land the day one does.
- **No interest or late fees.** Nobody asked, and charging them is a business decision with legal
  shape, not a feature.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Balance is a sum | Invoice, payment, credit note → balance walks to the hand-computed figure |
| 2 | Ledger is append-only | Source-read guard, as for stock, sales and returns |
| 3 | Credit limit is a hard stop | Over the limit with a seller session → refused, UI bypassed |
| 4 | Approval over the limit | An admin's approval lets it through, and is recorded on the entry |
| 5 | A zero limit means cash only | Any credit tender refused |
| 6 | Atomicity | Sale + lines + movements + the receivable commit together or not at all |
| 7 | Payment on account | 5,000 against four invoices reduces the balance once, and allocates oldest-first in the report only |
| 8 | Trade prices | A customer on the wholesale list is charged its prices, not the retail ones |
| 9 | Pack explodes | A 60-piece carton writes 3 movements and 3 lines, and the line total sums to the pack price |
| 10 | Pack allocation | The spread uses largest-remainder, so the lines equal the carton price exactly |
| 11 | Ageing | A 45-day-old invoice lands in 31–60, not 0–30 |
| 12 | Credit note | A Phase 8 return for a credit customer writes both the stock movement and the credit |
| 13 | Migration v6 → v7 | Seed v6 with retail trading, migrate, assert everything intact |

Checks 3 and 10 are the ones that cost money: a limit that can be clicked through, and a carton
whose lines do not add up to what was quoted.

## Definition of Done

- [x] Customers exist, with terms, a trade price list and a credit limit
- [x] A sale on account settles as `CREDIT` and opens a receivable, atomically
- [x] The balance is a sum over an append-only ledger, and nothing edits it
- [x] The credit limit is a hard stop, overridable only by an admin in place
- [x] Payments are taken against the account and allocate oldest-first in reporting
- [x] Statements and ageing read off the ledger
- [x] Assortment packs explode into lines that sum exactly to the carton price
- [x] v6 → v7 migration test green
- [x] `./scripts/check-gates.sh` and `./gradlew allTests` pass

## What changed while building it

**A new gate: shipped schemas are immutable.** This phase adds two columns to existing tables, and
building it exposed a trap. Add a column, compile once before bumping the version, and Room
rewrites the *current* exported schema in place — so `6.json` came to describe a v6 that never
shipped. The v6 → v7 migration test caught it (`duplicate column name: customerId`), but only
because this phase happened to have one. `check-gates.sh` now fails when an exported schema below
the current version has changed, which catches it at the moment somebody does it rather than at
the moment a shop upgrades.

**Statement arithmetic closes by construction.** The opening balance is derived from the closing
one rather than summed forward, so opening + movement = closing always holds and a reader never
has to take the first number on trust.

**New customers land on the trade list**, created on first use, rather than on the retail default.
A wholesale customer quietly buying at retail prices is a bug that surfaces as an argument.
