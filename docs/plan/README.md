# Keswa — Phase Plan Overview

Solo developer, ~10 hours/week. Every phase ends in something the shop can actually use.

| Phase | Goal | Effort | Calendar @10h/wk | Cumulative |
|---|---|---|---|---|
| [00](phase-00.md) | Skeleton + MVI foundation + catalog + stock — replaces the inventory spreadsheet | ~60h | ~6 weeks | ~6 wk |
| [01](phase-01.md) | POS, payments, returns, shifts — the shop rings up sales | ~100h | ~10 weeks | ~16 wk |
| [02](phase-02.md) | Purchasing + customer credit — full inbound and A/R | ~55h | ~6 weeks | ~22 wk |
| [03](phase-03.md) | Hardware + speed — barcode scanning, thermal receipts, auto-update | ~40h | ~4 weeks | ~26 wk |
| [04](phase-04.md) | Backend + sync — Ktor/Postgres, outbox drain, JWT auth | ~90h | ~9 weeks | ~35 wk |
| [05](phase-05.md) | Multi-store + mobile/web — store #2, transfers, owner app | ~85h | ~9 weeks | ~44 wk |

**Total to a synced multi-store system: ~430 hours ≈ 10–11 months at 10h/week.**

## Reading the estimates
Hours are *focused development hours* and already include the tests named in each phase. They do
**not** include: hardware procurement, the shop's data entry, the owner's training, or the days lost
to a Windows printer driver. Add ~20% for a first KMP desktop project.

## The scope tension, stated once
You want a cloud backend in ~3 months. Phases 00+01 alone are ~160h ≈ 16 weeks. A backend cannot
precede a usable POS without syncing an app the shop can't run on. See `docs/sync-strategy.md` §8 for
the two viable resolutions. Everything below assumes **Option 1: ship the shop first.**

## Phase ordering rationale
- **00 before 01** because the shop has no POS today. Digitising the catalogue and stock count is
  independently valuable, is the prerequisite for selling anything, and lets the owner see progress
  in week 6 rather than week 16.
- **02 after 01** because purchase orders and customer credit are worthless without a working till,
  even though you flagged them as day-one. See phase-01 "Deferred from your day-one list".
- **03 after 02** because barcode hardware and thermal printing are the highest-variance work in the
  project, and everything before them works without hardware (manual SKU entry, A4 receipts).
- **04 last before multi-store** because sync is only testable against a real dataset.
