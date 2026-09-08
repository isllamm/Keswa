# Keswa — Reporting & Export

**The rule:** reports are a query layer in shared code. Excel is one renderer among several.
If a report can only be produced by the Excel exporter, the architecture has already failed.

---

## 1. The pipeline

```
ReportSpec  (what a report is: id, params, columns)          :reporting   common
     │
ReportRequest (spec id + parameter values + Principal)        :reporting   common
     │
ReportEngine.run(request): ReportResult                       interface in :reporting
     ├── SqlReportEngine       (SQLDelight, offline)          :data        common
     └── HttpReportEngine      (backend, Phase 4+)            :data        common
     │
ReportResult (columns + typed rows + totals + metadata)       :reporting   common
     │
ReportRenderer
     ├── ComposeTableRenderer  on-screen                      :feature:reports  common
     ├── XlsxRenderer          .xlsx                          :export:xlsx      JVM only
     ├── CsvRenderer           .csv                           :export:csv       common
     ├── PdfRenderer           print/A4          (Phase 3)    :export:pdf       JVM only
     └── HtmlRenderer          website           (Phase 5)    :export:html      common
```

Every renderer consumes the **same** `ReportResult`. Adding a format is a new module; it never
touches a query. Serving the same report from the backend is a different `ReportEngine`; it never
touches a renderer.

---

## 2. The model (`:reporting`, commonMain, no dependencies beyond `:core:common`)

```kotlin
data class ReportSpec(
  val id: ReportId,
  val titleKey: String,                 // i18n key, never a literal
  val params: List<ParamSpec>,
  val columns: List<ColumnSpec>,
  val defaultSort: SortSpec?,
  val supportsGrouping: Boolean,
  val requiredPermission: String,       // e.g. REPORT_VIEW_FINANCIAL
)

data class ColumnSpec(
  val key: String,
  val titleKey: String,
  val type: CellType,                   // TEXT INT QTY MONEY PERCENT DATE DATETIME BOOL
  val align: Align,                     // START END CENTER  (never LEFT/RIGHT — RTL)
  val aggregate: Aggregate?,            // SUM AVG COUNT MIN MAX NONE
  val width: Int,                       // relative hint, used by table + xlsx
  val visibleByDefault: Boolean = true,
)

sealed interface Cell {
  data class Text(val value: String) : Cell
  data class Int_(val value: Long) : Cell
  data class Qty(val value: Long) : Cell
  data class MoneyCell(val minor: Long, val currency: CurrencyCode) : Cell   // NEVER pre-formatted
  data class Percent(val basisPoints: Int) : Cell
  data class DateCell(val epochDay: Int) : Cell
  data class DateTimeCell(val epochMillis: Long) : Cell
  data object Empty : Cell
}

data class ReportResult(
  val spec: ReportSpec,
  val rows: List<Row>,                  // Row = List<Cell>, positionally matching columns
  val groups: List<GroupNode>,          // optional grouped view
  val totals: Map<String, Cell>,        // by column key
  val meta: ReportMeta,                 // generated_at, params echo, store, currency, row_count, truncated
)
```

### The single most important rule

> **`ReportResult` carries typed values, never formatted strings.**

A `MoneyCell` holds `minor: Long` + currency. Only the renderer formats. Consequences:

- Excel receives a **real numeric cell with a number format**, so the owner can sum, filter and pivot.
  A money column exported as `"١٢٬٥٠٠ ج"` text is worthless to someone who lives in Excel — and that
  owner is the entire reason Excel export is a Phase-1 requirement.
- The same report renders in Arabic on screen and in English in a CSV without re-querying.
- Totals are computed once, in `:reporting`, from typed values — the screen and the spreadsheet can
  never disagree.

### Streaming for large results
`ReportEngine` also exposes `runStreaming(request): Flow<Row>` for exports. Three years of sale lines
must not be materialized in memory to write a spreadsheet. `ComposeTableRenderer` uses the paged
variant; `XlsxRenderer` uses the stream.

---

## 3. Report catalogue

Phase in which each first ships:

