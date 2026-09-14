# KD-002 — Real migrations; destructive fallback is a blocker

## Status
Accepted — 14 Sep 2026

## Context
kmp_cashimobile uses `fallbackToDestructiveMigration(dropAllTables = true)` at schema v108. Correct there — the server owns the truth. In Keswa the local database IS the source of truth through Phase 8.

## Decision
Every schema change ships a hand-written `Migration` with a test that seeds schema N, migrates, and asserts the data survived. `exportSchema = true`. `fallbackToDestructiveMigration` is a blocker, enforced by a CI grep gate.

## Consequences
Schema changes cost more to write and are safe to ship. ADR-025 is silent on migrations, so this strengthens it rather than contradicting it.

Full reasoning: `ai-plans/keswa-conventions-and-deviations.md` § KD-002.
