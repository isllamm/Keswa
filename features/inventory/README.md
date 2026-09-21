# `:features:inventory`

Stock coming in, and checking that what the database believes matches what is on the shelf.

## Cost is decided here — ADR [KD-008](../../docs/adr/KD-008-moving-average-cost.md)

Receiving is the only place `variant.costPiastres` changes, by moving weighted average:

```
newCost = (onHand × oldCost + received × receiptCost) / (onHand + received)   [HALF_EVEN]
```

With nothing on the shelf there is nothing to average against, so the delivery's own cost wins —
which also covers stock that has gone negative, since Phase 5 lets a shop sell below its figures.

FIFO was rejected because it needs a layer ledger replayed in order, and that fights the
commutative append-only design that makes Phase 9's sync need no conflict resolution.

**Every movement carries a `unitCostPiastres` snapshot.** The receipt's movement carries what *that
delivery* cost, not the new average — so the ledger describes its own cost basis and Phase 8 reads
it rather than recomputing anything. Movements written before Phase 6 carry `null`, which is
honest: they had no cost recorded, and a zero would read as "it was free".

## Decisions worth knowing

**A delivery is a draft until it is posted.** Unpacking boxes takes twenty minutes and a phone
call; a `DRAFT` moves no stock, so being interrupted costs nothing. One `POST` writes every
movement and every cost change together, guarded on the status inside the `UPDATE` so a double
click cannot receive the same carton twice.

**Counts are blind, and the schema is what enforces it.** `stock_count_line.expectedQuantity` is
null until the count is posted, and there is deliberately no query that would return it earlier —
so a screen has nothing to leak. A counter who can see that the system expects twelve will count
until they get twelve, not dishonestly but because the eye finds what it is told to look for, and
the discrepancy that would have told the owner something disappears.

Posting writes one `COUNT` movement per line that **differs**. A line that agrees writes nothing: a
ledger entry saying "nothing changed" is noise that makes the real ones harder to find.

**Adjustments need a reason in words.** `MovementReason` says the category; "three shirts
water-damaged in the stockroom" is the fact. `stock_movement.note` is the difference between an
audit trail and a list of numbers.

**Receiving and writing-off need `RECEIVE_STOCK`; counting needs `COUNT_STOCK`.** A seller has the
second and not the first — walking a rail with a scanner is the shop floor's job, deciding that
three shirts no longer exist is not. `InventoryPermissionsTest` proves the check happens *before*
anything reaches the data layer, by handing the use cases repositories that explode on contact.

**Hang tags print one per piece**, from the posted receipt's own lines, so nobody re-types "how
many navy mediums did we get". A variant with no barcode is reported rather than printed blank — a
tag that cannot be scanned at the till has no reason to exist.

**An import is validated as a whole and applied as a whole.** One bad row imports nothing, and
every problem names its line. A repeated SKU is caught here, where "row 41 repeats row 12" is
fixable, rather than by the unique index, where "UNIQUE constraint failed" is not. Applying reuses
the ordinary creation paths and brings stock in through a real receipt — an import that bypasses
the rules is how a catalogue acquires products with no category and prices nobody agreed.

## Deviations from the Phase 6 outline

**Goods receipts, not purchase orders.** A PO is raised *before* the goods arrive and earns its
keep through the expected-versus-actual comparison. That is supplier management, and it belongs
with the credit and supplier work in Phase 7. What a shop needs now is to record what arrived and
what it cost — the van is already outside. A PO can become an optional parent document later
without changing a receipt's shape.

**Import takes pasted text, not a file.** The parser, the validation and the apply path are all
here; choosing a file needs a new platform bridge on two targets and is the least interesting part
of the feature. Flagged as unfinished rather than quietly dropped.

## Not here yet

Transfers between locations (`TRANSFER_IN`/`TRANSFER_OUT` exist in the enum from Phase 1, but a
single-shop install has nowhere to transfer to — they land with multi-branch in Phase 9), a label
layout editor (a shop that wants a different tag wants a different tag, not a layout engine), and
what a return does to average cost (Phase 8, with the rest of the return policy).
