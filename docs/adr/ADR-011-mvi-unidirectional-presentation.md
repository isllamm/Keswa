# ADR-011 — MVI with a hand-rolled store for presentation

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0

## Context
The presentation layer must survive four surfaces (Desktop now; Android, iOS, Web later) and a
10-month solo build. The POS screen is the hardest case: high-frequency input (barcode scans,
quantity edits), several concurrent draft sales, and side effects that must happen exactly once —
printing a receipt and kicking the cash drawer. A receipt printed twice because a state holder
re-ran is a real, visible defect.

## Decision
**MVI with unidirectional data flow**, on a thin base class we own (~120 lines in `:core:ui/mvi`).

Every screen declares one `Contract` with three types — `State` (immutable, complete), `Intent` (user
actions plus `Internal` results), `Effect` (one-shot) — and one `Store`:

- `reduce(state, intent): State` is **pure and synchronous**. No I/O, no clock, no coroutines.
- `handle(intent, state)` performs async work by calling **domain use cases**, and feeds results back
  as `Intent.Internal.*`. A use case result never writes state directly.
- Effects go through a `Channel` (consumed exactly once), never a `SharedFlow` with replay.
- Composables are stateless: `Screen(state, onIntent)`. Only a thin `Route` owns a store and collects effects.

Hosted on the **`androidx.lifecycle.ViewModel` KMP artifact** for `viewModelScope` across
jvm/android/ios/wasm.

Layering is **Clean Architecture**: presentation → domain ← data, with repository interfaces in
`:domain` and implementations in `:data` ([ADR-005](ADR-005-module-graph-and-dependency-rules.md)).

## Alternatives considered
| Option | Rejected because |
|---|---|
| **MVVM with mutable observable state** (`var` in a state holder, or several `MutableStateFlow`s) | Multiple independently-mutable fields make illegal intermediate states reachable — a cart total that briefly disagrees with its lines. Transitions are scattered across methods rather than expressed as one function, so they can't be tested without the coroutine machinery. |
| **MVP** | Interface-per-view boilerplate, imperative view updates, and no natural fit with Compose's declarative recomposition. |
| **Orbit MVI** (library) | Genuinely good and KMP-ready; the closest call. Rejected because the machinery we actually need is ~120 lines, and over 10 months a third-party presentation framework is a bigger upgrade/compat liability than code we own. **Revisit if our base grows past ~200 lines** — that would mean we were rebuilding Orbit badly. |
| **Ballast** | Same reasoning as Orbit, with a larger API surface than this project uses. |
| **Circuit (Slack)** | Compelling model, but its presenter API is built around the Compose runtime, which couples presentation logic to Compose and makes non-UI reuse (server-side report generation, tests) awkward. Android-centric maturity. |
| **Decompose** | Solves component lifecycle and navigation, which is more than we need at ~9 screens, and would decide navigation for us in Phase 0 rather than at Android in Phase 5. |
| **Effects as `SharedFlow(replay = 1)`** | Replays on re-collection — the receipt prints twice. A `Channel` is the correct primitive for exactly-once. |
| **Errors and loading as Effects** | They vanish on recomposition, can't be asserted in a reducer test, and leave the screen in a state that doesn't describe itself. Both are `State`. |
| **Strict Clean with a UI model per screen** | Consistent, but at 10h/week it is hundreds of mapper files that only copy fields. We map DB↔domain and wire↔domain always; domain↔UI only where a screen needs computed fields. Marked as an assumption to flip if wanted. |
| **Use case for every repository read** | A class that forwards one call adds a file and a test double and no rule. Mutations and rule-bearing reads get use cases; plain observational reads may use the repository interface directly. |

## Consequences
**Good:** every state transition is a pure function testable with no mocks, no dispatchers and no
Compose — which is the practical reason this is worth the ceremony on a solo project; illegal states
are unrepresentable because one immutable object changes atomically; exactly-once effects make the
receipt/drawer problem structural rather than vigilance-based; stores and contracts are commonMain, so
the Android app in Phase 5 reuses them verbatim; a new field is one line in `State`, one in `reduce`,
one in the composable.

**Costs:** more ceremony than MVVM for genuinely trivial screens (Settings, About) — accepted for
uniformity, since a mixed codebase is worse than a slightly verbose one; a large `State` re-emits on
every change, so the POS screen needs list keys, stable types and flow slicing (budgeted in Phase 3,
with an explicit latency target); the `Intent.Internal` split is unfamiliar at first and must be
documented — it is, in `docs/presentation-architecture.md`.

**Guardrail:** if `reduce` starts containing business rules — pricing, return limits, credit checks —
the layering has been violated. Those belong in `:domain` use cases. Reduce arranges state; the domain
decides what is legal.
