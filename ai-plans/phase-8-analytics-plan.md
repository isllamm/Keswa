# Phase 8 Plan — Sales Analytics for the Admin

> **Status: 📝 DRAFT — awaiting review**
> Depends on: Phase 5 (sales exist), Phase 6 (receipts + cost model)
> Prototype: **Keswa Sales Console** — published, bilingual, themed
> Estimated: 6–9 days

## Goal

The shop owner opens Keswa and knows, in one screen, how the shop is doing — and specifically
**what to buy next**. Implemented on the desktop till against local data, so it works offline; the
same use cases feed the web back office in Phase 9.

The prototype is the spec. This plan covers what it takes to make it real.

---

## What the dashboard shows, and why these metrics

Generic POS dashboards show revenue and top sellers and stop. Those are the least actionable numbers
in clothing retail, because they say nothing about **what is left on the rail**.

| Panel | Form | Why it earns the space |
|---|---|---|
| Revenue today | Hero figure | The one number the owner opens the app for |
| Transactions · basket · units · return rate | Stat tiles + sparkline | Return rate is a first-class KPI in clothing, not a footnote |
| Revenue trend | Line, 7/30/90d | Shows the **Thursday–Saturday** Egyptian retail week, so staffing and delivery scheduling have evidence |
| **Colour performance** | Stacked column, sold vs. on hand | **The most valuable chart in the product.** It shows beige and green were over-bought while navy sold out — cash tied up in the wrong colours, which is next season's buying decision. No generic dashboard shows it |
| **Sell-through by category** | Horizontal bar vs. 70% target | The markdown trigger. Below target late in a season means discount now, not in January |
| Busy hours | Day × hour heatmap | Staffing. The only chart here that is about people, not stock |
| Top movers | Table | Past ~7 items a table beats a chart — units, revenue, and sell-through per SKU |

Colour performance and sell-through are the two panels that justify building this rather than buying
a generic dashboard. They are the questions a clothing buyer actually asks.

---

## Deliverables

### 1. Rollup projections — the real engineering work

Computing a year of analytics by scanning `stock_movement` on every dashboard open does not survive
contact with a real shop's data volume.

Same pattern as Phase 1's `stock_on_hand`: **a projection written in the same transaction as the
source event, and fully rebuildable from the ledger.**

```kotlin
@Entity(tableName = "daily_sales_summary", primaryKeys = ["date","locationId","channel"])
data class DailySalesSummaryEntity(
    val date: Long, val locationId: String, val channel: Channel,
    val revenuePiastres: Long, val cogsPiastres: Long,
    val units: Int, val transactions: Int, val returnedUnits: Int,
)

@Entity(tableName = "daily_variant_movement", primaryKeys = ["date","variantId","locationId"])
data class DailyVariantMovementEntity(
    val date: Long, val variantId: String, val locationId: String,
    val soldQty: Int, val receivedQty: Int, val returnedQty: Int,
)
```

`RebuildAnalyticsProjectionsUseCase` ships alongside, with a test asserting a rebuild from the ledger
reproduces the projections exactly — the same safety net Phase 1 established. If they ever drift,
they are recoverable rather than corrupt.

> This is the payoff of the append-only ledger. Because every sale, receipt and return is already an
> immutable row with a reason code, **sell-through is a query, not a new subsystem.**

### 2. Metric use cases — `:features:analytics`

Per ADR-021, nothing here is computed in a ViewModel.

```
GetRevenueTrendUseCase(range, channel)      → List<DailyPoint>
GetColourPerformanceUseCase(period, catId)  → List<ColourBucket>
GetSellThroughUseCase(groupBy, period)      → List<SellThroughRow>
GetBusyHoursUseCase(period)                 → HourDayMatrix
GetTopMoversUseCase(period, limit)          → List<MoverRow>
GetHeadlineKpisUseCase(period)              → HeadlineKpis
```

**Sell-through** = `soldQty / (openingStock + receivedQty)` across the period. It needs `RECEIPT`
movements, which the ledger already carries.

**Sell-through by category** rolls up the admin's own tree, so it depends on Phase 1's materialised
`path` column: selecting "T-shirts" must include everything under Round neck, V-neck and Polo. A
rollup that silently shows only directly-assigned products is the failure mode here — it under-reports
every parent category, and nobody notices until the numbers are used for a buying decision.

### 3. Charts in Compose — hand-drawn, not a library

There is no KMP charting library worth the dependency. Draw on Compose `Canvas`.

