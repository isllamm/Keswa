# Phase 1 Plan — Domain Model & Database Schema

> **Status: 📝 DRAFT — awaiting review**
> Depends on: Phase 0, `keswa-conventions-and-deviations.md`
> Not blocked by Q1 — see "Q1 impact" below.
> Estimated: 4–6 days. **The highest-leverage phase in the project.**

## Goal

The complete persistence and domain foundation: `Money`, the product/variant model, the stock
ledger, and tested migration infrastructure. No UI. Everything verified by tests.

Get this wrong and every later phase inherits the mistake. Get it right and Phases 2–8 are mostly
assembly.

---

## Structural decision: where shared domain lives

`Product`, `Variant` and `StockMovement` are needed by `sell`, `inventory`, `purchasing` and
`reports`. Cashi forbids `features:A → features:B`, so they cannot live in a feature module. They
go in `:core` — the same place `ServiceEntity`/`ServiceDao` live in Cashi.

```
core/src/commonMain/kotlin/com/alsoug/keswa/core/
├── domain/
│   ├── money/          Money, Rounding, allocation        ← KD-001
│   ├── model/          Product, Variant, StockMovement…   ← pure, no framework imports
│   └── repository/     IProductRepository, IStockRepository…
├── data/
│   ├── repository/     ProductRepositoryImpl…
│   └── mapper/         Entity.toDomain(), Domain.toEntity()
└── database/
    ├── KeswaDatabase.kt
    ├── entities/
    ├── dao/
    ├── converters/
    └── migrations/
```

Cashi's "`core` = no business logic" rule is preserved: `:core` holds models, repository
interfaces and persistence. **Use cases stay in features.**

---

## Deliverables

### 1. `Money` — KD-001

### [NEW] `core/domain/money/Money.kt`

```kotlin
@JvmInline
value class Money private constructor(val piastres: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(piastres + other.piastres)
    operator fun minus(other: Money) = Money(piastres - other.piastres)
    operator fun times(quantity: Int) = Money(piastres * quantity)

    companion object {
        val ZERO = Money(0)
        fun ofPiastres(value: Long) = Money(value)
        fun parse(text: String): Money?   // exact decimal parse — never via Double
    }
}
```

### [NEW] `core/domain/money/MoneyAllocation.kt`

The only lossy operations, given explicit names so they can never happen by accident:

```kotlin
/** Rounds HALF_EVEN — parity with the BigDecimal semantics SubAccountUi established. */
fun Money.percentage(basisPoints: Int): Money

/**
 * Splits [this] across [weights] so the parts sum EXACTLY back to [this].
 * Largest-remainder method — the piastre that rounding would lose goes to the
 * largest remainder rather than vanishing.
 */
fun Money.allocate(weights: List<Int>): List<Money>
```

`allocate` matters more than it looks: an order-level discount spread across lines must reconcile
to the penny, or the receipt total won't match the sum of its lines — the classic POS bug.

**Tests (required):** `0.1 + 0.2` exactness, HALF_EVEN at exact halves, `allocate` summing back
to the original across adversarial weights, negative money (returns), and the max-value boundary.

### 2. Catalogue entities

### [NEW] `core/database/entities/ProductEntity.kt`, `VariantEntity.kt`, …

```kotlin
@Entity(tableName = "product")
data class ProductEntity(
    @PrimaryKey val id: String,          // client-generated UUID — see KD-002 note below
    val name: String,
    val nameAr: String,
    val brandId: String?,
    val categoryId: String,          // FK to the admin's tree
    val supplierId: String?,
    val season: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "variant",
    foreignKeys = [ForeignKey(ProductEntity::class, ["id"], ["productId"], onDelete = RESTRICT)],
    indices = [Index("productId"), Index("colourId"), Index(value = ["sku"], unique = true)],
)
data class VariantEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val colourId: String,                // the ONLY variant axis
    val sku: String,
    val costPiastres: Long,              // Money via MoneyConverter
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
```

**`onDelete = RESTRICT`, never `CASCADE`.** A product with stock history must not be deletable —
deactivate it instead. CASCADE here would silently destroy ledger rows.

### Categories — an admin-defined tree

**Changed 14 Sep 2026 — sizes removed.** Variants carry **colour only**. The hierarchy that
matters is the category tree, and the admin owns it end to end: "T-shirts" with "Round neck",
"V-neck" and "Polo" beneath it, nested as deep as they like.

```kotlin
@Entity(
    tableName = "category",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["parentId"], onDelete = RESTRICT)],
    indices = [Index("parentId"), Index("path")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val parentId: String?,          // null = a main category
    val name: String,
    val nameAr: String,
    val path: String,               // materialised: "/tshirts/roundneck/"
    val depth: Int,
    val sortOrder: Int,
    val isActive: Boolean,
)
```

