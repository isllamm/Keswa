package com.alsoug.keswa.core.session

import com.alsoug.keswa.core.domain.auth.LockoutPolicy
import com.alsoug.keswa.core.domain.auth.decodeSalt
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.permissions
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.platform.IPasswordHasher

sealed interface ApprovalResult {
    data class Approved(val user: User) : ApprovalResult
    data object BadCredentials : ApprovalResult
    data object NotPermitted : ApprovalResult
    data class Locked(val untilMillis: Long) : ApprovalResult
}

/**
 * Asks somebody with authority to approve one action, without touching the session.
 *
 * The realistic flow in a shop: the owner walks over and approves the markdown, or the late
 * return. Not that the seller signs out, the owner signs in, does it, signs out, and hands the
 * till back with a customer waiting.
 *
 * In `:core` because two features now need it — the till, for discounts, overridden prices and
 * voids; and returns, for anything outside the policy window. Phase 5 moved [LockoutPolicy] here
 * for the same reason, and this is the rest of that move.
 *
 * Two things it must get right:
 *
 * - **It feeds the same lockout counter as sign-in.** An approval dialog that never locks is an
 *   unlimited oracle for guessing the admin password, reachable by anyone who can open a cart.
 * - **It never opens a session.** The seller is still the seller; the approval is recorded against
 *   the line or the document, which is what makes "who authorised this" answerable later.
 */
interface ICredentialVerifier {
    suspend fun approve(
        username: String,
        secret: CharArray,
        permission: Permission,
    ): ApprovalResult
}

class LockoutAwareCredentialVerifier(
    private val users: IUserRepository,
    private val hasher: IPasswordHasher,
    private val now: () -> Long,
) : ICredentialVerifier {

    override suspend fun approve(
        username: String,
        secret: CharArray,
        permission: Permission,
    ): ApprovalResult {
        val timestamp = now()
        val credential = users.findByUsername(username).getOrThrow()

        // An unknown user still costs a verification, so this cannot be used to discover who works
        // here — the same reasoning as `SignInUseCase`.
        if (credential == null) {
            hasher.verify(secret, DECOY_SALT, DECOY_HASH)
            return ApprovalResult.BadCredentials
        }

        credential.lockedUntil?.let { until ->
            if (timestamp < until) return ApprovalResult.Locked(until)
        }

        val matches = hasher.verify(secret, credential.secretSalt.decodeSalt(), credential.secretHash)
        if (!matches) {
            val attempts = credential.failedAttempts + 1
            val lockUntil = LockoutPolicy.lockUntil(attempts, timestamp)
            users.recordFailure(credential.user.id, attempts, lockUntil).getOrThrow()
            return lockUntil?.let { ApprovalResult.Locked(it) } ?: ApprovalResult.BadCredentials
        }

        users.clearFailures(credential.user.id).getOrThrow()

        // Right credential, wrong person: said separately from a bad password, because this one is
        // worth the cashier knowing rather than retyping.
        if (permission !in credential.user.role.permissions) return ApprovalResult.NotPermitted

        return ApprovalResult.Approved(credential.user)
    }

    private companion object {
        /** Cost-matching material for the unknown-user path. Never a real credential. */
        val DECOY_SALT = ByteArray(16) { it.toByte() }
        const val DECOY_HASH = "0000000000000000000000000000000000000000000="
    }
}
