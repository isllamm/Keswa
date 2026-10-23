# Receiving by Category — Plan

> **Status: 📝 DRAFT — waiting on the six open questions at the end**
> Touches: `:core`, `:features:catalog`, `:features:inventory`, `:features:settings`, `:composeApp`
> Branch: `feature/receiving-by-category`, from `dev` (see `docs/BRANCHING.md`)
> Database: v8 → **v9** (one nullable column)

## Goal

In الاستلام, the operator picks a category, then a sub-category, and types a quantity, cost and
selling price against each colour filed there. They add the lines to the delivery and print the
labels — for one line at any time, or for the whole delivery after posting.

Categories and sub-categories are built first, in the Catalogue.

---

## Decisions

Agreed on 25 Sep 2026.

| # | Question | Answer | What it means for the build |
|---|---|---|---|
| D1 | What gets the quantity? | The colours **already in the Catalogue** | Nothing is created while receiving. Stock stays on the variant, so the till, the ledger, costs and sync keep working as they are. |
| D2 | How many levels? | **Any depth** | The picker walks down one level at a time, with a path at the top to go back. |
| D3 | Barcodes | **One per item** (per colour) | Same as today. 20 shirts = 20 labels with the same code. No new barcode table. |
| D4 | What is typed per line? | **Quantity, cost and selling price** | A price field joins the line, filled in with the current price. |
| D5 | When does a typed price reach the till? | **When the delivery is posted** | The price is held on the line. A draft still changes nothing. Needs one new column (v9). |
| D6 | When are labels printed? | **Both**: per line at any time, and all after posting | A **Print labels** button on each line. The existing "Print hang tags" after posting stays. |
| D7 | Name on the label | **Arabic** item and colour | The printer cannot draw Arabic, so the app draws the name as a picture first. |
| D8 | Where are categories managed? | The **Catalogue** screen, improved | Adds a separate Arabic name, rename, and hide/show. |

---

## What exists today

The plan builds on these. None of them change shape.

- **Category tree** — `category` table: `parentId`, a materialised `path` (`/men/shirts/`),
  `depth`, `sortOrder`, `isActive`, `name`, `nameAr`. Any depth. Created in the Catalogue with
  "Add top level" / "Add under selected" — but the same text is saved as both the English and the
  Arabic name, and there is no rename or hide.
- **Product** — filed under exactly one category (`categoryId`, required).
- **Variant** — a product in one colour. Stock, barcodes, cost and price all hang off it.
- **Receipt** — a `DRAFT` moves nothing. One `POST` writes every movement and cost change in one
  transaction. One line per variant: adding the same colour again replaces its line.
- **Labels** — TSPL over TCP, 40 × 30 mm by default, one tag per piece. Today the tag shows the
  SKU (twice), the price and an EAN-13.
- **Module rule** — one feature cannot import another. The Catalogue's `CategoryTree` component
  cannot be used by the receiving screen, so the picker is new.

---

## How it works — the shop's view

### 1. Catalogue: build the tree

```
Categories
─────────────────────────────────────────────
▾ Men                           [Rename] [Hide]
   ▾ Shirts                     [Rename] [Hide]
      · Oxford                  [Rename] [Hide]
      · Linen                   [Rename] [Hide]
   · Trousers                   [Rename] [Hide]
· Women   (hidden, greyed)      [Rename] [Show]

English name [ Shirts ]    Arabic name [ قمصان ]
[Add top level]  [Add under selected]
```

Products and colours are filed under the tree exactly as today.

### 2. الاستلام: pick, type, add

Start a delivery as today (reference, supplier). Then:

```
All categories ▸ Men ▸ Shirts
[ Oxford › ]  [ Linen › ]

Item           Colour   On hand   Qty      Cost       Price
Plain shirt    Navy        4     [ 20 ]   [ 120.00 ]  [ 250.00 ]
Plain shirt    White       0     [ 30 ]   [ 120.00 ]  [ 250.00 ]
Plain shirt    Black       2     [    ]   [ 118.00 ]  [ 240.00 ]

                              [Clear]  [Add to delivery (2)]
```

- Tap a sub-category to go down. Tap any part of the path to go back up.
- Each level shows its sub-categories, then the colours of the products filed **directly** there.
- Cost and price are filled in: from the line already on the delivery if there is one, otherwise
  from the item's current cost and price.
- What you type is kept when you move to another category, until you add it or clear it.
- **Add to delivery** adds every row that has a quantity. A colour already on the delivery has its
  line replaced, as scanning does today. An item with no barcode gets one now, as today.
- If any row is wrong, nothing is added and the bad rows are marked.

### 3. Print

```
Plain shirt — Navy                                          2,400.00
PLA-NAV · 2000000000015 · 20 × 120.00 · new price 250.00 on posting
                                              [Print labels]  [✕]
```

