# Phase 5 — Multi-store, Mobile, Web

> *"Store #2 opens, and the owner checks both from a phone."*

**Effort:** ~85h+ · **Calendar:** ~9 weeks @10h/wk

---

## Goal
Turn the single-store system into a multi-store one and give the owner a read-mostly view away from
the till. This is where the Phase 0 decisions (`store_id` everywhere, locations, per-store counters)
either pay off invisibly or reveal themselves as gaps.

## In scope

**Multi-store**
- Second store + its locations, users, price lists, document counter prefixes
- Stock transfers between stores: transfer document, `TRANSFER` movements through a `TRANSIT`
  location, dispatch/receive with discrepancy handling
- Consolidated reports with a store dimension and an "all stores" mode
- Per-store vs shared catalogue policy (**ASSUMPTION:** catalogue shared tenant-wide, prices per store)
- Cross-store stock visibility at the POS ("we have it at the other branch")

**Mobile (owner app)**
- `:app:android` reusing `:domain`, `:reporting`, `:feature:reports` unchanged
- Remote-only data sources (`HttpReportEngine`); no local DB in v1
- Dashboard: today's sales, cash position, low stock, top products
- Login with the same JWT auth

**Web**
- `:app:web` (Compose Wasm) or a thin server-rendered reporting portal
  — **ASSUMPTION:** decide at the start of this phase based on whether the site is for the owner
  (share the Compose code) or for customers (a separate, SEO-friendly site — do not force Compose Wasm on it)

## Explicitly out of scope
E-commerce / online ordering · customer accounts on the web · warehouse management · franchise
multi-tenancy · full accounting/GL · iOS (add when someone actually needs it — the code supports it)

## Deliverables
1. Store #2 operating with its own till and its own numbering
2. A completed transfer with a deliberate discrepancy, resolved correctly
3. Android owner app on the owner's phone showing live figures
4. Consolidated month report the owner accepts

## Exit criteria
- [ ] Store #2 ran a full trading day with no data crossing between stores incorrectly
- [ ] Transfer of 10 units where 9 arrive produces correct stock at both ends and a discrepancy record
- [ ] Consolidated sales total equals the sum of the two per-store totals, to the minor unit
- [ ] The Android app required **no changes** to `:domain` or `:reporting` — the additive-target claim, verified
- [ ] A store offline for a day does not affect the other store's operation

## Migration impact
Additive: `stock_transfer` + `stock_transfer_line`. Everything else exists because `store_id`,
`location`, and per-store counters were designed in Phase 0.

## Risks
| Risk | Mitigation |
|---|---|
| Transfers become the shop's biggest source of stock errors | Two-step dispatch/receive with an explicit discrepancy document. Never a single-step "move stock" button |
| A `store_id` filter missing from a query leaks data across stores | CI grep asserting every report `.sq` filters `tenant_id` and `store_id`; add a test with two seeded stores |
| Android app grows into a second POS | Owner app is read-mostly in this phase. A mobile POS is its own phase with its own offline story |
| Web target choice made by momentum rather than purpose | Decide owner-portal vs customer-site explicitly, in an ADR, at the start of the phase |
