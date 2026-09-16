# Phase 5 Plan — Sell Flow

> **Status: ✅ BUILT**
> Depends on: Phase 1 (ledger, pricing tables), Phase 3 (receipt printer), Phase 4 (session, permissions)
> Expands the outline in `phases-5-to-10-outline.md`.
> Estimated: 8–10 days

## Goal

Scan → cart → tender → print → stock moves. The first genuinely usable till, and the first phase
whose output is money.

---

## What Phases 1–4 left open, and this phase must close

Three gaps only become visible when you try to sell something:

1. **Nothing writes `price`.** The tables have existed since v1; the catalogue sets `cost` and never
   a selling price. A till cannot ring up a variant that has no price, so Phase 5 adds the
   repository, the default RETAIL list, and price entry in the product editor — a Phase 2 gap this
   phase is the first to actually need.
2. **Nothing seeds a default location.** `stock_movement.locationId` is `RESTRICT`, so on a fresh
   install the first sale would fail on a foreign key. `Main.kt` reads the default location; nothing
   creates it.
3. **The receipt has no shop on it.** `Receipt` takes `shopName`, `shopNameAr` and `addressLine`;
   `ShopSettings` holds printer addresses and nothing else.

None is a design change. All three are the kind of thing a plan discovers by being executed.

---

## Decisions

### 5a — One transaction, and printing outside it

Sale header, lines, payments and stock movements commit in **one Room transaction**. Printing
happens after it returns, and a print failure never rolls a sale back.

This is Phase 3's rule, and it is the right way round: a sale that committed but did not print is
recoverable — reprint it. A sale that printed but did not commit is a customer holding a receipt for
a transaction the shop has no record of.

### 5b — The cart is domain state

`Basket` is a domain model and `CalculateBasketTotalUseCase` computes its totals. ADR-021: the
ViewModel holds state and delegates; it does not do arithmetic on money.

The order of operations, fixed here because every later feature depends on it:

```
line gross          = unitPrice × quantity
line net            = gross − lineDiscount
subtotal            = Σ line gross
order discount      = allocated across lines by line-net weight (Money.allocate)
line total          = line net − its allocated share
total               = Σ line total
discount            = Σ lineDiscount + orderDiscount
invariant           = subtotal − discount == total
tax                 = total.taxIncludedAt(vatBasisPoints)
line tax            = tax allocated across lines by line-total weight
```

`subtotal` is the gross of the lines rather than their net, so that the one identity a receipt has
to satisfy — *subtotal minus discount is the total* — holds on the printed page and in the `sale`
row alike. Both are stored, so a reader never has to reconstruct either.

Both allocations use largest-remainder, so **the lines always sum to the header** — the classic POS
bug is a receipt whose lines add up to one piastre less than the total printed under them.

Tax is *extracted*, not added: Egyptian retail prices are VAT-inclusive, and `Money.taxIncludedAt`
already exists for exactly this.

> **VAT defaults to zero.** Most small shops are not registered, and printing a VAT line when you
> are not registered is a legal problem rather than a cosmetic one. It is a setting, opt-in.

### 5c — Split tender from day one

`payment` is its own table with one row per tender, not two columns on the sale. Retrofitting split
payment touches every payment path in the app, and a customer paying 200 in cash and the rest by
card is an ordinary Tuesday.

- `amount` is what the tender settles; `tendered` is what the customer handed over.
- They differ only for cash, and the difference is change due.
- Change is `Σ tendered − total`, never negative — a sale cannot complete under-tendered.
- Cash sales pop the drawer via `EscPos.document(bitmap, openDrawer = true)`; card sales do not.

`CARD` here is a *record* that a card was used, not an integration. Q2 decides whether a terminal
is ever driven from this app.

### 5d — Shifts, deferred here from the auth plan

A seller opens a shift with a cash float and closes it with a count. Sales carry `shiftId`. The
Z-report at close is how a till discrepancy gets caught on the day rather than at the month end.

`expectedCash` is **stored on the shift at close**, not recomputed on demand: a report that changes
its own history when a later correction lands is not a report anyone can act on.

A sale does not *require* a shift — an owner ringing something up outside one should not be blocked
— but `shiftId` is null then, and the Z-report says how many such sales there were.

### 5e — Held sales are not sales

Park and resume gets its own pair of tables rather than a `HELD` status on `sale`.

A parked cart has not happened. Keeping it out of `sale` means every revenue query in Phases 8–9 is
a plain read with no status filter to forget — and the one someone forgets is the one that reports
parked carts as takings.

They are also the only rows in this phase that are genuinely local: they never sync. Two tables
rather than a serialised JSON blob, so there is no second schema format to migrate and resuming is
an ordinary read.