**`path` is the load-bearing column.** "Everything under T-shirts, including sub-categories" is the
single most common query in the app — it drives the catalogue tree, the category filter, and every
analytics rollup. As a materialised path it is `WHERE path LIKE '/tshirts/%'`, which SQLite answers
from the index. The alternative is a recursive CTE on every read, which is both slower and far
easier to get subtly wrong.

`path` and `depth` are maintained on write, in the same transaction as the insert or move. Moving a
sub-tree rewrites the paths of its descendants — the one genuinely fiddly operation here, and it
gets its own test.

### Colours — a shared, admin-managed list

```kotlin
@Entity(tableName = "colour")
data class ColourEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val hex: String,                // for the swatch; never the only identifier
    val sortOrder: Int,
    val isActive: Boolean,
)
```

One list shared by every product, managed by the admin exactly like categories — so "Navy" means
the same thing shop-wide and analytics can group by it.

`VariantEntity` therefore carries a plain `colourId`. No attribute-axis machinery.

> **On reversibility:** a generic axis model would make adding a second dimension later a data
> change rather than a migration. It was cut deliberately — it is unused complexity on every query
> today. If a second axis is ever wanted, the migration is contained, because the ledger is keyed on
> `variantId` regardless of what distinguishes one variant from another.

### [NEW] `VariantBarcodeEntity`

```kotlin
@Entity(tableName = "variant_barcode", indices = [Index("variantId")])
data class VariantBarcodeEntity(
    @PrimaryKey val barcode: String,     // barcode IS the natural key — scan → O(1) lookup
    val variantId: String,
    val isPrimary: Boolean,
    val source: BarcodeSource,           // OWN | SUPPLIER
)
```

Its own table, never a column on `variant` — a garment routinely carries the supplier's EAN-13 and
your own printed code, and both must scan to the same variant.

### 3. The stock ledger — the core of the system

### [NEW] `core/database/entities/StockMovementEntity.kt`

```kotlin
@Entity(
    tableName = "stock_movement",
    indices = [Index("variantId", "locationId"), Index("occurredAt"), Index("refType", "refId")],
)
data class StockMovementEntity(
    @PrimaryKey val id: String,          // client-generated UUID → idempotent sync (cashi_pax defect F2)
    val variantId: String,
    val locationId: String,
    val quantity: Int,                   // SIGNED: -2 sold, +10 received
    val reason: MovementReason,
    val refType: String?,                // "SALE", "PURCHASE_ORDER", "COUNT"
    val refId: String?,
    val occurredAt: Long,
    val userId: String,
)

enum class MovementReason {
    SALE, RETURN, RECEIPT, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, COUNT, DAMAGE
}
```

**Append-only. There is no `update` or `delete` DAO method — deliberately.** A mistake is corrected
by writing a compensating `ADJUSTMENT`, never by editing history. This is what makes the audit trail
trustworthy and offline merges conflict-free.

### [NEW] `StockOnHandEntity` — the projection

```kotlin
@Entity(tableName = "stock_on_hand", primaryKeys = ["variantId", "locationId"])
data class StockOnHandEntity(variantId, locationId, quantity, lastMovementAt)
```

A cache of `SUM(quantity)`, kept current in the same transaction as the movement write, and
**fully rebuildable**:

```sql
INSERT OR REPLACE INTO stock_on_hand
SELECT variantId, locationId, SUM(quantity), MAX(occurredAt)
FROM stock_movement GROUP BY variantId, locationId;
```

A `RebuildStockOnHandUseCase` ships in this phase, with a test asserting that rebuilding from the
ledger reproduces the projection exactly. That test is the safety net for every later phase — if
the projection ever drifts, it is recoverable rather than corrupt.

### 4. Pricing

```kotlin
@Entity(tableName = "price_list")
data class PriceListEntity(id, name, type /* RETAIL | WHOLESALE */, isDefault)

@Entity(tableName = "price", indices = [Index("variantId", "priceListId")])
data class PriceEntity(id, priceListId, variantId, pricePiastres, validFrom, validTo)
```

Included now even though wholesale is Phase 7 (**Q1**). A single `RETAIL` list is seeded at
install; adding a `WHOLESALE` list later is then a data change, not a migration. The cost of the
extra table now is near zero; the cost of retrofitting price lists into a live schema is not.

### 5. Migration infrastructure — KD-002

### [NEW] `core/database/KeswaDatabase.kt`

```kotlin
@Database(entities = [...], version = 1, exportSchema = true)
@TypeConverters(MoneyConverter::class, DateTimeConverters::class)
@ConstructedBy(KeswaDatabaseConstructor::class)
abstract class KeswaDatabase : RoomDatabase() { /* DAOs */ }

expect object KeswaDatabaseConstructor : RoomDatabaseConstructor<KeswaDatabase>
```

