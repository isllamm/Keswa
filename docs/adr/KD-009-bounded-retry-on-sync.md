# KD-009 — Bounded, jittered retry on the sync path, and nowhere else

## Status
Accepted — 22 Sep 2026. Re-decides ADR-041 for Keswa.

## Context
ADR-041 bans HTTP retry. The conventions document already flags it as inherited for a reason that does not hold here: Cashi banned it purely for parity with its native app (ADR-036), which Keswa declares not applicable.

The reason retry is usually dangerous does still hold — a request that may have taken effect before the connection dropped must not be repeated. But KD-010 engineered exactly that away: every row carries a client-generated UUID and every write is an upsert, so pushing a batch twice is pushing it once.

A till on a shop's wifi has a failure profile a phone on a mobile network does not: the router is rebooted, the cable is kicked, and the device is expected to catch up by itself.

## Decision
**Retry on the sync path only.** Exponential backoff with **full jitter**, at most 5 attempts, ceiling 60 seconds.

**Nothing user-facing retries.** A person waiting at a counter wants a failure and a button, not a silent thirty-second stall. The ban stands everywhere except the background sync, which is the one place where nobody is waiting.

**A refused device does not back off — it stops.** A revoked or unknown token is not a network problem, and no amount of waiting will change it. Its outbox is left untouched, so nothing it did is lost while somebody sorts out the enrolment.

## Consequences
Full jitter rather than plain exponential, because three tills in a shop whose router died would otherwise retry in lockstep for ever and arrive together the moment it came back.

Retry is safe here *because* of KD-010, so the two decisions are not independent: anything that made a push non-idempotent would make this one wrong. `SyncMergeTest` is what guards that.

Rejected: retrying everywhere (the original reasoning for ADR-041 is wrong here, but the conclusion is still right for anything a person is waiting on); unbounded retry (a server that is not coming back gets hammered until someone notices); plain exponential without jitter (synchronised retries from every device in the shop).
