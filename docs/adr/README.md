# Keswa ADRs

Numbered `KD-001…` from scratch. `kmp_cashimobile` has **two** ADR-033s, and that collision is not
worth inheriting.

These record only what Keswa does **differently** from the Cashi conventions. Everything not listed
here is inherited unchanged — see [`docs/CODE_GUIDELINES.md`](../CODE_GUIDELINES.md).

Full reasoning, with the evidence behind each: [`ai-plans/keswa-conventions-and-deviations.md`](../../ai-plans/keswa-conventions-and-deviations.md).

## Index

| ADR | Title | Status |
|---|---|---|
| [KD-001](KD-001-money-as-minor-units.md) | Money is `Money(Long)` piastres, not `String`/`BigDecimal` | Accepted |
| [KD-002](KD-002-real-migrations.md) | Real migrations; `fallbackToDestructiveMigration` is a blocker | Accepted |
| [KD-003](KD-003-injected-dispatchers.md) | Dispatchers are injected, including in ViewModels | Accepted |
| [KD-004](KD-004-targets-desktop-first.md) | Targets are `jvm("desktop")` then `androidTarget()`. No iOS | Accepted |
| [KD-005](KD-005-hardware-as-core-interfaces.md) | Printers/scanners are `:core` interfaces bound through Koin | Accepted |
| [KD-006](KD-006-desktop-secure-storage-deferred.md) | Desktop secure storage is deferred to Phase 9 | Superseded by KD-007 |
| [KD-007](KD-007-desktop-secure-storage.md) | Desktop secure storage: user profile boundary, filesystem permissions and server-side revocation | Accepted |
| [KD-008](KD-008-moving-average-cost.md) | Cost is a moving weighted average, snapshotted onto every movement | Accepted |
| [KD-009](KD-009-bounded-retry-on-sync.md) | Bounded, jittered exponential backoff retry on sync | Accepted |
| [KD-010](KD-010-sync-is-log-shipping.md) | Sync is log shipping: three row kinds, append-only outbox triggers | Accepted |

