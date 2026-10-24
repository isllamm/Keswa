# KD-007 — On desktop, the filesystem is the trust boundary

## Status
Accepted — 22 Sep 2026. Discharges KD-006.

## Context
KD-006 deferred this and said Phase 9 must decide it, because Phase 9 is where the first Tier 1 secret is persisted: the device's sync token. ADR-038 puts Tier 1 data behind Android Keystore or iOS Keychain, and JVM desktop has neither.

The inherited options were macOS Keychain via JNA, Windows DPAPI, libsecret, or a passphrase-derived key — three platform bindings and a fallback, for one string.

Deciding it means first being honest about what is being protected. The token file would sit in the application data directory. **Next to it sits `keswa.db`**: the shop's entire trading history, every customer, every price, every cost and margin, in plaintext SQLite. Anyone who can read the token can read that.

Q2 (non-fiscal slips, 21 Sep 2026) removed the e-seal material, so the sync token is the whole of the remaining Tier 1 set on desktop.

## Decision
**Desktop** — the token is a file in the application data directory, `0600` where the filesystem has POSIX permissions and under the user profile on Windows, which is the same protection by a different mechanism. Restricting permissions is best effort: refusing to enrol a till because its filesystem has a different permission model would be the wrong trade.

**Android** — Keystore, AES-256-GCM, ciphertext in preferences. It is there, it costs nothing, and the handheld is the device that actually gets left on a counter.

**Both** — the token is device-scoped and **server-revocable**, and that is the control doing the real work.

`ISyncTokenStore` lives in `core/platform/` and is bound per platform through Koin, per KD-005 and ADR-018. No file handle, no `KeyStore`, no `Context` crosses it.

## Consequences
Encrypting one string with an OS keystore while the database beside it is open would be security theatre: it protects the least valuable thing in the directory and lets the reader believe the problem is solved. This decision says so out loud instead.

If the threat model changes — a shared machine, a laptop that leaves the shop — the answer is encrypting the whole database (SQLCipher), not a keystore for one string. Written down so the next person to ask reaches for the right tool.

**ADR-022 (PCI-DSS) is re-examined here, as KD-006 promised, and does not apply.** It assumes a consumer phone its owner carries. A till is a machine behind a shop's door, and Q2 took card data out of scope entirely: the payment terminal is separate and Keswa prints non-fiscal slips.

Rejected: JNA bindings to three OS credential stores (three platform-specific code paths and a fallback, to protect a revocable token sitting beside a plaintext database); a passphrase-derived key (somebody must type it at every till start, which is a cash-desk usability decision made for a threat that is not the shop's).
