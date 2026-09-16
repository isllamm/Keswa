package com.alsoug.keswa.features.sell.domain.usecase

import com.alsoug.keswa.core.domain.auth.LockoutPolicy
import com.alsoug.keswa.core.domain.auth.decodeSalt
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.permissions
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.platform.IPasswordHasher

sealed interface ReauthResult {
    data class Approved(val user: User) : ReauthResult
    data object BadCredentials : ReauthResult
    data object NotPermitted : ReauthResult
    data class Locked(val untilMillis: Long) : ReauthResult
}

/**
 * Asks someone with authority to approve one action, without disturbing the session.
 *
 * A seller has `SELL` but not `DISCOUNT_LINE`, `OVERRIDE_PRICE` or `VOID_SALE`. The realistic flow
 * in a shop is that the owner walks over and approves the markdown — not that the seller signs out,
 * the owner signs in, does it, signs out, and hands the till back with a customer waiting.
 *
 * Two things this must get right:
 *
 * - **It feeds the same lockout counter as sign-in.** An approval dialog that never locks is an
 *   unlimited oracle for guessing the admin password, reachable by anyone who can open a cart.
 * - **It never opens a session.** The seller is still the seller; the approval is recorded against
 *   the line, which is what makes "who authorised this discount" answerable later.
 */
class ReauthenticateUseCase(
    private val users: IUserRepository,
    private val hasher: IPasswordHasher,
    private val now: () -> Long,
) {

    suspend operator fun invoke(
        username: String,
        secret: CharArray,
        permission: Permission,
    ): Result<ReauthResult> = runCatching {
        val timestamp = now()
        val credential = users.findByUsername(username).getOrThrow()

        // An unknown user still costs a verification, so this cannot be used to discover who works
        // here — the same reasoning as `SignInUseCase`.
        if (credential == null) {
            hasher.verify(secret, DECOY_SALT, DECOY_HASH)
            return@runCatching ReauthResult.BadCredentials
        }

        credential.lockedUntil?.let { until ->
            if (timestamp < until) return@runCatching ReauthResult.Locked(until)
        }

        val matches = hasher.verify(secret, credential.secretSalt.decodeSalt(), credential.secretHash)
        if (!matches) {
            val attempts = credential.failedAttempts + 1
            val lockUntil = LockoutPolicy.lockUntil(attempts, timestamp)
            users.recordFailure(credential.user.id, attempts, lockUntil).getOrThrow()
            return@runCatching lockUntil?.let { ReauthResult.Locked(it) } ?: ReauthResult.BadCredentials
        }

        users.clearFailures(credential.user.id).getOrThrow()

        // Right credential, wrong person: said separately from a bad password, because this one is
        // worth the cashier knowing rather than retyping.
        if (permission !in credential.user.role.permissions) return@runCatching ReauthResult.NotPermitted

        ReauthResult.Approved(credential.user)
    }

    private companion object {
        /** Cost-matching material for the unknown-user path. Never a real credential. */
        val DECOY_SALT = ByteArray(16) { it.toByte() }
        const val DECOY_HASH = "0000000000000000000000000000000000000000000="
    }
}
