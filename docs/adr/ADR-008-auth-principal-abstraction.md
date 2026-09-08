# ADR-008 — `Principal` abstraction: local PIN now, server JWT later

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 1

## Context
Phase 1 has no server, so users are local records unlocked with a PIN. Phase 4 introduces
server-issued JWTs. The requirement is that swapping the mechanism must not change any call site —
and there will be hundreds, because every money- or stock-affecting action must record who did it.

## Decision
A single value type, produced by a single gateway:

```kotlin
data class Principal(
  val userId: String, val tenantId: String, val storeId: String,
  val deviceId: String, val roleCode: String, val permissions: Set<String>
)

interface AuthGateway {
  suspend fun authenticate(credential: Credential): AppResult<Session>
  fun current(): Principal?
  suspend fun refresh(): AppResult<Unit>
  suspend fun signOut()
}
```

- Phase 1: `LocalAuthGateway` — Argon2id PIN hash, lockout after 5 failures, roles from `app_user`.
- Phase 4: `JwtAuthGateway` — same interface, claims from the token. **PIN unlock remains** as a local
  screen-lock so a server outage can never lock the cashier out of the till.
- **Every use case takes a `Principal`.** No use case reads a global "current user".
- **Permissions are data** (`role_permission` rows and string codes), not a Kotlin `when` — so
  "cashiers may not discount over 10%" becomes a setting rather than a release.
- `UnitOfWork.transaction(principal)` stamps audit and `origin_device_id` from the `Principal`, so
  attribution cannot be forgotten.

## Alternatives considered
| Option | Rejected because |
|---|---|
| A global `CurrentUser` singleton | Untestable, invisible in signatures, and impossible to run two contexts (e.g. a background export as a different user). Also lets a use case silently forget attribution. |
| Passing `userId: String` around | Loses tenant, store, device and permissions — each of which then gets fetched ad hoc, inconsistently, at call sites. |
| Building JWT auth now | There is no server for months. Unused auth code rots and constrains the design toward a protocol not yet chosen. |
| Deferring the abstraction until Phase 4 | The refactor touches every use case, every repository call and every audit write, at the exact moment sync is also being introduced. The abstraction costs ~2h now. |
| Enum-based roles with hardcoded checks | Every permission tweak becomes a code change and a shop visit. Codes-as-data is barely more work. |
| OS-level / Windows account auth | Shop staff share a machine and switch at the till many times a day; a 4-digit PIN is the right ergonomics. |

## Consequences
**Good:** the Phase 4 auth swap is a DI binding change; permissions are tunable without a release;
audit attribution is structurally guaranteed; the same `Principal` shape works for the mobile app and
for server-side report generation.

**Costs:** an extra parameter on every use case (which is also self-documenting); permission checks
must be written explicitly rather than implied; PIN handling needs real care — Argon2id, salt,
lockout, and never logging the PIN.

**Explicit non-goal:** Phase 1 offers **no protection against someone with physical access to the
machine and its files**. The DB is not encrypted. The threat model is casual staff misuse and
attribution, not a determined attacker. Say this out loud to the owner rather than implying otherwise.
