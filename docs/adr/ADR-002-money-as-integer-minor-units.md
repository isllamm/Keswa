# ADR-002 — Money as integer minor units + currency code

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0

## Context
This is a retail system whose totals must reconcile against a physical cash drawer, and later across
replicas that compute independently. Floating point cannot do that.

## Decision
```kotlin
data class Money(val minor: Long, val currency: CurrencyCode)
```
Stored as `..._minor INTEGER NOT NULL` + `currency_code TEXT NOT NULL`. `Long`, never `Int`.
No `Double`, `Float`, or `BigDecimal` in persistence, domain, DTOs, or report results.
Rounding is half-up, applied **once per line**; document-level discounts are allocated across lines by
**largest-remainder** so line amounts always sum exactly to the document amount.
Formatting reads `currency.minor_unit_exponent` and happens only at the rendering edge.

## Alternatives considered
| Option | Rejected because |
|---|---|
| `Double` | 0.1 + 0.2 ≠ 0.3. Totals drift, replicas disagree, and the drawer never balances. Non-negotiable. |
| `BigDecimal` | Correct, but not available in Kotlin/Native or Wasm without a third-party library, and invites unspecified scale/rounding at every operation. Blocks the iOS/Web targets this project promises. |
| Integer minor units without a currency code | Works for exactly one currency, then requires touching every monetary column. The column is 3 bytes. |
| `Int` minor units | Overflows at ~21.5M minor units. A high-inflation currency reaches that in a single wholesale invoice. |
| Storing formatted strings | Unsummable, unsortable, locale-dependent, and unusable in Excel — the exact failure `docs/reporting-and-export.md` exists to prevent. |

## Consequences
**Good:** exact arithmetic; deterministic across devices and platforms; Excel receives real numbers;
percentages are stored as basis points (also integers) so tax and discount rates are exact too.

**Costs:** every display path must format explicitly (no `toString()` shortcuts — this is a feature);
percentage discounts must decide a rounding point, and that decision is now explicit and tested;
`Money` arithmetic must reject mixed currencies at runtime.

**Invariant (tested):** for every non-credit sale,
`Σ(line_total) − document_discount + tax == Σ(payment.amount)`.
