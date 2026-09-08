# Phase 4 — Backend & Sync

> *"The owner sees the shop's numbers from somewhere other than the till."*

**Effort:** ~90h · **Calendar:** ~9 weeks @10h/wk

---

## Goal
Stand up Ktor + PostgreSQL, upload the shop's accumulated history through the outbox that has been
filling since Phase 0, and make a second device converge with the first. Replace local PIN auth with
server-issued JWTs without touching a single call site.

## In scope

**Server**
- Ktor service, PostgreSQL schema mirrored from `docs/sql/postgres.sql`, Flyway migrations
- `server_seq BIGSERIAL` per table + `(tenant_id, table, server_seq)` indexes
- Tenant/store/device provisioning; device pairing via one-time code
- `POST /sync/push` and `POST /sync/pull` implementing `:sync:contract`
- Idempotency on `(device_id, operation_id)`; deferred FK constraints within a batch
- Auth: user login → JWT with tenant/store/role claims; refresh; device tokens
- Server-side report endpoints reusing `ReportSpec` (a subset — the ones the owner wants remotely)
- Deployment: single VM or managed container, automated backups, TLS. **ASSUMPTION:** one small
  instance is ample; this is one shop's data

**Client**
- `:sync:engine`: outbox drain, cursor pull, batching, backoff, resumability
- Conflict application per the policy table; `sync_conflict` rows and a review screen
- `JwtAuthGateway` replacing `LocalAuthGateway` — PIN remains as a *local unlock*, credentials are
  server-side
- Sync status UI: last sync, pending count, errors, manual "sync now"
- Initial upload of all historical data through the normal push path
- Offline remains the default: **every screen works with the network down**

**Verification**
- Two-device convergence test with the documented policies
- Shadow-mode run: sync enabled, second device read-only, for two weeks before trusting writes

## Explicitly out of scope
Real-time/websocket sync · a second physical store (Phase 5) · mobile apps · the website · server-side
reporting beyond a chosen subset · multi-tenant onboarding self-service · analytics

## Deliverables
1. Ktor service deployed with automated Postgres backups
2. Full shop history uploaded and verifiable against the local DB
3. Two devices in the same store converging, including after a 24h offline period
4. Owner logging in with a server account; roles enforced from JWT claims

## Exit criteria
- [ ] Local totals for a chosen month equal server totals, to the minor unit
- [ ] Device offline for 24h with 200 sales syncs cleanly with no duplicates and no lost rows
- [ ] Killing the app mid-sync leaves the outbox correct; resume completes it
- [ ] A product deleted on device A does not resurrect on device B after two sync cycles
- [ ] A stock movement created on both devices results in both being kept and levels summing correctly
- [ ] **No local schema migration was required to enable sync** — the Phase 1 claim, verified
- [ ] POS remains fully usable with the network cable unplugged, including sale completion

## Migration impact
Client: ideally none (this is the test). Realistically one migration for things the contract needed
that the schema lacked — budget for it, and treat more than two as a Phase 0 design miss worth a
retrospective note in the ADRs.

Server: greenfield.

## Risks
| Risk | Mitigation |
|---|---|
| Sync bugs corrupt live shop data | Shadow mode first; second device read-only; the local DB remains authoritative until convergence is proven. Backups before enabling |
| Initial upload of months of history is slow or fails halfway | Resumable batches; upload is just the outbox drain, already exercised. Run it overnight |
| Clock skew on the shop PC | Server stamps receipt time; client warns on >5min skew; Windows time sync enabled during Phase 0 install |
| Auth swap breaks the till at a bad moment | `Principal` is unchanged (ADR-008); PIN unlock stays local so a server outage never locks out the cashier |
| Scope explosion into a "platform" | The server's only jobs this phase are sync and auth. Reports beyond the chosen subset wait |
| Hosting/ops is new work | Budget 10h of the 90 for deployment, TLS, monitoring, and restore rehearsal |