### 5f — Void is a negative sale, not an adjustment

Voiding writes compensating movements with the **same reason (`SALE`) and the opposite sign**,
`refType = "SALE_VOID"`, `refId` = the sale.

The alternative, `ADJUSTMENT`, loses information: with a signed reversal under the same reason,
`SUM(quantity) WHERE reason = SALE` is units sold — correct, with no special case, for the whole of
Phase 8's analytics. The audit trail still shows both movements, and the header carries who voided
it and why.

The sale row is **never deleted and never edited into a different shape**. It is marked `VOIDED`
with `voidedAt`, `voidedByUserId`, `voidReason`. The ledger's append-only discipline (ADR-KD-002 and
the Phase 1 entity KDoc) applies to money as much as to stock.

### 5g — Re-authentication, and two things that move into `:core`

A seller has `SELL` but not `DISCOUNT_LINE`, `OVERRIDE_PRICE` or `VOID_SALE`. So those actions
prompt for an admin credential *in place* — the admin does not sign the seller out to approve a
discount and hand the till back.

Whoever approved is recorded on the line (`authorisedByUserId`). "Who authorised this discount" is
the first question an owner asks about a receipt, and an approval nobody can trace is not a control.

Two pieces move down into `:core` to make this possible without `features:sell → features:auth`,
which the module rule forbids:

| Moves | From | Why it is core |
|---|---|---|
| `LockoutPolicy` | `features/auth/domain` | Two features now enforce it; one definition or it drifts |
| salt hex codec | `features/auth/domain/usecase` | It is the storage format of a `:core` entity's column |
| `TransportFactory` | `features/settings/domain/usecase` | It constructs a `:core` platform interface; the till needs it to print |

Re-authentication feeds the same lockout counter as sign-in. An override dialog that never locks is
an unlimited oracle for guessing the admin password, reachable by anyone who can open a cart.

### 5h — An unknown barcode is refused, not invented

Scanning something not in the catalogue shows "not found" and offers a search by SKU or name. It
does **not** offer to create a product.

Creating catalogue entries at the till with a queue waiting produces exactly the junk — "item",
"t-shirt 2", no category, no colour — that nobody ever goes back and cleans up, and it needs
`MANAGE_CATALOGUE`, which the person scanning does not have. Phase 6's receiving flow is where
stock arrives, and that is where it should acquire a barcode.

*Reconsider if:* real use shows the shop regularly selling items that genuinely have no barcode. The
fix then is a deliberate "miscellaneous line at a typed price", not accidental catalogue creation.

### 5i — Negative stock is allowed, with a warning

The stock figure is more often wrong than the customer's hands. Refusing the sale trains the shop to
work around the till; allowing it silently means the discrepancy is never noticed.

So: the sale completes, the movement is written, and the warning is returned from the use case and
shown — a signal, not a gate.

---

## Deliverables

### Schema v4

Six tables. Additive, so `MIGRATION_3_4` touches nothing existing.

```kotlin
@Entity(tableName = "sale", indices = [Index(value = ["receiptNumber"], unique = true), ...])
data class SaleEntity(
    @PrimaryKey val id: String,          // client UUID — the sync key (cashi_pax defect F2)
    val receiptNumber: Long,             // sequential, human-facing, unique
    val locationId: String,
    val priceListId: String,
    val userId: String,
    val shiftId: String?,
    val status: SaleStatus,              // COMPLETED | VOIDED
    val subtotalPiastres: Long,
    val discountPiastres: Long,
    val taxPiastres: Long,
    val totalPiastres: Long,
    val tenderedPiastres: Long,
    val changePiastres: Long,
    val occurredAt: Long,
    val voidedAt: Long?, val voidedByUserId: String?, val voidReason: String?,
)
```

`sale_line` carries `unitCostPiastres` — **a snapshot**. Margin computed against today's cost would
change every historical report the next time a supplier raises a price.

`payment`, `shift`, `held_sale`, `held_sale_line` as described above.

**Receipt numbers** are `MAX(receiptNumber) + 1` allocated *inside* the sale transaction, with a
unique index so that two tills sharing a database in Phase 9 collide loudly rather than silently
issuing the same number twice. The UUID stays the identity; the number is for humans.

### `:features:sell`