| ID | Report | Key params | Phase |
|---|---|---|---|
| `SALES_SUMMARY` | Sales by day/week/month: gross, discounts, returns, net, tx count, avg basket | date range, store, channel | 1 |
| `SALES_BY_PRODUCT` | Qty and value per product/variant, with margin | date range, category, top-N | 1 |
| `SALES_BY_CATEGORY` | Roll-up by category/brand | date range | 1 |
| `SALES_BY_USER` | Per-cashier totals, discounts given, voids | date range | 1 |
| `SALES_BY_HOUR` | Hour-of-day heat table (staffing) | date range | 3 |
| `PAYMENTS_BREAKDOWN` | By method, incl. split payments | date range, shift | 1 |
| `SHIFT_RECONCILIATION` | Z-report: expected vs counted, variance, pay-ins/outs | shift | 1 |
| `RETURNS` | Returns/exchanges with reason and original document | date range | 1 |
| `STOCK_ON_HAND` | Qty + value at average cost, by location | as-of date, location, category | 0 |
| `STOCK_MOVEMENTS` | Ledger extract, filterable by reason/ref | date range, variant, reason | 0 |
| `STOCK_VALUATION` | Total inventory value + by category | as-of date | 2 |
| `LOW_STOCK` | Below reorder point, with suggested order qty | location, threshold | 2 |
| `DEAD_STOCK` | No sales in N days, value at risk | days, location | 3 |
| `PURCHASES_BY_SUPPLIER` | POs, receipts, landed cost | date range, supplier | 2 |
| `SUPPLIER_BALANCES` | A/P outstanding | as-of date | 2 |
| `CUSTOMER_BALANCES` | A/R with aging buckets (0-30/31-60/61-90/90+) | as-of date | 2 |
| `CUSTOMER_STATEMENT` | One customer's ledger, printable | customer, date range | 2 |
| `PROFIT_MARGIN` | Revenue − COGS (from cost snapshots), by product/category | date range, group-by | 2 |
| `AUDIT_TRAIL` | Filterable audit events | date range, user, entity, action | 1 |
| `PRICE_LIST_EXPORT` | Full catalogue with prices per channel | channel | 0 |

**As-of reports read the ledger, not `stock_level`.** "Stock on hand at 31 Aug" must be reconstructible
after the fact — that is a large part of why the ledger exists. `stock_level` only answers "right now".

---

## 4. Query implementation (`SqlReportEngine`)

- One `.sq` file per report in `:data`, named after the `ReportId`.
- Parameters are bound, never interpolated. Dynamic date grain is handled by separate named queries
  (`salesSummaryByDay`, `...ByMonth`), not string-built SQL.
- Every report query filters `tenant_id`, `store_id` (or an explicit "all stores" variant), and
  `deleted_at IS NULL`. A helper macro-comment in each `.sq` file makes this checkable by grep in CI.
- **Voided sales are excluded** from every financial report and included in `AUDIT_TRAIL` and a
  dedicated voids report. Getting this wrong is the classic POS reporting bug.
- Indexes are added *for* reports: `sale(tenant_id, store_id, completed_at, status)`,
  `sale_line(sale_id)`, `stock_movement(tenant_id, variant_id, occurred_at)`,
  `customer_ledger(tenant_id, customer_id, occurred_at)`.
- Each report has a fixture test asserting totals against hand-computed values. This is the cheapest
  possible defence against the owner losing trust in the numbers — which is unrecoverable.

**Performance target:** any Phase 0–3 report over 12 months of a single-store shop returns in <1s on
the shop PC. **ASSUMPTION:** ~50–300 sales/day → ~100k sale lines/year. SQLite handles this trivially
with the right indexes; the risk is unindexed `LIKE` filters, not volume.

---

## 5. Excel export (`:export:xlsx`, JVM-only)

### Interface (`:export:api`, commonMain)
```kotlin
interface ReportExporter {
  val format: ExportFormat
  suspend fun export(result: ReportResult, target: ExportTarget, options: ExportOptions): AppResult<ExportedFile>
}
data class ExportOptions(
  val locale: String,            // "ar" | "en"
  val rightToLeft: Boolean,
  val includeTotals: Boolean,
  val includeMetaSheet: Boolean,
  val digitStyle: DigitStyle,    // WESTERN | EASTERN_ARABIC
)
```

`:feature:reports` depends on `:export:api` only. The concrete `XlsxExporter` is bound in
`:app:desktop`. On a future iOS/Web build the binding is simply absent and the export button hides —
**nothing fails to compile.** That is the whole point of the split (ADR-006).

