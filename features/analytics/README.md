# `:features:analytics`

The numbers the owner opens the app for — and specifically, **what to buy next**.

## Every figure is a query, not a subsystem

The plan proposed rollup tables maintained inside every sale, receipt and return transaction, and
made its own performance check the arbiter. That check was run:

```
all six panels, 90-day window, two years of data (6,000 sales):  18 ms
budget:                                                         300 ms
```

So the rollups were not built. This is the append-only ledger paying off: every sale, receipt and
return is already an immutable row with a reason code and a timestamp, so sell-through is a
`GROUP BY` rather than a second set of tables to keep in step — plus a rebuild path, plus a class
of drift bug that surfaces only as numbers quietly disagreeing.

`AnalyticsTest` keeps the measurement in the suite. If a shop's data ever pushes past the budget,
it fails, and the projection gets built then — against a real number rather than a guess.

## Why these panels

Generic POS dashboards show revenue and top sellers and stop. Those are the least actionable
numbers in clothing retail, because they say nothing about what is left on the rail.

**Colour performance** is the one that justifies building this rather than buying something. Sold
stacked against still-on-hand, per colour: beige and green over-bought while navy sold out is cash
tied up in the wrong colours, and that is next season's buying decision.

**Sell-through by category** is the markdown trigger. Below the 70% target late in a season means
discount now, not in January. It rolls up through the materialised `path`, so selecting T-shirts
includes everything under Round neck and V-neck — a rollup that counted only directly-assigned
products would under-report every parent, and nobody would notice until stock was bought on the
strength of it.

**Return rate** is a first-class KPI here, not a footnote. Fit is guesswork in clothing; a shop
with no returns is a shop nobody is trying things on in.

**Busy hours** is the only panel about people rather than stock, and it is what staffing and
delivery scheduling get decided on. It shows the Thursday-to-Saturday Egyptian week.

## Two permissions, not one

`VIEW_SHOP_ANALYTICS` gates the page. `VIEW_COST_AND_MARGIN` gates cost and margin **separately**
— a seller may see units and revenue without learning what the shop paid, which in a trade where
staff move between shops on the same street is the leak an owner actually cares about.

Where the second is absent the cost is **not fetched**, rather than fetched and hidden. A number
that reaches a ViewModel is a number that can reach a screen, and on an offline desktop app the
user owns the machine the UI runs on.

## Charts are drawn by hand

There is no KMP charting library worth the dependency, and this is the same skill the receipt
renderer already needed: measure, then draw on a Compose `Canvas`.

The mark specs are from the prototype and are not review-negotiable:

| Mark | Spec |
|---|---|
| Line | 2px, round cap and join; area fill at 10% |
| Column | ≤ 24px thick, rounded at the data end, square at the baseline |
| Stacked segments | a 2px gap **in the surface colour** — never a stroke |
| End markers | ≥ 8px, with a 2px surface ring |
| Grid and axes | 1px solid hairline. **Never dashed** |
| Axis ticks | round numbers only — 0 / 20K / 40K / 60K |

Three rules about colour, each of which fixes something that reads badly:

- **Series colours mean the same thing on every panel** — series 1 is sold, series 2 is on hand.
  Otherwise the legend is something the reader re-learns per chart.
- **Text never wears the series colour.** Labels and values use ink; a coloured mark beside them
  carries identity. Coloured text fails contrast at small sizes and reads as a state.
- **A garment's own colour is a swatch beside the label, never a bar's fill.** A beige bar on a
  beige-and-navy chart cannot be read, and reusing the fill for identity breaks the rule above.

`niceCeiling` snaps every axis to 1, 2, 5 or 10 times its magnitude, so the halfway tick is round
too — and `ChartTokensTest` checks that across five thousand magnitudes, because an axis topping
out at 63,482 makes the reader do arithmetic to compare two panels.

## One filter, not seven

A single period row scopes the whole page. Per-chart filters make two panels silently disagree,
which destroys trust in the numbers faster than any bug.

## Day one

Every panel degrades to a stated empty state — never a broken axis, never a NaN. `averageBasket`,
the return rate and the margin percentage are **null** rather than zero when there is nothing to
divide by: "no average basket yet" and "an average basket of zero" are different facts. Every new
install sees this screen first, so it is the state most worth getting right.

## Not here yet

No custom report builder (a fixed set of well-chosen panels beats a query tool nobody learns), no
Excel export (cheap later, and a Phase 9 concern once a back office exists), and no forecasting or
reorder suggestion — tempting on top of this data, and wrong to build before a full season of real
history exists to validate it against.

RTL is inherited from the shell: the page direction flips with the language while the plot
interiors stay left-to-right, which is real practice in Arabic dashboards rather than a shortcut.