### [NEW] `core/database/KeswaDatabaseFactory.kt`

```kotlin
fun getKeswaDatabase(builder: RoomDatabase.Builder<KeswaDatabase>): KeswaDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(dispatchers.io)
        .addMigrations(*ALL_MIGRATIONS)        // KD-002 — NEVER fallbackToDestructiveMigration
        .build()
```

Mirrors Cashi's `getRoomDatabase(builder)` bridge, with the destructive-migration line replaced.

### [NEW] `core/database/DatabaseBuilder.desktop.kt`

Per **KD-004**, this follows Cashi's actual pattern — **not** `expect`/`actual`, but a same-named
function per source set, resolved because only one compiles per target. Cashi's Android and iOS
signatures deliberately differ (Android takes a `Context`); desktop takes neither:

```kotlin
fun getDatabaseBuilder(): RoomDatabase.Builder<KeswaDatabase> {
    val dir = appDataDirectory()          // %APPDATA% / ~/Library/Application Support / XDG
    return Room.databaseBuilder<KeswaDatabase>(name = dir.resolve("keswa.db").absolutePath)
}
```

**The path strategy is a real decision, not boilerplate.** `Context.getDatabasePath()` has no
desktop analogue, and putting the shop's only copy of its sales ledger somewhere the OS may clear —
or somewhere that does not survive an app upgrade — is a data-loss bug. Per-OS convention,
documented, with the resolved path logged at startup so support can find it.

### [MODIFY] `core/build.gradle.kts` — per-target KSP

Cashi configures KSP per target rather than with a blanket `ksp(...)`. Keswa's equivalent:

```kotlin
dependencies {
    add("kspDesktop", libs.androidx.room.compiler)   // "kspAndroid" added in Phase 6
}
room { schemaDirectory("$projectDir/schemas") }
```

> The configuration name follows the **target** name, so `jvm("desktop")` gives `kspDesktop`. If the
> target is declared as a bare `jvm()` it is `kspJvm` instead — a five-minute trap worth naming here.

### [NEW] `core/src/commonTest/.../migrations/MigrationTestHarness.kt`

The pattern every future migration test follows: open schema N, insert a representative row,
migrate to N+1, assert the row survived with correct values. Built now while there is exactly one
schema version and the harness is trivial to write.

---

## Q1 impact

**This phase is not blocked.** A desktop app sold to many shops is *multi-instance* — each shop
gets its own local database — so tenancy is a Phase 9 server concern, not a schema one. The
`price_list` table already accommodates wholesale. If Q1 comes back "SaaS", Phase 1 is unaffected.

---

## Best-Practice Notes

**Deliberately NOT in this phase:**

- **No use cases beyond `RebuildStockOnHandUseCase`.** Use cases belong to features; writing them
  before there is a feature to own them means guessing at the boundary.
- **No sync columns** (`syncedAt`, `dirty`). Phase 9 adds them. Guessing the sync protocol's needs
  six phases early reliably produces the wrong columns.
- **No soft-delete on ledger rows.** Append-only already gives history.

**Flagged for a separate decision, not assumed:**

- `Int` for quantity assumes whole garments. If they ever sell fabric by the metre, this needs to
  become a scaled integer. Worth 30 seconds of confirmation now — it is a migration later.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Money exactness | `./gradlew :core:allTests` — parse/format round-trip, HALF_EVEN halves, `allocate` sums back |
| 2 | Schema compiles + exports | `./gradlew :core:kspKotlinDesktop`; assert `core/schemas/…/1.json` committed |
| 3 | Ledger → projection parity | Property test: N random movements, rebuild, compare against incremental value |
| 4 | Append-only enforced | Test asserting `StockMovementDao` exposes no update/delete |
| 5 | FK integrity | Deleting a product with variants fails with RESTRICT |
| 6 | Barcode lookup | Two barcodes (own + supplier) resolve to the same variant |
| 8 | Category tree | Nesting 3 deep, then moving a sub-tree, rewrites every descendant `path` |
| 7 | Migration harness | A throwaway v1→v2 migration proves the harness works, then is reverted |

Check 3 is the one that matters most. If the ledger and the projection can disagree, stock numbers
are untrustworthy and every report built on them is wrong.

## Definition of Done

- [ ] `Money` covered, including `allocate` reconciliation
- [ ] All entities, DAOs, converters compile; schema v1 exported and committed
- [ ] Rebuild-from-ledger test green
- [ ] `StockMovementDao` has no mutation methods
- [ ] Migration harness proven on a throwaway migration
- [ ] `fallbackToDestructiveMigration` appears nowhere — CI grep gate added
- [ ] `:core` has zero Compose/Ktor imports in `domain/`