**This is the same skill as Phase 3's receipt renderer** — the `ReceiptRenderer` already draws text,
shapes and layout to a Compose canvas. The team will have done this once before reaching here.

The prototype's specs transfer directly and are not negotiable in review:

| Mark | Spec |
|---|---|
| Line | 2px, round cap/join; area fill at 10% opacity |
| Bar / column | ≤ 24px thick, 4px rounded data-end, square at baseline |
| Stacked segments | **2px gap in the surface colour** between segments — never a stroke |
| End markers | ≥ 8px diameter, 2px surface ring |
| Grid / axes | 1px solid hairline, one step off surface. **Never dashed** |
| Axis ticks | Round numbers only (0 / 20K / 40K / 60K) |

### 4. Design tokens → `:core:designsystem`

The prototype's palette is the validated reference palette from the `dataviz` skill, used unmodified:

```
series 1  #2a78d6 light / #3987e5 dark      (sold, revenue)
series 2  #eb6834 light / #d95926 dark      (on hand)
the garment's own colour is a swatch beside the axis label — never the bar fill
sequential blue ramp, 7 steps               (heatmap)
status    good #0ca30c · warning #fab219 · critical #d03b3b
```

Two rules that survive the port to Compose:
- **Text never wears the series colour.** Values and labels use ink tokens; a coloured mark beside
  them carries identity.
- **A legend is present whenever there are ≥ 2 series** (sold vs. on hand). One-series charts get none —
  the title names what is plotted.

### 5. RTL — chrome flips, charts do not

The prototype demonstrates the intended behaviour, and it is deliberate: **the page direction flips
with the language, but the plot interiors stay LTR.** Axes, numbers and time all read left-to-right
even in an Arabic UI — that is real practice in Arabic-language dashboards, not a shortcut.

What does flip: layout order, labels, legend position, table column order.

---

## Two gaps this phase exposes

### A. Roles — resolved, in Phase 4

Flagged here first, now its own phase. `phase-4-auth-plan.md` builds `ADMIN` / `SELLER` with
permissions checked in the use case layer.

This phase **consumes** two of them: `VIEW_SHOP_ANALYTICS` gates the dashboard, and
`VIEW_COST_AND_MARGIN` gates every margin and cost figure on it separately — a seller may be allowed
to see units and revenue without seeing what the shop paid.

### B. Margin is gated on the cost model

Every margin and COGS figure depends on **Phase 6a** — moving-average vs FIFO. Until that ADR is
written, `cogsPiastres` cannot be populated correctly, and margin panels must be built but left dark
rather than shown with a number nobody can defend.

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No custom report builder.** A fixed set of well-chosen panels beats a query tool the owner never
  learns. Revisit only after a real shop asks for a specific report twice.
- **No export to Excel.** Cheap to add later, and it is a Phase 9 concern once the back office exists.
- **No forecasting or reorder suggestion.** Tempting on top of this data, and wrong to build before a
  full season of real history exists to validate against.
- **No per-chart filters.** One filter row scoping the whole page — per-chart filters make two panels
  silently disagree, which destroys trust in the numbers faster than any bug.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Projection parity | Rebuild from ledger, compare against incrementally-maintained rollups |
| 2 | Sell-through correctness | Fixture: known receipts + sales + returns → hand-computed expected % |
| 3 | Category rollup | A product 3 levels deep is counted under its top-level category |
| 4 | Performance | 2 years of synthetic data (~200k movements) → dashboard opens in < 300 ms |
| 5 | Offline | Airplane mode: every panel renders from local data |
| 6 | RTL | Arabic: chrome mirrors, plot interiors do not; no clipped labels |
| 7 | Axis ticks | Assert tick values are round across several data magnitudes |
| 8 | Role gating | A `CASHIER` session cannot reach margin or whole-shop revenue |
| 9 | Empty states | A shop on day one — every panel degrades to a stated empty state, never a broken axis or NaN |

Check 4 is the one that decides whether the rollups were necessary. Check 9 is the one most likely to
be skipped and most likely to be seen — every new install hits it first.

## Definition of Done

- [ ] All seven panels render from real local data
- [ ] Rebuild-from-ledger parity test green
- [ ] Dashboard opens in < 300 ms against 2 years of data
- [ ] Works fully offline
- [ ] Arabic and English both correct
- [ ] Margin panels either correct or explicitly disabled pending Phase 6a
- [ ] Role gating enforced in the use case layer, not only hidden in UI
