# Keswa — Code Guidelines

> **Source of truth is `kmp_cashimobile`, not this file.**

Keswa inherits the Cashi KMP conventions wholesale. Rather than fork a 615-line document that will
drift, this points at the originals and lists only what differs.

## Read these, in this order

1. `kmp_cashimobile/docs/CODE_GUIDELINES.md` — layers, naming, MVI, error handling, the PR checklist
2. `kmp_cashimobile/.agents/rules/rules.md` — the pre-code rules digest
3. `kmp_cashimobile/.agents/context/conventions.md` — the actual code templates
4. `kmp_cashimobile/docs/adr/` — 43 ADRs
5. **`ai-plans/keswa-conventions-and-deviations.md`** — what Keswa does differently, and why

## What differs — `docs/adr/`

| ADR | Decision |
|---|---|
| KD-001 | Money is `Money(Long)` piastres, not `String`/`BigDecimal` |
| KD-002 | Real migrations; `fallbackToDestructiveMigration` is a blocker |
| KD-003 | Dispatchers are injected, including in ViewModels |
| KD-004 | Targets are `jvm("desktop")` then `androidTarget()`. No iOS |
| KD-005 | Printers/scanners are `:core` interfaces bound through Koin; protocols are pure `commonMain` |
| KD-006 | Desktop secure storage is unsolved — decided in Phase 9 |

## Declared not applicable

ADR-002 (ring fencing), ADR-035 (migration simplicity), ADR-036 (strict logic parity),
ADR-041 (no HTTP retry), ADR-033-api-envelope. All are artifacts of porting an existing app against
a specific backend. Listed so their absence reads as a decision.

## Build gates

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew allTests
```
