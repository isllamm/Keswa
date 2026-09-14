# KD-004 — Targets are desktop then Android; no iOS

## Status
Accepted — 14 Sep 2026

## Context
The till drives ESC/POS printers, HID scanners and, in Phase 10, a PKCS#11 signing token. iOS blocks wired and Bluetooth-Classic peripherals behind MFi and has no PKCS#11 path.

## Decision
`jvm("desktop")` from Phase 0; `androidTarget()` from Phase 6; no iOS. Platform implementations are named `Desktop*`, extending the `Android*`/`Ios*` convention. Room KSP is configured per target (`kspDesktop`).

## Consequences
`commonMain` must avoid `java.*` as well as `android.*` — `java.*` would compile on desktop and break Android subtly. A CI grep gate enforces it.

Full reasoning: `ai-plans/keswa-conventions-and-deviations.md` § KD-004.
