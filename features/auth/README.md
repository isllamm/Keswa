# `:features:auth`

Who is at the till, what they may do, and how they prove it.

## Why sellers and admins sign in differently

A seller signs in dozens of times a shift; an admin signs in occasionally and holds real power.

| | Seller | Admin |
|---|---|---|
| Credential | 4-digit **PIN** on a keypad | Full **password** |
| Entry | One hand, no keyboard | Normal form |
| Scope | Sell, returns within policy, stock count | Everything |

Making a cashier type a strong password forty times a day guarantees it ends up on a sticky note
under the drawer — the auth model would have created the vulnerability it was meant to prevent. A
short PIN on a low-privilege role, on a physical till in a staffed shop, is the stronger design.

The PIN's shortness is covered elsewhere: the key-derivation cost makes each guess expensive, and
**lockout** makes a run of guesses impractical.

## Why this phase comes before selling

Phase 1 stamps `userId` on every stock movement, and the ledger is append-only. Ship selling first
and every sale in the shop's permanent history is attributed to a placeholder that can never be
corrected. Attribution has to be real before the first real sale.

## Decisions worth knowing

**Permission is checked in the use case, never by hiding a button.** On an offline desktop app the
user owns the machine the UI runs on, so a hidden menu item is a usability affordance, not a
security control. `AccountUseCasesTest` proves this by calling privileged use cases directly with a
seller's session and no screen involved.

**`VIEW_COST_AND_MARGIN` is separate from `VIEW_SHOP_ANALYTICS`.** A seller may see units and
revenue without learning what the shop paid. In a trade where staff move between shops on the same
street, that is the leak an owner actually cares about.

**Bad credentials never say which half was wrong.** "No such user" and "wrong PIN" return the same
answer, and an unknown username still costs a verification, so the screen cannot be used to discover
who works here.

**Lockout is persisted, not held in memory.** A lockout a restart clears is not a lockout. Five
failures lock for five minutes, doubling each further run, capped at an hour so nobody is ever
permanently shut out by someone mashing a keypad.

**The session is in memory only.** Closing the app signs everyone out — correct for a shared till,
and it means KD-006 (no secure storage on desktop) does not block this phase: with no server there
is no token, so nothing Tier-1 to store. That gap comes due in Phase 9.

## ⚠️ Q5 — offline recovery is still an open decision

Implemented here is the recommended answer: **a recovery code shown once at setup**. A shop with no
server and no email otherwise has *no* path back from a forgotten admin password, and the whole
trading history sits in one local file.

The alternatives, both still open to you:

| Option | Trade-off |
|---|---|
| **Recovery code at setup** (built) | Works offline — but gets lost exactly as often as the password |
| A second admin required | Good operational hygiene; no help to a one-person shop |
| Support-issued unlock | Needs a support process to exist |

Swapping this out touches only `BootstrapFirstAdminUseCase` and `RecoverWithCodeUseCase`.

An admin can always reset a **seller's** PIN, so sellers are never permanently locked out. A reset
forces a change at next sign-in, because the temporary one is known to whoever set it.

## Not here yet

Auto-lock on idle (needs a screen to show the lock over — lands with the sell flow), biometrics
(desktop has none, and a shared till is the wrong place for a personal credential), and any
server-side identity (Phase 9).