```
features/sell/
├── domain/
│   ├── model/Basket.kt              Basket, BasketLine, BasketTotals
│   └── usecase/
│       ├── CalculateBasketTotalUseCase.kt
│       ├── FindSellableUseCase.kt          barcode, then SKU, then name
│       ├── CompleteSaleUseCase.kt          the one transaction
│       ├── PrintReceiptUseCase.kt          after the commit, never inside
│       ├── ReauthenticateUseCase.kt
│       ├── VoidSaleUseCase.kt
│       ├── HeldSaleUseCases.kt
│       └── ShiftUseCases.kt                open, close, Z-report
├── presentation/screens/till/
├── presentation/screens/shift/
└── di/SellModule.kt
```

### `:core` additions

`ISaleRepository`, `IShiftRepository`, `IHeldSaleRepository`, `IPriceRepository`, the domain models
behind them, `core/domain/auth/`, and `ShopSettings` gaining `shopName`, `shopNameAr`,
`addressLine`, `vatBasisPoints`.

### `:features:catalog`

Price entry per variant in the product editor, writing through `IPriceRepository`. The smallest
change that makes the catalogue sellable.

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No returns or exchanges.** Phase 8, with the receipt-QR scan the QR was put there for.
- **No customers, no accounts, no invoices.** Phase 7, gated on Q1.
- **No cost method.** Lines snapshot cost; *which* cost (moving average or FIFO) is Phase 6's 6a and
  needs its own ADR. Snapshotting now is what makes that decision still available.
- **No offline/online anything.** There is no server until Phase 9.
- **No loyalty, no gift cards, no promotions engine.** A line discount and an order discount cover
  what a clothing shop actually does; a rules engine nobody configures is a slower `when`.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Lines reconcile to the header | Property-ish test: many baskets, assert Σ line totals == header total, Σ line tax == header tax |
| 2 | Order discount survives allocation | 100 discount over 3 lines → parts sum to exactly 100, deterministic |
| 3 | Atomicity | Force a failure mid-commit; assert no sale, no line, no payment, no movement |
| 4 | Stock is written once per line | Complete a sale; ledger has exactly one movement per line, signed negative, `refId` = sale |
| 5 | Attribution | Every movement and the header carry the signed-in `userId` (Phase 4 check 6, now real) |
| 6 | Receipt number | Concurrent completes produce distinct consecutive numbers; the unique index holds |
| 7 | Change due | Split cash+card, over-tendered cash → correct change; under-tender is refused |
| 8 | Void | Reversal movements net on-hand back to the pre-sale figure; original sale row still present and `VOIDED` |
| 9 | Permission in the domain | `CompleteSaleUseCase` with a discount and a SELLER session → `ForbiddenAccess`, **UI bypassed** |
| 10 | Re-auth locks out | 5 bad admin passwords at the override dialog → locked, same counter as sign-in |
| 11 | Hold and resume | Park a cart, restart the app, resume it — quantities, discounts and prices intact |
| 12 | Z-report | Float + cash sales == expected cash; a counted difference is reported, not hidden |
| 13 | Print failure ≠ lost sale | Transport that always fails → sale committed, error surfaced, reprint works |
| 14 | Migration v3 → v4 | KD-002 harness: seed v3, migrate, assert catalogue, ledger and users intact |
| 15 | Negative stock | Selling 3 of 1 on hand completes, warns, and leaves on-hand at −2 |

Checks 1 and 3 are the ones that matter. Everything else is recoverable; a till that loses money in
rounding or writes half a sale is not.

## Definition of Done

- [x] Scan, cart, tender, print, ledger — end to end on a fresh install
- [x] Lines always reconcile to the header, proven by test
- [x] Sale + lines + payments + movements are atomic; printing is outside the transaction
- [x] Split tender and change due work; under-tender is refused
- [x] Discount, price override and void each check permission in the domain layer and record who approved
- [x] Shifts open and close; the Z-report reconciles
- [x] Held sales survive a restart
- [x] Prices can be set in the catalogue, and a fresh install seeds a location and a RETAIL list
- [x] v3 → v4 migration test green
- [x] `./scripts/check-gates.sh` and `./gradlew allTests` pass

## What changed while building it

Four things worth recording, because they are the sort of thing a plan cannot know in advance:

1. **`subtotal` became gross rather than net** (above), so the receipt's own arithmetic closes.
2. **Approvals are verified, not trusted.** The plan had the authorising user's id recorded on the
   line; the build also *looks that user up* and re-checks their permission before committing.
   An id in a field is a claim the screen makes.
3. **A void takes the approving user, not the session.** The first draft checked
   `sessions.require(VOID_SALE)`, which contradicts Phase 4's rule that a void needs a fresh
   credential *regardless of who is signed in*.
4. **`SellableDao` was added.** A scan resolving variant → product → colour → price → on-hand
   through four repositories is four round trips with a customer waiting; it is one indexed query.
