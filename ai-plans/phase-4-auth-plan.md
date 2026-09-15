# Phase 4 Plan — Users, Roles & Login

> **Status: ✅ BUILT — Q5 answered provisionally**
> Q5 (offline admin recovery) is implemented as **a recovery code shown once at setup**, the
> recommended option. Still open to change: it touches only `BootstrapFirstAdminUseCase` and
> `RecoverWithCodeUseCase`.
> Depends on: Phase 1 (schema), Phase 0 (DI, dispatchers)
> **New phase, inserted 14 Sep 2026.** Phases 4–9 shifted to 5–10.
> Estimated: 5–7 days

## Goal

Admins and sellers log in; every sale, refund and stock movement is attributed to a real person; and
a cashier cannot see what a cashier should not see.

## Why this comes before the sell flow

Phase 1 already stamps `userId` on every `stock_movement`, but nothing fills it. If the sell flow
ships first, every sale in the shop's permanent, append-only ledger is attributed to a placeholder —
and because the ledger is immutable by design, **that is not repairable later**. Attribution has to
be real before the first real sale.

---

## The thing most POS auth gets wrong

A cashier signs in and out dozens of times a shift. Two people share one till. Someone steps away to
the stockroom mid-sale.

**So sellers and admins do not get the same login.**

| | Seller | Admin |
|---|---|---|
| Credential | **4–6 digit PIN** | Full password |
| Frequency | Dozens of times a day | Occasionally |
| Entry | On-screen keypad, one hand, no keyboard | Normal form |
| Scope | Sell, returns within policy, stock count | Everything |

Making a cashier type `Tr0ub4dor&3` forty times a day guarantees the password ends up on a sticky
note under the drawer — the auth model itself has created the vulnerability. A PIN scoped to a
low-privilege role, on a physical till in a staffed shop, is the stronger design.

Admin actions keep the full password, and **re-authentication is required** for the destructive ones
regardless of who is signed in — voiding a completed sale, changing a price, opening the drawer
without a sale.

---

## Deliverables

### 1. Schema — and the project's first real migration

The user tables land as **schema v2**, not as part of Phase 1's v1. That is deliberate: it makes
them the first genuine exercise of the KD-002 migration harness, on a small, well-understood change,
while there is no production data to lose.

```kotlin
@Entity(tableName = "app_user", indices = [Index(value = ["username"], unique = true)])
data class AppUserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val displayNameAr: String,
    val role: UserRole,
    val secretHash: String,          // PBKDF2-HMAC-SHA256, never the credential
    val secretSalt: String,
    val secretKind: SecretKind,      // PIN | PASSWORD
    val isActive: Boolean,
    val mustChangeSecret: Boolean,   // set on admin reset — forces a change at next login
    val failedAttempts: Int,
    val lockedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class UserRole { ADMIN, SELLER }
```

Two roles, matching the ask. `UserRole` is an enum rather than a join to a permissions table because
two fixed roles do not need a permission engine — and an engine nobody configures is just a slower
`when`. If custom roles are ever wanted, that is an additive migration.

### 2. `IPasswordHasher` — platform bridge, KD-005

There is no KDF in Kotlin `commonMain`. Both targets are JVM-based, so:

```kotlin
// core/platform — interface, Koin-bound (ADR-018)
interface IPasswordHasher {
    suspend fun hash(secret: CharArray, salt: ByteArray): String
    suspend fun verify(secret: CharArray, salt: ByteArray, expected: String): Boolean
}
```

`DesktopPasswordHasher` / `AndroidPasswordHasher` both use
`javax.crypto.SecretKeyFactory` with **PBKDF2WithHmacSHA256**, ≥ 210,000 iterations, a 16-byte
random salt per user.

Three rules that are 🔴 blockers:
- **Never a plain hash.** `SHA-256(pin)` over a 4-digit PIN is brute-forced in milliseconds. The
  iteration count is the entire defence for a short credential.
- **`CharArray`, not `String`.** Zero it after use; a `String` sits in the heap until GC.
- **Never log a credential**, not even masked, not even at DEBUG — ADR-029 and the `Error.kt`
  KDoc's existing constraint both already say this.

> **PIN-specific:** a 4-digit PIN has 10,000 combinations, so the KDF alone is not enough. The
> defence is **lockout** — 5 failed attempts locks the account for 5 minutes, escalating. That is
> what `failedAttempts` and `lockedUntil` are for, and the counter must be persisted, not in memory,
> or restarting the app resets it.

### 3. `:features:auth` — mirroring Cashi's golden module

`rules.md` names `features/auth/` as the **golden module** in the reference project. Keswa's should
be recognisably its sibling, following the same layout and MVI shape.

```
features/auth/
├── domain/usecase/
│   ├── SignInUseCase.kt            → Result<SignInResult>
│   ├── BootstrapFirstAdminUseCase.kt
│   ├── ChangeOwnSecretUseCase.kt
│   ├── ResetUserSecretUseCase.kt   (admin only)
│   └── LockSessionUseCase.kt
├── presentation/screens/
│   ├── signin/                     PIN keypad + password form
│   ├── firstrun/                   create the first admin
│   └── lock/                       quick re-unlock
└── di/AuthModule.kt
```

