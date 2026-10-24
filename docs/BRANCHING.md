# Branching

Three levels, and one rule each.

```
prod                      what is installed in the shop
└── dev                   integration — everything that has landed and is green
    ├── feature/phase-9-sync
    ├── feature/phase-9-backoffice
    └── fix/<short-name>
```

| Branch | Rule |
|---|---|
| **`prod`** | Only ever receives a merge from `dev`, and only when a build is actually going onto the shop's machine. Never commit to it directly. |
| **`dev`** | The default branch. Always builds, always green — `./gradlew allTests` and `./scripts/check-gates.sh` both pass on every commit. |
| **`feature/*`** | One per phase, branched from `dev`, merged back when its Definition of Done is met. |

## Starting a phase

```bash
git checkout dev
git pull
git checkout -b feature/phase-8-analytics
```

> **Phases are not always in order.** Phase 7 is gated on Q1, so Phase 8 was taken first — the
> outline sanctions the swap. Tags record what landed, and in what order it actually landed.

## Finishing one

The Definition of Done is in that phase's plan under `ai-plans/`. Before merging:

```bash
./gradlew allTests
./scripts/check-gates.sh
```

Then merge to `dev` and tag the phase:

```bash
git checkout dev
git merge --no-ff feature/phase-8-analytics
git tag -a phase-8-analytics -m "Phase 8 — analytics"
```

`--no-ff` keeps the phase visible as a unit in the history rather than flattening it into a run of
unrelated-looking commits.

## Releasing

```bash
git checkout prod
git merge --no-ff dev
git tag -a v0.1.0 -m "First install — Downtown branch"
```

A release tag is what you check out to reproduce exactly what a shop is running when they phone
about a bug. That matters more here than in most projects: until a shop enrols a second device its
database is the only copy of its history, so knowing precisely which schema version they are on is
the difference between a five-minute fix and a guess.

## Tags

Phases are tagged `phase-0` … `phase-N`, releases `vX.Y.Z`. A branch is for work in progress; a tag
is the better marker for something finished, because it does not move.

## Current state

`dev` and `prod` are both at **v0.4.2**, with all nine core phases complete:
- Phase 0: Scaffolding, targets, DI, and architectural gates
- Phase 1: Domain models, minor-unit `Money`, category tree, append-only stock ledger, and tested Room migrations
- Phase 2: Catalogue, variant management, and colour tracking
- Phase 3: Hardware abstraction (ESC/POS receipt printing, TSPL label printing, barcode scanning, and TCP transports)
- Phase 4: Users, roles (Admin/Seller), PIN/password auth, lockout policy, and permission gates
- Phase 5: Sell flow, basket calculation, split tender, shift management, held sales, and receipt generation
- Phase 6: Receiving, stock counts, adjustments, Android target support, and native spreadsheet catalogue importer
- Phase 7: Wholesale (B2B), customer credit limits, statements, invoices, and assortment pack explosion
- Phase 8: Returns & exchanges (with receipt QR lookup) and real-time business analytics
- Phase 9: Sync engine, multi-device log-shipping server, till device enrolment, and Web Back Office dashboard

Historical phase branches point at the commit where each phase landed. Release tags `v0.1.0` … `v0.4.4` mark production-ready checkpoints.
