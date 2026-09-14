# KD-001 — Money is `Money(Long)` piastres

## Status
Accepted — 14 Sep 2026

## Context
ADR-013 mandates `String` for display and `BigDecimal` for arithmetic. There is no `BigDecimal` in Kotlin `commonMain`, and it appears nowhere in kmp_cashimobile's production code — that project displays server-computed amounts. A till computes its own totals, offline, on every keystroke.

## Decision
A `Money` value class over `Long` minor units in `commonMain`. Lossy operations get explicit names: `percentage(basisPoints)` rounding HALF_EVEN, and `allocate(weights)` using largest-remainder so split discounts sum back to the whole. Room stores `INTEGER`.

## Consequences
Exact by construction; serves ADR-013's intent more strongly than `String` on a platform where its letter is unimplementable. `Double`/`Float` on money remains a blocker. Rejected alternative: `BigDecimal` in a `jvmCommonMain` source set — it forecloses any non-JVM target and allocates in the basket hot path.

Full reasoning: `ai-plans/keswa-conventions-and-deviations.md` § KD-001.
