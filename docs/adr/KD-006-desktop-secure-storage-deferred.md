# KD-006 — Desktop secure storage is deferred to Phase 9

## Status
Accepted — 14 Sep 2026. **Discharged by KD-007 on 22 Sep 2026**, when Phase 9 persisted the first sync token.

## Context
ADR-038 puts Tier 1 data behind Android Keystore or iOS Keychain. JVM desktop has neither, and there is no inheritable answer.

## Decision
No Tier 1 data is persisted before Phase 9 — true by construction, since there is no server and Phase 4 keeps sessions in memory. Phase 9 must produce KD-007 choosing among macOS Keychain via JNA, Windows DPAPI, libsecret, or a documented passphrase-derived key.

## Consequences
Phases 0–8 are unblocked. The decision cannot slip past the first persisted sync token. ADR-022 (PCI-DSS) assumes a mobile threat model and gets re-examined in the same phase.

KD-007 chose none of the four options named above. See that ADR for why the honest answer was to look at what sits in the same directory first.

Full reasoning: `ai-plans/keswa-conventions-and-deviations.md` § KD-006.
