# ADR-006 — Reporting as a query layer; Excel as one renderer

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0

## Context
The shop owner works in Excel today, so Excel export is a Phase 1 requirement. The obvious
implementation — a set of functions that query the database and write spreadsheets — would make Excel
the reporting architecture, and every later surface (in-app screens, the website, the mobile app,
emailed reports) would either re-implement each report or export a spreadsheet nobody wanted.

## Decision
A three-part split, all in shared code:

1. **`ReportSpec`** — declares a report: id, i18n title, parameters, typed columns, required permission.
2. **`ReportEngine.run(request): ReportResult`** — an interface. `SqlReportEngine` (SQLDelight) today;
   `HttpReportEngine` (backend) later. `ReportResult` carries **typed cells** — `MoneyCell(minor, currency)`,
   `DateCell`, `Percent(basisPoints)` — never formatted strings.
3. **`ReportRenderer`** implementations consume `ReportResult`: Compose table, XLSX, CSV, PDF, HTML.

`:export:xlsx` (Apache POI, **JVM-only module**) implements the `ReportExporter` interface declared in
`:export:api`. `:feature:reports` depends only on `:export:api`, so a future iOS or Web build simply
has no binding and hides the button — nothing fails to compile.

`ReportRequest`/`ReportResult` are `@Serializable` from Phase 0, although nothing serializes them yet.

## Alternatives considered
| Option | Rejected because |
|---|---|
| Direct query → POI functions per report | Reports exist only as files; nothing can be shown on screen, on the web, or in an app without rewriting each one. This is the failure mode the requirement explicitly names. |
| Formatting money/dates inside SQL | Produces text that cannot be summed in Excel — the owner's single most important use — and cannot be re-formatted for another locale. |
| A generic "run arbitrary SQL and export" screen | Powerful, unmaintainable, unlocalisable, and a permissions hole. A report the owner cannot name is a report nobody trusts. |
| FastExcel instead of Apache POI | Smaller and faster, but no sheet-level RTL control and weaker styling/number formats. RTL is a hard requirement. |
| Generating CSV only | Loses number formats, RTL, freeze panes and autofilter — the things that make the export usable to an Excel-native owner. |
| Putting POI behind `expect/actual` in a shared module | Breaks any non-JVM target at compile time. A separate module keeps it invisible. |

## Consequences
**Good:** a new report is one `ReportSpec` + one `.sq` query + i18n keys, and it appears on screen,
in Excel, and in CSV simultaneously; serving reports from the backend is a different `ReportEngine`
with no renderer changes; the website and mobile app reuse the whole layer; totals are computed once,
so the screen and the spreadsheet can never disagree.

**Costs:** an extra indirection compared to writing a query straight into a screen (~2h of the first
report, near-zero thereafter); `ReportResult` must stay strictly typed, which requires discipline when
someone wants "just one pre-formatted column"; POI adds ~12MB to the desktop bundle, which is
irrelevant for a bundled desktop app and reaches no mobile artifact.

**Test that keeps it honest:** a golden-file test asserting Arabic round-trips, `isRightToLeft` is set,
and money cells are numeric with the expected format.
