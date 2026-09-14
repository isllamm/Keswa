# Phase 2 Plan — Catalogue Feature

> **Status: 📝 DRAFT — awaiting review**
> Depends on: Phase 1
> Estimated: 5–7 days

## Goal

`:features:catalog` — the admin builds their own category tree, creates products under it, and gives
each product its colours; every colour becomes a SKU with its own barcode. The first real feature, so
it doubles as **the reference implementation** every later feature is copied from. Extra care on the
pattern is repaid six times over.

---

## Deliverables

### [NEW] `features/catalog/` — standard Cashi layout

```
features/catalog/
├── README.md                          ← required by CODE_GUIDELINES
├── data/
│   ├── model/                         mappers only — no DTOs yet (offline-only until Phase 9)
│   └── repository/                    (repository impls are in :core — see Phase 1)
├── domain/
│   └── usecase/
│       ├── CreateProductUseCase.kt
│       ├── ManageCategoryUseCase.kt
│       ├── MoveCategoryUseCase.kt
│       ├── AddColourToProductUseCase.kt
│       ├── AssignBarcodeUseCase.kt
│       ├── GenerateInternalBarcodeUseCase.kt
│       └── SearchCatalogUseCase.kt
├── presentation/
│   ├── model/                         ProductUiModel, CategoryNodeUiModel
│   ├── components/                    CategoryTree.kt · ColourQuantityList.kt
│   └── screens/
│       ├── catalogbrowser/           CatalogBrowserScreen/ViewModel/UDF   (tree + list)
│       └── producteditor/             ProductEditorScreen/ViewModel/UDF    (colours)
└── di/CatalogModule.kt
```

### The category tree — the feature that defines the product

**Changed 14 Sep 2026 — sizes removed, category tree added.**

Variants are colour-only, so the interesting structure moved from a size × colour grid to a tree
the admin builds themselves.

```kotlin
class MoveCategoryUseCase(private val repo: ICategoryRepository) {
    suspend operator fun invoke(categoryId: String, newParentId: String?): Result<Unit>
}
```

Rules that must hold:

- **A category cannot be moved under its own descendant.** Cheap to check (`newParent.path` must
  not start with `category.path`), and without it the tree becomes a cycle that hangs every
  recursive read.
- **Moving a sub-tree rewrites every descendant's `path`**, in one transaction. This is the only
  genuinely tricky write in the feature and it gets its own test.
- **Deleting a category with products is refused** — deactivate instead, same discipline as variants
  with stock.
- **Depth is not capped** in the schema. The UI renders three levels comfortably and indents beyond
  that; an arbitrary limit would be a constraint invented for no reason.

### Colour quantity list — the component reused three times

Phases 3, 5 and 7 all need "a row per colour, with a number against it" — receiving, counting and
reporting. Build it standalone and slot-based now (ADR-011), as `ColourQuantityList`:

```kotlin
@Composable
fun ColourQuantityList(
    colours: List<Colour>,
    trailing: @Composable (Colour) -> Unit,   // qty field, count input, or a read-only figure
    modifier: Modifier = Modifier,
)
```

Building it inline in the editor and extracting later is how it ends up coupled to catalogue state.

### Barcode strategy

Two paths, both needed:

| Source | Flow |
|---|---|
| `SUPPLIER` | Scan the garment's existing EAN-13 → attach to variant |
| `OWN` | Generate an internal code → print in Phase 6 |

`GenerateInternalBarcodeUseCase` uses **EAN-13 with a private prefix** (the `20`–`29` in-store
range) and a correct check digit, rather than an arbitrary string — so cheap scanners read it
without configuration and it can never collide with a real GTIN.

Check-digit computation is pure and gets unit tests with known-good vectors.

### RTL and Arabic

`composeResources/values-ar/` from the first screen, mirroring Cashi's existing setup (they already
ship `values-ar` and `drawable-ar`). Every entity carries `name` + `nameAr` from Phase 1, so the UI
has something to render.

**Test both directions from day one.** Retrofitting RTL is a known trap — mirrored padding, icon
direction and number formatting all surface late and cost more than they should.

---

## Q1 dependency

This is the first phase Q1 touches, and only lightly: if Keswa is **multi-tenant SaaS**, product
identity must be tenant-scoped at the server in Phase 9. Local schema is unaffected (each shop has
its own database). **Phase 2 can proceed before Q1 is answered.**

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No image handling.** Cashi uses Coil; Keswa will need product photos eventually, but they are
  irrelevant to selling and would pull in storage and cache decisions that belong after Phase 5.
- **No categories/brands management UI.** Seeded as reference data; a management screen is a Phase 8
  nicety.
- **No import from spreadsheet.** It will be asked for — it is a much better Phase 6 task, once
  receiving exists and the import can reuse validated paths.

**Pattern discipline — this feature is the template:**

- Exactly three flows per ViewModel. No dialog booleans in `UiState`.
- Every use case `operator fun invoke()`, single responsibility.
- Fakes, not mocking libraries.
- `README.md` with a Mermaid flow diagram, matching `features/auth/README.md`.

A review specifically against `CODE_GUIDELINES.md`'s PR checklist is worth doing on this feature
before Phase 3 starts — every later feature will inherit whatever shape it lands in.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Tree integrity | Moving a category under its own descendant is refused; a legal move rewrites all descendant paths |
| 2 | Stock-bearing variant protected | Removing a colour that has stock → refused, no rows deleted |
| 3 | Category rollup | Products under a 3-deep sub-category appear when the top category is selected |
| 4 | EAN-13 check digit | Unit tests against known-good vectors |
| 5 | Barcode uniqueness | Assigning an in-use barcode is rejected |
| 6 | RTL | Screenshot the editor in `ar` and `en`; assert mirrored layout |
| 7 | MVI conformance | ViewModel tests for state transitions and effect emissions |
| 8 | Guidelines | Manual pass against the PR checklist |

## Definition of Done

- [ ] Build a 3-level category tree, move a sub-tree, and see paths stay correct
- [ ] Add a product with 5 colours → 5 variants with distinct SKUs and barcodes
- [ ] Both supplier and internal barcodes resolve to the right variant
- [ ] `CategoryTree` and `ColourQuantityList` are standalone and slot-based
- [ ] Arabic and English both render correctly
- [ ] `features/catalog/README.md` exists
- [ ] PR checklist passes