**Print labels** asks how many copies (the line's quantity to start with) and prints them with
the price the line will have after posting.

**Post** brings the stock in, averages the cost and puts the new prices live — all in one
transaction. After posting, **Print hang tags** prints one label per piece, as today.

### 4. Scanning still works

The scan box stays. Its dialog gets a **Price** field and follows the same rules as the grid.

### The label

```
┌──────────────────────────────┐
│          قميص سادة — كحلي    │  ← name, drawn as a picture (Arabic, right-aligned)
│ PLA-NAV                      │  ← SKU
│ EGP 250.00                   │  ← price
│ ▌▌▌ ▌▌ ▌▌▌▌ ▌ ▌▌▌ ▌▌ ▌▌▌▌   │  ← EAN-13
│ 2 000000 000015              │
└──────────────────────────────┘
```

---

## Step 1 — Categories in the Catalogue

### 1.1 Repository (`:core`)

[ICategoryRepository.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/domain/repository/ICategoryRepository.kt) — add:

```kotlin
suspend fun rename(id: String, name: String, nameAr: String): Result<Category>

/** Hides or shows one category. Its products are not touched — they still sell and still scan. */
suspend fun setActive(id: String, isActive: Boolean): Result<Unit>

/** The active categories directly under [parentId]; the top level when it is null. */
suspend fun children(parentId: String?): Result<List<Category>>
```

[CatalogDaos.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/database/dao/CatalogDaos.kt) — `CategoryDao` gains:

```kotlin
@Query("SELECT * FROM category WHERE parentId IS :parentId AND isActive = 1 ORDER BY sortOrder, name")
suspend fun getActiveChildren(parentId: String?): List<CategoryEntity>
```

[CategoryRepositoryImpl.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/data/repository/CategoryRepositoryImpl.kt) —
`rename` and `setActive` read the row, `copy(...)` it with a new `updatedAt`, and `upsert` it.
The path does not change on rename, because the path is built from ids, not names.

Hiding needs no cascade. The picker walks down from the top, so the children of a hidden category
cannot be reached through it.

### 1.2 Use cases (`:features:catalog`)

[CategoryUseCases.kt](../features/catalog/src/commonMain/kotlin/com/alsoug/keswa/features/catalog/domain/usecase/CategoryUseCases.kt) — add:

```kotlin
class RenameCategoryUseCase(
    private val repository: ICategoryRepository,
    private val sessions: ISessionManager,
) {
    /** A blank English name is refused; a blank Arabic name falls back to the English, as create does. */
    suspend operator fun invoke(categoryId: String, name: String, nameAr: String): Result<Category>
}

class SetCategoryActiveUseCase(
    private val repository: ICategoryRepository,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(categoryId: String, isActive: Boolean): Result<Unit>
}
```

If open question **Q6** is agreed, `CreateCategoryUseCase` and `MoveCategoryUseCase` also take
`ISessionManager` and call `sessions.require(Permission.MANAGE_CATALOGUE)` first, as the two new
ones do. Wire all four in
[CatalogModule.kt](../features/catalog/src/commonMain/kotlin/com/alsoug/keswa/features/catalog/di/CatalogModule.kt).

### 1.3 Screen (`:features:catalog`)

- `CategoryNodeUiModel` in
  [UiModels.kt](../features/catalog/src/commonMain/kotlin/com/alsoug/keswa/features/catalog/presentation/model/UiModels.kt)
  gains `name`, `nameAr` (to fill in the rename dialog) and `isActive`.
- [CategoryTree.kt](../features/catalog/src/commonMain/kotlin/com/alsoug/keswa/features/catalog/presentation/components/CategoryTree.kt)
  greys out a row whose `isActive` is false. Its `trailing` slot carries the **Rename** and
  **Hide/Show** buttons.
- [CatalogBrowserScreen.kt](../features/catalog/src/commonMain/kotlin/com/alsoug/keswa/features/catalog/presentation/screens/catalogbrowser/CatalogBrowserScreen.kt):
  the add row gets two fields, **English name** and **Arabic name**, and sends both in the
  existing `CreateCategory(name, nameAr, underSelected)` event. Today it sends `name, name`.
- The rename dialog lives in `remember`, not in state (ADR-030).
- New events in
  [CatalogBrowserUDF.kt](../features/catalog/src/commonMain/kotlin/com/alsoug/keswa/features/catalog/presentation/screens/catalogbrowser/CatalogBrowserUDF.kt):

```kotlin
data class RenameCategory(val categoryId: String, val name: String, val nameAr: String) :
    CatalogBrowserUiEvent
data class SetCategoryActive(val categoryId: String, val isActive: Boolean) : CatalogBrowserUiEvent
```

- Previews: add a hidden row to the success preview.

### 1.4 Tests

| Test | File |
|---|---|
| Rename keeps the path and the products | [CategoryTreeTest.kt](../core/src/desktopTest/kotlin/com/alsoug/keswa/core/data/CategoryTreeTest.kt) |
| A hidden category is left out of `children` | same |
| `children(null)` returns the top level only, in `sortOrder` then name order | same |
| Blank name refused; blank Arabic name falls back to English | `features/catalog/src/commonTest/.../domain/usecase/CategoryUseCasesTest.kt` (new) |
| Without `MANAGE_CATALOGUE`, create, rename, hide and move are refused before the repository is touched (if Q6 is agreed) | same |
| Rename and hide update the tree and show a message | `features/catalog/src/commonTest/.../presentation/CatalogBrowserViewModelTest.kt` (new) |

### Done when

- [x] A category can be added with separate English and Arabic names
- [x] A category can be renamed, hidden and shown again
- [x] Hidden categories are greyed out in the Catalogue

---

## Step 2 — Price on the delivery line (database v9)

### 2.1 Schema

[InventoryEntities.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/database/entities/InventoryEntities.kt) — `StockReceiptLineEntity` gains one column:

```kotlin
/**
 * A retail price typed while receiving, applied when the receipt is posted.
 *
 * Null means "leave the price alone". Held here rather than written straight away, so a draft
 * still changes nothing and a discarded one leaves no trace.
 */
val sellPricePiastres: Long? = null,
```

[Migrations.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/database/migrations/Migrations.kt):

```kotlin
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `stock_receipt_line` ADD COLUMN `sellPricePiastres` INTEGER")
    }
}
```

- Add `MIGRATION_8_9` to `ALL_MIGRATIONS`.
- [KeswaDatabase.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/database/KeswaDatabase.kt), line 103:
  `version = 9, // 9: stock_receipt_line.sellPricePiastres — a price typed while receiving, applied on post`
- Commit the generated `core/schemas/com.alsoug.keswa.core.database.KeswaDatabase/9.json`.

### 2.2 Domain and mapper

- [Inventory.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/domain/model/Inventory.kt):
  `StockReceiptLine` gains `val sellPrice: Money? = null`.
- [SaleMappers.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/data/mapper/SaleMappers.kt)
  (`StockReceiptLineEntity.toDomain`, line 199): `sellPrice = sellPricePiastres?.let(Money::ofPiastres)`.

### 2.3 Repository

[InventoryRepositories.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/domain/repository/InventoryRepositories.kt) — `IStockReceiptRepository`:

```kotlin
/** One line to put on a draft. [sellPrice] null means "leave the retail price alone". */
data class ReceiptLineInput(
    val id: String,
    val variantId: String,
    val quantity: Int,
    val unitCost: Money,
    val sellPrice: Money? = null,
)

/**
 * Puts several lines on a draft in one transaction — all of them, or none.
 *
 * Same rule as [putLine]: a colour already on the delivery has its line replaced, not doubled.
 */
suspend fun putLines(receiptId: String, lines: List<ReceiptLineInput>): Result<StockReceipt>
```

`putLine` gains `sellPrice: Money? = null` at the end, so the import keeps compiling, and becomes
a one-line call to `putLines`.

[InventoryRepositoryImpls.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/data/repository/InventoryRepositoryImpls.kt) — `StockReceiptRepositoryImpl`:

- The constructor takes `PriceDao`.
  [CoreModule.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/di/CoreModule.kt) passes
  `get<KeswaDatabase>().priceDao()`.
- `putLines` runs inside `database.inTransaction { }` with the same checks as `putLine` (quantity
  above 0, cost not negative, receipt still a draft), plus: price not negative.
- `post()` — inside the existing transaction, after the stock movements:

```kotlin
val priced = lines.filter { it.sellPricePiastres != null }
if (priced.isNotEmpty()) {
    val listId = requireNotNull(prices.getDefaultList()?.id) { "no default price list" }
    priced.forEach { line ->
        val live = prices.getEffectivePrice(line.variantId, listId, atMillis)?.pricePiastres
        // A price that did not change writes nothing: a history full of "same again" hides the
        // changes that matter.
        if (live != line.sellPricePiastres) {
            prices.closeCurrent(line.variantId, listId, atMillis)
            prices.upsertPrice(
                PriceEntity(
                    id = ids.newId(),
                    priceListId = listId,
                    variantId = line.variantId,
                    pricePiastres = requireNotNull(line.sellPricePiastres),
                    validFrom = atMillis,
                    validTo = null,
                ),
            )
        }
    }
}
```

The new price starts at the posting time, so the till sells the new stock at the new price from
the moment it has it.

### 2.4 Sync

Nothing to register:

- `stock_receipt_line` already syncs with its receipt on post (`SyncTables.kt`).
- `RowCodec` reads every column (`SELECT *`), so the new column travels by itself.
- `price` is already a synced record, so new price rows go out as they do from the Catalogue.

But see **Risk R1**: every device must be on v9 before any device posts a delivery.

### 2.5 Tests

| Test | File |
|---|---|
| v8 → v9: a draft and a posted receipt survive intact; the new column is null | `core/src/desktopTest/.../database/Migration8To9Test.kt` (new, same shape as `Migration7To8Test`) |
| A price on a draft line does not reach the till | [StockReceiptTest.kt](../core/src/desktopTest/kotlin/com/alsoug/keswa/core/database/StockReceiptTest.kt) |
| Posting puts the new price live from the posting time, and closes the old one | same |
| A line whose price equals the live price writes no price row | same |
| A post that fails part-way changes no stock, no cost and no price | same |
| Discarding a draft leaves the price alone | same |
| `putLines` with one bad line saves none of them | same |
| `putLines` for a colour already on the delivery replaces its line | same |

### Done when

- [x] The v9 migration and its test are green
- [x] A typed price goes live on post, in the same transaction, and never before

---

## Step 3 — Finding items by category, and adding many lines

### 3.1 Query (`:core`)

[SellableDao.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/database/dao/SellableDao.kt) — a fourth query, with the same row as the other three:

```kotlin
@Query(
    """
    SELECT v.id AS variantId, v.productId AS productId, v.sku AS sku,
           p.name AS name, p.nameAr AS nameAr,
           c.name AS colourName, c.nameAr AS colourNameAr,
           v.costPiastres AS costPiastres,
           (SELECT pr.pricePiastres FROM price pr
             WHERE pr.variantId = v.id AND pr.priceListId = :priceListId
               AND pr.validFrom <= :at AND (pr.validTo IS NULL OR pr.validTo > :at)
             ORDER BY pr.validFrom DESC LIMIT 1) AS pricePiastres,
           COALESCE((SELECT soh.quantity FROM stock_on_hand soh
                      WHERE soh.variantId = v.id AND soh.locationId = :locationId), 0) AS onHand
    FROM variant v
    JOIN product p ON p.id = v.productId
    JOIN colour c ON c.id = v.colourId
    WHERE p.categoryId = :categoryId AND p.isActive = 1 AND v.isActive = 1
    ORDER BY p.name, c.sortOrder, c.name
    """,
)
suspend fun inCategory(
    categoryId: String,
    priceListId: String,
    locationId: String,
    at: Long,
): List<SellableRow>
```

`product.categoryId` is already indexed.

- `ISellableRepository.inCategory(...)` in
  [SaleRepositories.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/domain/repository/SaleRepositories.kt),
  and its implementation in `SellableRepositoryImpl`.
- [FindStockItemUseCase.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/domain/usecase/FindStockItemUseCase.kt):
  `suspend fun inCategory(categoryId: String, locationId: String): Result<List<SellableItem>>`.

### 3.2 `BrowseCategoryUseCase` (new, `:features:inventory`)

```kotlin
/** What one level of the picker shows: where you can go down to, and what is filed right here. */
data class CategoryLevel(
    val children: List<Category>,
    val items: List<SellableItem>,
)

class BrowseCategoryUseCase(
    private val categories: ICategoryRepository,
    private val find: FindStockItemUseCase,
) {
    /** [categoryId] null is the top level. It has no items: a product is always filed under a category. */
    suspend operator fun invoke(categoryId: String?, locationId: String): Result<CategoryLevel>
}
```

### 3.3 `AddReceiptLinesUseCase` (new)

One path for both the grid and the scan dialog, so both follow the same rules.

```kotlin
/** What was typed against one colour. Text, not numbers: turning it into numbers is a rule. */
data class PickedEntry(
    val item: SellableItem,
    val quantity: String,
    val cost: String,
    val price: String,
)

sealed interface AddLinesResult {
    data class Added(val receipt: StockReceipt, val lines: Int) : AddLinesResult

    /** Nothing was added. Every bad row is named, so they can all be fixed in one go. */
    data class Rejected(val problems: Map<String, EntryProblem>) : AddLinesResult
}

enum class EntryProblem { NOT_A_QUANTITY, NOT_AN_AMOUNT, PRICE_REQUIRED }

class AddReceiptLinesUseCase(
    private val receipts: IStockReceiptRepository,
    private val sessions: ISessionManager,
    private val ensureBarcode: EnsureVariantBarcodeUseCase,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(receiptId: String, entries: List<PickedEntry>): Result<AddLinesResult>
}
```

Order of work:

1. `sessions.require(Permission.RECEIVE_STOCK)`.
2. Drop rows with a blank quantity — they were not typed.
3. Check every remaining row (table below). Any problem → `Rejected`, nothing written.
4. Work out each row's price change: `sellPrice = typedPrice.takeIf { it != item.price }`.
5. If any row changes a price → `sessions.require(Permission.MANAGE_CATALOGUE)`, the same
   permission the Catalogue needs to set a price.
6. `ensureBarcode(variantId)` for each row, as `AddReceiptLineUseCase` does today.
7. `receipts.putLines(receiptId, lines)`.

| Field | Blank | Rule | Problem |
|---|---|---|---|
| Quantity | row skipped | a whole number above 0 | `NOT_A_QUANTITY` |
| Cost | refused | an amount, not negative | `NOT_AN_AMOUNT` |
| Price | allowed only if the item already has a price, which is then kept | an amount, not negative — the Catalogue's rule | `NOT_AN_AMOUNT`, or `PRICE_REQUIRED` if blank and the item has no price (open question **Q4**) |

A quantity of `0` is `NOT_A_QUANTITY`. Taking a line off is the ✕ on the line.

`Rejected` is a business outcome, not an exception, as the Catalogue's `AddColourResult` is.
Infrastructure failure stays in the outer `Result`.

### 3.4 Posting checks the price permission too

[ReceiveStockUseCases.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/domain/usecase/ReceiveStockUseCases.kt) —
`PostReceiptUseCase`: if any line carries a `sellPrice`, also require `MANAGE_CATALOGUE`. A line
can be added by one person and posted by another, and the permission must hold at the moment the
price actually changes.

`AddReceiptLineUseCase` stays for the import, which sets its own prices first. It gains
`sellPrice: Money? = null` and passes it through.

### 3.5 Wiring

[InventoryModule.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/di/InventoryModule.kt):

```kotlin
factory { BrowseCategoryUseCase(get(), get()) }
factory { AddReceiptLinesUseCase(get(), get(), get(), get()) }
```

`ReceivingUseCases` gains `browse` and `addLines`.

### 3.6 Tests (`:features:inventory`, commonTest)

Fakes in [InventoryFakes.kt](../features/inventory/src/commonTest/kotlin/com/alsoug/keswa/features/inventory/fakes/InventoryFakes.kt):
`FakeSellableRepository.inCategory`, `FakeCategoryRepository.children`,
`FakeStockReceiptRepository.putLines`, and the new `sellPrice` on lines.

| Test |
|---|
| The top level lists the top categories and no items |
| A category lists its active children and the colours filed directly in it |
| A blank quantity is skipped; `0`, `abc` and `1.5` are `NOT_A_QUANTITY` |
| A bad cost or price is `NOT_AN_AMOUNT`, and nothing is added |
| A blank price on an item with no price is `PRICE_REQUIRED` |
| A blank price on a priced item keeps the price (no `sellPrice` stored) |
| The same price as today stores no `sellPrice` |
| A changed price without `MANAGE_CATALOGUE` is refused before the repository is touched |
| Every added item has a barcode afterwards |
| `PostReceiptUseCase` refuses a priced receipt without `MANAGE_CATALOGUE` |

### Done when

- [x] Browsing a category returns its sub-categories and its colours
- [x] Many lines are added in one go, all or none, with every bad row named

---

## Step 4 — The receiving screen

### 4.1 Layout

Wide screen (desktop till):

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ ← الاستلام                                                                    │
│ [ Scan a garment to add it — colour by colour ]                               │
├────────────────────────────────┬──────────────────────────────┬───────────────┤
│ Pick from categories           │ Lines on this delivery       │ INV-4471      │
│ All ▸ Men ▸ Shirts             │ Plain shirt — Navy           │ Pieces     50 │
│ [Oxford ›]  [Linen ›]          │ 20 × 120.00 · new 250.00     │ Cost 6,000.00 │
│                                │        [Print labels]  [✕]   │               │
│ Item  Colour  Stock  Qty  Cost  Price                         │ [Post]        │
│ …grid…                         │ …                            │ [Discard]     │
│ [Clear] [Add to delivery (2)]  │                              │ Recent …      │
└────────────────────────────────┴──────────────────────────────┴───────────────┘
```

Handheld (narrow): the lines and summary show as today, with a **Pick from categories** button.
It opens the picker full screen, and **Add to delivery** returns to the lines. Whether the picker
is open is screen-only state in `remember`.

In Arabic the whole layout mirrors, right to left.

### 4.2 State and events

[ReceivingUDF.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/presentation/screens/receiving/ReceivingUDF.kt):

```kotlin
/** One level walked down in the picker. An empty list of crumbs is the top level. */
data class CategoryCrumb(val id: String, val name: String, val nameAr: String)

/** What has been typed against one colour — kept by variant id, so moving category loses nothing. */
data class TypedEntry(val item: SellableItem, val quantity: String, val cost: String, val price: String)

data class ReceivingUiState(
    // … everything there today …
    val priceEntry: String = "",                          // scan dialog
    val crumbs: List<CategoryCrumb> = emptyList(),
    val subCategories: List<Category> = emptyList(),
    val levelItems: List<SellableItem> = emptyList(),
    val typed: Map<String, TypedEntry> = emptyMap(),
    /** Rows the last "Add" refused, like the import's problems. Cleared as each row is changed. */
    val problems: Map<String, EntryProblem> = emptyMap(),
    val isAddingLines: Boolean = false,
) {
    val typedCount: Int get() = typed.values.count { it.quantity.isNotBlank() }

    val canAddLines: Boolean get() = isDraft && typedCount > 0 && !isAddingLines
}
```

Keeping `problems` in state follows `ImportUiState.problems`: they describe the form until it is
fixed, rather than being a one-off event.

New events:

```kotlin
data class OpenCategory(val categoryId: String) : ReceivingUiEvent
data class GoUpTo(val depth: Int) : ReceivingUiEvent                // tap a crumb; 0 = top level
data class PickQuantityChanged(val variantId: String, val value: String) : ReceivingUiEvent
data class PickCostChanged(val variantId: String, val value: String) : ReceivingUiEvent
data class PickPriceChanged(val variantId: String, val value: String) : ReceivingUiEvent
data object AddPicked : ReceivingUiEvent
data object ClearPicked : ReceivingUiEvent
data class PriceChanged(val value: String) : ReceivingUiEvent        // scan dialog
data class PrintLineLabels(val lineId: String, val copies: Int) : ReceivingUiEvent
```

`PrintVariantTag` goes: **Print labels** on the line replaces it. `GenerateBarcode` stays for old
lines that have no barcode.

### 4.3 View model

[ReceivingViewModel.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/presentation/screens/receiving/ReceivingViewModel.kt) —
holds state and delegates; no rules here (ADR-021):

- After `StartReceipt`, and after `Open` of a draft: load the top level with `receiving.browse(null)`.
- `OpenCategory` / `GoUpTo`: call `browse`, then update `crumbs`, `subCategories` and `levelItems`.
- `Pick…Changed`: update `typed` and drop that row from `problems`. On a row's first change, it
  is filled in from the delivery's line, or else from the item's cost and price.
- `AddPicked`: `receiving.addLines(receiptId, typed.values.toList())`.
  - `Added` → `show(receipt)`, clear `typed` and `problems`, message "2 lines added".
  - `Rejected` → set `problems`, message "2 rows need fixing".
- `ConfirmLine` (scan dialog): the same `addLines` call with one entry. A problem shows as an
  error message.
- `PrintLineLabels` → `labels.printLineLabels(receiptId, lineId, copies)`.

### 4.4 Screen

[ReceivingScreen.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/presentation/screens/receiving/ReceivingScreen.kt):

- New `CategoryPicker` composable in `presentation/components/`: the path, the sub-category
  chips and the grid. Slot-based (ADR-011), so the screen passes in the grid's fields.
- Grid rows show `localisedName(...)` for item and colour, the on-hand figure, and three fields.
  A row in `problems` shows its field in the error colour with a short reason.
- Line row: shows "new price 250.00 on posting" when `sellPrice` is set, and **Print labels**.
  The copies dialog is in `remember` (ADR-030), filled in with the line's quantity.
- Scan dialog: a **Price** field under **Unit cost**.
- Empty level: "Nothing filed here yet — add products and colours in the Catalogue".
- Previews (ADR-027): loading, picker at the top level, picker inside a category with typed rows
  and one problem, posted delivery.

[EnrichReceiptLinesUseCase.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/domain/usecase/EnrichReceiptLinesUseCase.kt):
`ReceiptLineUiModel` gains `sellPrice: Money?` and `livePrice: Money?`, so the line can show
"230.00 → 250.00".

### 4.5 Strings

Added to `Strings`, `EnglishStrings` and `ArabicStrings` in
[Strings.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/designsystem/Strings.kt).

- Every new property goes into `STRING_READERS` in
  [StringsTest.kt](../core/src/commonTest/kotlin/com/alsoug/keswa/core/designsystem/StringsTest.kt).
- Every new function gets its own line in "the labels that carry a value are translated too".
- Reuse a key where one already exists: `quantity`, `cost`, `price`, `onHandLegend`, `clear`,
  `cancel`, `categories`.

| Key | English | Arabic |
|---|---|---|
| `pickFromCategories` | Pick from categories | اختر من الأقسام |
| `allCategories` | All categories | كل الأقسام |
| `item` | Item | الصنف |
| `colour` | Colour | اللون |
| `addToDelivery(count)` | Add to delivery ($count) | أضف إلى الاستلام ($count) |
| `linesAdded(count)` | $count lines added | أُضيف $count إلى الاستلام |
| `rowsNeedFixing(count)` | $count rows need fixing | $count صف يحتاج تصحيحًا |
| `priceRequired` | This item has no price yet — set one | هذا الصنف بلا سعر بعد — حدّد سعرًا |
| `newPriceOnPosting(price)` | new price $price on posting | السعر الجديد $price عند الترحيل |
| `nothingFiledHere` | Nothing filed here yet — add products and colours in the Catalogue | لا شيء هنا بعد — أضف المنتجات والألوان من الأصناف |
| `printLabels` | Print labels | اطبع التيكيت |
| `copies` | Copies | عدد النسخ |
| `print` | Print | طباعة |
| `englishName` | English name | الاسم بالإنجليزية |
| `arabicName` | Arabic name | الاسم بالعربية |
| `rename` | Rename | إعادة تسمية |
| `hide` | Hide | إخفاء |
| `show` | Show | إظهار |
| `hidden` | Hidden | مخفي |
| `categoryRenamed` | Category renamed | تم تغيير اسم القسم |
| `categoryHidden` | Category hidden | تم إخفاء القسم |
| `categoryShown` | Category shown | تم إظهار القسم |

The Arabic wording should be checked by someone at the shop before release.

### 4.6 Tests

[ReceivingViewModelTest.kt](../features/inventory/src/commonTest/kotlin/com/alsoug/keswa/features/inventory/presentation/ReceivingViewModelTest.kt) — add:

| Test |
|---|
| Starting a delivery loads the top level |
| Opening a category shows its sub-categories and items; going up returns to the parent |
| Typed values survive moving to another category and back |
| A row already on the delivery is filled in from its line |
| Add with good rows adds the lines and clears what was typed |
| Add with a bad row adds nothing and marks that row |
| Changing a marked row clears its mark |
| The scan dialog's price reaches the line |
| Print labels on a line sends that line's copies |

### Done when

- [x] A delivery can be built from the picker alone, without scanning
- [x] The screen works in Arabic and English, and at the handheld's width

---

## Step 5 — Labels with the Arabic name and the right price

### 5.1 Drawing the name (`:core`, a platform bridge — KD-005)

New `ILabelNameRenderer.kt` in `core/src/commonMain/kotlin/com/alsoug/keswa/core/platform/`:

```kotlin
/**
 * Draws one line of text as dots, for the name on a hang tag.
 *
 * A platform bridge for the same reason as [IReceiptRenderer]: TSPL firmware cannot shape Arabic,
 * so the name is sent as a picture.
 */
interface ILabelNameRenderer {
    /** Fits [text] in [widthDots] × [maxHeightDots]: shrinks the font first, then cuts it with "…". */
    fun render(text: String, widthDots: Int, maxHeightDots: Int): MonoBitmap
}
```

| Platform | File | How |
|---|---|---|
| Desktop | `core/src/desktopMain/.../printing/DesktopLabelNameRenderer.kt` | Java2D `TextLayout` (it shapes Arabic and applies bidi), SansSerif, no antialiasing, threshold 128 — the same set-up as `DesktopReceiptRenderer` |
| Android | `core/src/androidMain/.../printing/AndroidLabelNameRenderer.kt` | `Canvas` and `Paint`, `Typeface.SANS_SERIF` — the same set-up as `AndroidReceiptRenderer` |

- Font size: start at 30 px and step down to 18 px until it fits. If it is still too wide, cut
  the text and add "…".
- Arabic is right-aligned inside the picture, English left-aligned.
- Bind it next to the receipt renderer:
  - [PlatformModule.desktop.kt](../composeApp/src/desktopMain/kotlin/com/alsoug/keswa/di/PlatformModule.desktop.kt):
    `single<ILabelNameRenderer> { DesktopLabelNameRenderer() }`
  - [PlatformModule.android.kt](../composeApp/src/androidMain/kotlin/com/alsoug/keswa/di/PlatformModule.android.kt):
    `single<ILabelNameRenderer> { AndroidLabelNameRenderer() }`

### 5.2 Where the name goes

`Tspl.label()` in [Tspl.kt](../core/src/commonMain/kotlin/com/alsoug/keswa/core/printing/tspl/Tspl.kt)
already takes `nameBitmap` and places it at (10, 10). On the default 40 × 30 mm label at 203 dpi
(324 × 243 dots), the SKU line starts at y = 54, so the name picture must be at most
**304 × 40 dots**. `Tspl.label()` itself does not change.

The picture is sent once per line and `PRINT n` repeats it, so 20 copies do not mean 20 pictures.

### 5.3 One tag builder (`:features:inventory`)

Three use cases print tags, and today each builds its own. They will share one builder so they
cannot drift apart:

```kotlin
/** The TSPL for one colour's tags. */
class HangTagBuilder(private val names: ILabelNameRenderer) {

    fun build(spec: LabelSpec, item: SellableItem, barcode: String, price: Money, copies: Int): ByteArray =
        Tspl.label(
            spec = spec,
            barcode = barcode,
            price = "EGP ${price.format()}",
            sku = item.sku,
            nameBitmap = names.render(tagName(item), spec.widthDots - 20, NAME_HEIGHT_DOTS),
            copies = copies,
        )

    /** Arabic when the product has an Arabic name; English otherwise, so a tag is never blank. */
    private fun tagName(item: SellableItem): String =
        if (item.nameAr.isNotBlank()) {
            "${item.nameAr} — ${item.colourNameAr.ifBlank { item.colourName }}"
        } else {
            item.description
        }

    private companion object {
        const val NAME_HEIGHT_DOTS = 40
    }
}
```

The item (names, SKU, live price) is read with `FindStockItemUseCase.byVariantId`.

### 5.4 Print use cases

Which price goes on the tag:

| Receipt | Line has a new price | Tag shows |
|---|---|---|
| Draft | yes | the line's new price |
| Draft | no | the live price |
| Posted | either | the live price (the new price is live by then) |

- **New** `PrintReceiptLineLabelsUseCase(receiptId, lineId, copies)` — needs `RECEIVE_STOCK`,
  makes sure of the barcode, uses the table above, and returns `LabelRunResult` like the others.
- [PrintHangTagsUseCase.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/domain/usecase/PrintHangTagsUseCase.kt) —
  uses `HangTagBuilder` and the same price rule. Still one tag per piece, and still names lines
  with no barcode instead of printing them blank.
- [PrintSingleVariantLabelUseCase.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/domain/usecase/PrintSingleVariantLabelUseCase.kt) —
  uses `HangTagBuilder`. It now needs a location to read the item; the Adjust screen already has one.
- [InventoryLabelUseCases.kt](../features/inventory/src/commonMain/kotlin/com/alsoug/keswa/features/inventory/domain/usecase/InventoryLabelUseCases.kt)
  gains `printLineLabels`.

### 5.5 Test label in Settings (`:features:settings`)

`Tspl.testLabel(spec)` gains `nameBitmap: MonoBitmap? = null`.
[PrintTestUseCases.kt](../features/settings/src/commonMain/kotlin/com/alsoug/keswa/features/settings/domain/usecase/PrintTestUseCases.kt)
renders "اختبار التيكيت — Test" with `ILabelNameRenderer` and passes it in. This shows at install
time whether the Arabic prints the right way round (see **R2**).

### 5.6 Tests

| Test | File |
|---|---|
| A label with a 304 × 40 name picture has `BITMAP 10,10,38,40,0,` and no `TEXT` for the name | [TsplTest.kt](../core/src/commonTest/kotlin/com/alsoug/keswa/core/printing/TsplTest.kt) |
| Arabic draws black dots, is 304 wide and at most 40 high; a very long name still fits | `core/src/desktopTest/.../printing/DesktopLabelNameRendererTest.kt` (new) |
| A draft line with a new price → the tag shows the new price; without one → the live price | `PrintReceiptLineLabelsUseCaseTest.kt` (new) |
| Copies sent = copies asked for | same |
| No printer → `NoPrinter`; printer off → `Unreachable` | same |
| A product with no Arabic name gets the English name | `HangTagBuilderTest.kt` (new) |
| The Koin graph resolves `ILabelNameRenderer` and the new use cases | [KoinGraphTest.kt](../composeApp/src/desktopTest/kotlin/com/alsoug/keswa/di/KoinGraphTest.kt) |

The fakes need a `FakeLabelNameRenderer` that returns a fixed small bitmap.

### Done when

- [x] Tags show the Arabic name, the SKU, the right price and the barcode
- [x] One line's tags print from the delivery, before or after posting
- [x] The test label in Settings prints an Arabic line

---

## Step 6 — Docs and checks

- [features/inventory/README.md](../features/inventory/README.md): a "Receiving by category"
  section — the price is held on the line and applied on post, one path for grid and scan, and
  tags carry the Arabic name as a picture.
- [features/catalog/README.md](../features/catalog/README.md): rename, hide, and the Arabic name.
- Gates:

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:assembleDebug
./gradlew allTests
./scripts/check-gates.sh
```

- By hand:
  - the Catalogue and الاستلام screens in Arabic and in English
  - الاستلام at the handheld's width
  - a test label and a real delivery's tags on the shop's label printer

---

## Verification plan

| # | Check | How |
|---|---|---|
| 1 | Categories have two names and can be renamed, hidden and shown | `CategoryTreeTest`, use-case tests |
| 2 | Hidden categories cannot be reached in the picker | `CategoryTreeTest`, `BrowseCategoryUseCase` test |
| 3 | A draft changes no price | `StockReceiptTest` |
| 4 | Posting puts prices live in the same transaction as the stock | `StockReceiptTest` |
| 5 | An unchanged price writes nothing | `StockReceiptTest` |
| 6 | Adding many lines is all or none, with every bad row named | `StockReceiptTest`, `AddReceiptLinesUseCase` test |
| 7 | Price changes need `MANAGE_CATALOGUE`, checked before any write | use-case tests with a session that lacks it |
| 8 | The tag shows the price the line will have | `PrintReceiptLineLabelsUseCase` test |
| 9 | The name picture fits and sits where TSPL expects it | `TsplTest`, `DesktopLabelNameRendererTest` |
| 10 | v8 → v9 keeps every receipt intact | `Migration8To9Test` |
| 11 | Arabic is translated, not pasted | `StringsTest` |
| 12 | Nothing else broke | `allTests`, `check-gates.sh`, both builds |

Checks 3 and 4 are the ones that keep "a draft moves nothing" true.

---

## Definition of done

- [x] Categories and sub-categories, any depth, with English and Arabic names, rename and hide
- [x] الاستلام can pick category ▸ sub-category and take a quantity, cost and price per colour
- [x] Many lines are added at once, all or none
- [x] A typed price goes live on post, never before
- [x] Labels print per line at any time, and all together after posting
- [x] Labels show the Arabic name
- [x] The v8 → v9 migration test is green
- [x] Both READMEs are updated
- [x] `./gradlew allTests` and `./scripts/check-gates.sh` pass

---

## Commits

One commit per step, in the repo's style, on `feature/receiving-by-category`:

1. `Catalogue: categories get an Arabic name, rename and hide`
2. `Inventory: a price typed while receiving is applied on post (schema v9)`
3. `Inventory: browse by category, and add many lines at once`
4. `Inventory: the receiving screen picks from categories`
5. `Printing: hang tags carry the Arabic name and the line's price`
6. `Docs: receiving by category`

Merge to `dev` with `--no-ff` once the Definition of Done is met.

---

## Risks

| # | Risk | What to do |
|---|---|---|
| R1 | **Devices on different versions.** Sync writes every column it receives, so a device still on v8 cannot apply a receipt line from a v9 device, and its sync gets stuck on that row. | Update every till and the handheld on the same day, before any updated device posts a delivery. |
| R2 | **The name prints as a negative.** TSPL printers disagree on whether a set bit is black, and `Tspl.bitmap` inverts by default. | Print the Settings test label on the real printer first. If the name comes out white on black, flip `invert`. |
| R3 | **No Arabic font on a till.** SansSerif falls back to whatever font the operating system has. | Receipts already depend on this, and the test label shows it at install. |
| R4 | **Long grids.** A category with many products gives a long grid. | File products into sub-categories. A filter box can come later. |

---

## Not in this plan

- Making new products or colours while receiving (D1)
- A different barcode for every piece (D3)
- Wholesale prices while receiving — they stay in the Catalogue (see **Q2**)
- Reordering categories (`sortOrder` exists but has no screen) and deleting them
- A list of changed prices after posting (the cost-change card could show them later)
- A search box inside the picker
- Tags for items with no price at all still print `EGP 0.00`, as today

---

## Open questions — need your answer before building

Each has a suggested answer, and the plan above follows it.

| # | Question | Suggested answer |
|---|---|---|
| Q1 | Does scanning stay in الاستلام, next to the picker? | Yes |
| Q2 | Is the price typed while receiving the **retail** price only (the one on the till and the tag)? | Yes — wholesale prices stay in the Catalogue |
| Q3 | Does each level show only the items filed **directly** in it, not everything below it? | Yes |
| Q4 | Is a price required for an item that has no price yet? | Yes |
| Q5 | Does hiding a category only hide it (and what is under it) from pickers, while its products still sell and scan? | Yes |
| Q6 | Can only admins (`MANAGE_CATALOGUE`) add, rename, move or hide categories? Today anyone signed in can add or move one. | Yes |

---

## Files

| File | Step | Change |
|---|---|---|
| `core/.../domain/repository/ICategoryRepository.kt` | 1 | `rename`, `setActive`, `children` |
| `core/.../database/dao/CatalogDaos.kt` | 1 | `getActiveChildren` |
| `core/.../data/repository/CategoryRepositoryImpl.kt` | 1 | the three new methods |
| `features/catalog/.../domain/usecase/CategoryUseCases.kt` | 1 | rename, hide/show; permission checks (Q6) |
| `features/catalog/.../di/CatalogModule.kt` | 1 | wire the new use cases |
| `features/catalog/.../presentation/model/UiModels.kt` | 1 | `name`, `nameAr`, `isActive` on the node |
| `features/catalog/.../presentation/components/CategoryTree.kt` | 1 | grey hidden rows |
| `features/catalog/.../screens/catalogbrowser/*` | 1 | two name fields, rename, hide/show |
| `core/.../database/entities/InventoryEntities.kt` | 2 | `sellPricePiastres` |
| `core/.../database/migrations/Migrations.kt` | 2 | `MIGRATION_8_9` |
| `core/.../database/KeswaDatabase.kt` | 2 | version 9 |
| `core/schemas/.../9.json` | 2 | new, generated |
| `core/.../domain/model/Inventory.kt` | 2 | `sellPrice` |
| `core/.../data/mapper/SaleMappers.kt` | 2 | map `sellPrice` |
| `core/.../domain/repository/InventoryRepositories.kt` | 2 | `ReceiptLineInput`, `putLines`, `sellPrice` on `putLine` |
| `core/.../data/repository/InventoryRepositoryImpls.kt` | 2 | `putLines`; apply prices on post |
| `core/.../di/CoreModule.kt` | 2 | pass `PriceDao` |
| `core/.../database/dao/SellableDao.kt` | 3 | `inCategory` |
| `core/.../domain/repository/SaleRepositories.kt` | 3 | `ISellableRepository.inCategory` |
| `core/.../data/repository/SellRepositoryImpls.kt` | 3 | `inCategory` |
| `features/inventory/.../domain/usecase/FindStockItemUseCase.kt` | 3 | `inCategory` |
| `features/inventory/.../domain/usecase/BrowseCategoryUseCase.kt` | 3 | new |
| `features/inventory/.../domain/usecase/AddReceiptLinesUseCase.kt` | 3 | new |
| `features/inventory/.../domain/usecase/ReceiveStockUseCases.kt` | 3 | price permission on post; facade gains `browse`, `addLines` |
| `features/inventory/.../di/InventoryModule.kt` | 3, 5 | wire the new use cases |
| `features/inventory/.../screens/receiving/*` | 4 | picker, grid, price field, print per line |
| `features/inventory/.../presentation/components/CategoryPicker.kt` | 4 | new |
| `features/inventory/.../domain/usecase/EnrichReceiptLinesUseCase.kt` | 4 | `sellPrice`, `livePrice` |
| `core/.../designsystem/Strings.kt` | 1, 4 | new strings, both languages |
| `core/.../platform/ILabelNameRenderer.kt` | 5 | new |
| `core/src/desktopMain/.../printing/DesktopLabelNameRenderer.kt` | 5 | new |
| `core/src/androidMain/.../printing/AndroidLabelNameRenderer.kt` | 5 | new |
| `composeApp/.../di/PlatformModule.desktop.kt` and `PlatformModule.android.kt` | 5 | bind the renderer |
| `features/inventory/.../domain/usecase/HangTagBuilder.kt` | 5 | new |
| `features/inventory/.../domain/usecase/PrintReceiptLineLabelsUseCase.kt` | 5 | new |
| `features/inventory/.../domain/usecase/PrintHangTagsUseCase.kt` | 5 | use the builder and the price rule |
| `features/inventory/.../domain/usecase/PrintSingleVariantLabelUseCase.kt` | 5 | use the builder |
| `features/inventory/.../domain/usecase/InventoryLabelUseCases.kt` | 5 | `printLineLabels` |
| `core/.../printing/tspl/Tspl.kt` | 5 | `testLabel` takes a name picture |
| `features/settings/.../domain/usecase/PrintTestUseCases.kt` | 5 | Arabic line on the test label |
| `features/inventory/README.md`, `features/catalog/README.md` | 6 | docs |
