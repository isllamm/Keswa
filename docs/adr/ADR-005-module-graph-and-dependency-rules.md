# ADR-005 — Clean Architecture module graph and enforced dependency rules

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 0

## Context
The project ships Desktop first but promises Android, iOS and Web later with no rewrite. That promise
is kept or broken by exactly two things: whether a JVM-only dependency leaks into shared code, and
whether the UI reaches directly into persistence.

## Decision
**Clean Architecture layering** — presentation → domain ← data, with repository interfaces declared in
`:domain` and implemented in `:data` — expressed as the module graph in `docs/architecture.md` §2, with
these rules **enforced by a Gradle convention plugin that fails the build** on a violating
`project(...)` edge:

- `:domain` depends only on `:core:common` + kotlinx. No SQLDelight, Compose, Ktor, POI, or `java.*`.
- `:feature:*` may **not** depend on `:data`, `:database`, `:export:xlsx`, or `:printing:escpos`.
- `:reporting` may **not** depend on `:data` — reports must be servable from the backend later.
- Nothing depends on `:app:*`. The composition root is a sink.
- JVM-only code lives in JVM-only modules (`:export:xlsx`, `:printing:escpos`), never in a shared
  module's `jvmMain`, so a future iOS target cannot even see it.

**Platform variation is expressed as interfaces + DI, not `expect/actual`,** except where the type
must be resolved at compile time (realistically only `SqlDriverFactory`).

The presentation layer inside `:feature:*` follows MVI — see
[ADR-011](ADR-011-mvi-unidirectional-presentation.md) and `docs/presentation-architecture.md`.

## Alternatives considered
| Option | Rejected because |
|---|---|
| Single module, packages only | Fastest to start, but nothing prevents a UI file importing POI. By the time the Android target exists, the violations are everywhere and the "additive" promise is already broken. |
| Convention only, no enforcement | Decays within weeks on a solo project with no reviewer. The plugin is ~40 lines. |
| `expect/actual` for every platform concern | Forces *every* target to supply an `actual`. Adding an iOS target would fail to compile over a thermal printer that iOS will never use. Interfaces + DI make new targets purely additive. |
| Feature modules only when Android arrives | Extracting features later means untangling accumulated illegal dependencies at the worst moment. Gradle modules are cheap; create them as features appear. |
| Konsist / ArchUnit for architecture tests | Good tools, but they run at test time on classes; a Gradle dependency check fails earlier, faster, and with a clearer message. Reconsider for intra-module rules. |

## Consequences
**Good:** adding Android/iOS/Web is a new `:app:*` module plus platform bindings, with zero changes to
`:domain`, `:data` queries, `:feature:*`, or `:reporting`; the Excel exporter cannot block a mobile
build; local and remote data sources are swappable because `:feature:*` only ever sees interfaces.

**Costs:** ~8 modules in Phase 0 growing to ~15, with the Gradle boilerplate that implies (mitigated by
convention plugins); occasional friction when a feature "just needs one thing" from `:data` — which is
the rule doing its job, and is resolved by adding a repository method to `:domain`.

**Failure mode to watch:** a shared module quietly gaining a `jvmMain` source set with a JVM library in
it. The rule is that JVM-only *libraries* live in JVM-only *modules*.