### Library: Apache POI (SXSSF for streaming)
Chosen for full style/number-format/RTL support. ~12 MB of jars, irrelevant for a bundled desktop app,
and it never reaches a mobile artifact because it lives in a JVM-only module. FastExcel rejected: no
sheet-level RTL control, weaker styling. See ADR-006.

### Arabic / RTL correctness — the rules that actually matter

| Concern | Implementation |
|---|---|
| Sheet direction | `sheet.isRightToLeft = true` when `options.rightToLeft`. Column A appears at the right; freeze panes and widths follow automatically. |
| Text encoding | POI writes UTF-8 XLSX natively. **No CP1256, no manual reshaping, no RLM injection.** Arabic shaping is the *font renderer's* job — Excel does it correctly given correct Unicode. |
| Font | Explicit workbook font with Arabic coverage ("Arial" is safe on Windows; ship the report styled with a font the shop PC actually has). Do not rely on Calibri for Arabic. |
| Numbers | Written as numeric cells with a format (`#,##0.00`), **never as text**. Numbers stay LTR inside an RTL sheet — this is correct, not a bug. |
| Money | `minor / 10^exponent` written as a numeric cell; currency in the number format string (`#,##0.00" ج"`) or a separate currency column. Never bake the symbol into a text cell. |
| Dates | Real Excel date serials with a date format, so the owner can filter by month. |
| Percent | Numeric `bp/10000` with a `0.0%` format. |
| Headers | Bold, frozen top row, autofilter enabled. The owner *will* filter. |
| Column widths | From `ColumnSpec.width`; `autoSizeColumn` is slow and misjudges Arabic. |
| Negative money | Red, parentheses-free (`-1,234.00`). Returns must be visually obvious. |
| Meta sheet | Second sheet: report name, parameters, generated-at, store, currency, app version, row count. Prevents "which export is this?" a month later. |
| Filename | `<report>_<yyyy-MM-dd>_<store>.xlsx`, ASCII-safe, Arabic title inside the file rather than in the filename. Windows + Arabic filenames + email attachments is a fight not worth having. |

### Golden-file test (write once, keeps paying)
Export a fixture report → reopen with POI → assert: Arabic strings round-trip byte-identical, money
cells are numeric with the expected format, `isRightToLeft` is true, totals row matches
`ReportResult.totals`. Roughly 60 lines, catches every regression that would embarrass you in front
of the owner.

### Export UX
- Every report screen has one **Export to Excel** button; no per-report export code.
- Default folder `C:\ProgramData\Keswa\exports\`, remembered per user, with "open folder" after export.
- Export runs off the UI thread with progress and cancel. A 200k-row export must not freeze the till.

---

## 6. Why this survives going online

| Future need | What changes | What does not |
|---|---|---|
| Backend serves reports | New `HttpReportEngine`; `ReportRequest`/`ReportResult` become the wire types | Specs, renderers, screens |
| Website shows reports | New `HtmlRenderer` in commonMain | Everything else |
| Mobile owner app | Reuses `:reporting` + `ComposeTableRenderer`; shares the Excel-free path | Everything else |
| Scheduled email reports | Server-side: `ReportEngine` + `XlsxExporter` run headless — both are already pure functions of `(request) → bytes` | Everything else |
| New report | One `ReportSpec` + one `.sq` query + i18n keys. **No renderer changes.** | — |

`ReportRequest` and `ReportResult` are designed to be JSON-serializable (`kotlinx.serialization`) from
Phase 0, even though nothing serializes them yet. That is a one-annotation cost now and the difference
between "add a server endpoint" and "redesign reporting" later.

---

## 7. Anti-patterns this design forbids

1. **Writing SQL inside the Excel exporter.** The exporter never sees a database.
2. **Formatting money in the query.** `SELECT printf('%.2f', ...)` makes the value unusable everywhere else.
3. **A report that exists only as an export.** If it isn't on screen, it isn't a report.
4. **Per-report export code.** One renderer, N specs — never N exporters.
5. **Hardcoded report titles/columns.** Everything is an i18n key; the owner reads Arabic.
6. **Excel as the reconciliation tool.** If the owner has to sum a spreadsheet to check the till,
   `SHIFT_RECONCILIATION` is incomplete.
