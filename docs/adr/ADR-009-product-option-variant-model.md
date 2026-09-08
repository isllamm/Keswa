# ADR-009 — Generic option model for the size × colour variant matrix

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0

## Context
Clothing is sold as variants: a shirt exists in S/M/L × red/blue. Every sale line, stock movement,
barcode and price points at a **variant**, not a product. Some products need a third axis (length,
fit, cup size); some need only one (one-size accessories in three colours).

## Decision
A generic option model in the schema, with a deliberately narrower UI:

```
product  1─n  product_option (name, position; max 3)
                 1─n  product_option_value (value, position)
product  1─n  product_variant (sku, cost, position)
                 1─n  variant_option_value  (variant ↔ one value per option)
product_variant 1─n barcode
```

- The **schema** supports up to 3 option axes with arbitrary names.
- The **UI** in Phase 0 exposes exactly two axes labelled Size and Colour, presents a grid, generates
  the cross-product, and lets the user untick impossible combinations.
- Option names and values are localisable rows, not enums — "مقاس"/"Size" is data.
- A product with no options still gets exactly one variant, so *everything downstream references a
  variant, always*. No nullable variant, no product-or-variant branching anywhere.

## Alternatives considered
| Option | Rejected because |
|---|---|
| `size TEXT, color TEXT` columns on the variant | Fine until the first three-axis product, then it is a migration across every sale line, stock movement and barcode ever recorded — i.e. all the historical data. The two extra join tables cost a few hours now. |
| Sell the product, treat size/colour as free text on the line | Stock per size becomes impossible, which is the entire point of the system for a clothing shop. |
| A JSON `attributes` column on the variant | No referential integrity, no efficient "all red items" query, no clean way to render a size grid, and it mirrors badly to PostgreSQL reporting. |
| EAV with unlimited axes | More general than clothing needs, and combinatorially dangerous — nothing stops a 6-axis product generating 2,000 variants. The 3-axis cap is a useful constraint. |
| Variants as independent products, linked by a group id | Duplicates name, category, tax and description across every size, and every edit becomes an N-row update. |

## Consequences
**Good:** adding a third axis later is a UI change, not a data migration — the expensive half is
already right; option names are localisable and shop-specific; the uniform "everything references a
variant" rule removes an entire class of branching from the domain.

**Costs:** three tables and two joins to answer "what size is this?" (mitigated by denormalising a
display suffix onto `product_variant.name_suffix`, maintained on generation); variant generation needs
a small combinatorial UI, which is ~4h of Phase 0; the owner must think in variants during data entry,
which the grid makes natural.

**Guardrail:** cap generated variants per product (**ASSUMPTION:** 200) with a confirmation prompt.
A mis-entered option list can otherwise generate thousands of rows the owner then has to delete.
