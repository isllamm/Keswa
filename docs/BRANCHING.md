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

`dev` sits at the end of Phase 9's first half — the server and the sync engine. `prod` is still at
Phase 4. Nothing has been installed in a shop yet, so `prod` means "last known-good, ready to
install" rather than "running somewhere" — it will start meaning the latter the day the first till
is set up.

**Phase 5 is the last one that can be installed carelessly.** From here the database can hold a
shop's real trading history, and KD-002's migration tests stop being a discipline and start being
the only thing standing between an upgrade and a permanent loss.

Historical phase branches (`feature/phase-0-scaffold` … `feature/phase-9-sync`) point at the commit
where each phase landed. They are kept for reference and are safe to delete once you are happy the
tags are enough.

> **On the remote.** `origin` currently has only `feature/phase-1-domain-schema`, and GitHub's
> default branch still points at it. Once `prod` and `dev` are pushed, change the default to `dev`
> in the repository settings — otherwise a clone lands on a branch that stops at Phase 1.