Error layering per ADR-032 — infrastructure failure in the outer `Result`, business outcome in the
sealed class:

```kotlin
sealed interface SignInResult {
    data class Success(val user: User) : SignInResult
    data object BadCredentials : SignInResult
    data class Locked(val untilMillis: Long) : SignInResult
    data class MustChangeSecret(val user: User) : SignInResult
}
```

**`BadCredentials` never says which half was wrong** — "no such user" and "wrong PIN" are the same
message, or the screen becomes a username oracle.

### 4. Session — in memory, and that is the right call

```kotlin
// core/session
interface ISessionManager {
    val current: StateFlow<Session?>
    suspend fun signIn(user: User)
    suspend fun lock()
    suspend fun signOut()
}
```

The session lives **in memory only**. No token is persisted, so:

- Closing the app signs everyone out — correct for a shared till.
- **KD-006 (desktop secure storage) does not block this phase.** There is no server, so no token, so
  nothing Tier-1 to store. That gap stays a Phase 9 problem.

**Auto-lock after idle** (default 5 minutes, configurable) — a till left unattended with the owner's
session open is the realistic threat in a shop, far more than a remote attacker. Locking keeps the
cart intact and asks only for the PIN.

### 5. Permission enforcement — in use cases, not UI

```kotlin
// core/domain/auth
fun Session.require(permission: Permission)   // throws Error.ForbiddenAccess

enum class Permission {
    SELL, REFUND_WITHIN_POLICY, REFUND_ANY, DISCOUNT_LINE, OVERRIDE_PRICE,
    VOID_SALE, RECEIVE_STOCK, COUNT_STOCK, MANAGE_CATALOGUE, MANAGE_USERS,
    VIEW_COST_AND_MARGIN, VIEW_SHOP_ANALYTICS, CHANGE_SETTINGS,
}
```

**The check belongs in the use case.** Hiding a button is a usability affordance, not a security
control — and in an offline desktop app the user owns the machine the UI runs on.

The one that matters commercially: `VIEW_COST_AND_MARGIN`. A seller who can see cost price can
work out the shop's margins, and in a trade where staff move between shops on the same street, that
is the leak the owner actually cares about.

### 6. First run, and the offline reset problem

**First run:** a fresh install has no users, so the app opens on *"create the owner account"* rather
than a sign-in it can never satisfy. Easy to forget; blocks everything.

**The reset problem — flagged, not solved here.** There is no server and no email, so an admin who
forgets their password has no recovery path, and the shop's entire history is in one local SQLite
file. Options, needing a decision before build:

| Option | Trade-off |
|---|---|
| Recovery code shown once at setup | Simple, offline, works — but gets lost exactly as often as the password |
| A second admin account required | Good operational hygiene; no help for a one-person shop |
| Support-issued unlock tied to the install id | Needs a support process to exist |

**Recommendation: recovery code at setup, plus requiring a second admin.** An admin can always reset
a *seller's* PIN — sellers are never locked out permanently.

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No shift management** (opening float, Z-report, till reconciliation). It is the natural next
  step from user attribution and a real POS need — but it is a *cash-handling* feature, not an auth
  one, and it belongs with the sell flow in Phase 5. Flagging rather than absorbing it.
- **No biometrics.** Cashi has `BiometricManager` for Android; desktop has no equivalent, and a
  shared till is the wrong place for a personal credential.
- **No per-user UI preferences.** Settings stay shop-wide.
- **No SSO or server accounts.** Phase 9, if ever.

---

## Verification Plan

| # | Check | Method |
|---|---|---|
| 1 | Hashing | Known salt + secret → stable hash; verify accepts correct and rejects wrong |
| 2 | Iteration count | Assert the configured cost; a test fails if it is ever lowered |
| 3 | Lockout | 5 bad PINs → `Locked`; persists across an app restart |
| 4 | No credential leak | Grep the codebase and the log output for the test PIN — must appear nowhere |
| 5 | Permission enforced in domain | Call a privileged use case with a SELLER session → `ForbiddenAccess`, **with the UI bypassed entirely** |
| 6 | Attribution | A sale made by a seller writes that `userId` onto every movement |
| 7 | Migration v1 → v2 | KD-002 harness: seed v1 data, migrate, assert nothing lost |
| 8 | First run | Empty database opens the bootstrap screen, not sign-in |
| 9 | Auto-lock | Idle past the timeout locks; unlocking restores the cart intact |
| 10 | Username enumeration | Wrong user and wrong PIN return identical messages and similar timing |

Check 5 is the important one. It is easy to ship a build where the only thing standing between a
seller and the margin report is a hidden menu item.

## Definition of Done

- [ ] Admin signs in with a password, seller with a PIN
- [ ] First run creates the owner account
- [ ] Lockout survives a restart
- [ ] Every privileged use case checks permission in the domain layer, tested with the UI bypassed
- [ ] Sales and movements carry the real `userId`
- [ ] v1 → v2 migration test green
- [ ] Auto-lock works and preserves the cart
- [ ] No credential appears in any log at any level
