package com.alsoug.keswa.features.auth.domain.usecase

import com.alsoug.keswa.core.domain.auth.LockoutPolicy
import com.alsoug.keswa.core.domain.auth.decodeSalt
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.StoredCredential
import com.alsoug.keswa.core.platform.IPasswordHasher
import com.alsoug.keswa.core.session.ISessionManager

sealed interface SignInResult {
    data class Success(val user: User) : SignInResult
    data object BadCredentials : SignInResult
    data class Locked(val untilMillis: Long) : SignInResult
    data class MustChangeSecret(val user: User) : SignInResult
}

/**
 * Verifies a credential and opens a session.
 *
 * **[SignInResult.BadCredentials] never says which half was wrong.** "No such user" and "wrong PIN"
 * are the same answer, or the sign-in screen becomes a way to enumerate who works here.
 */
class SignInUseCase(
    private val users: IUserRepository,
    private val hasher: IPasswordHasher,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {

    /** Admins sign in by username. */
    suspend operator fun invoke(username: String, secret: CharArray): Result<SignInResult> =
        runCatching {
            authenticate(users.findByUsername(username).getOrThrow(), secret)
        }

    /** Sellers pick themselves from a list and enter a PIN, so the lookup is by id. */
    suspend fun byUserId(userId: String, secret: CharArray): Result<SignInResult> =
        runCatching {
            authenticate(users.findById(userId).getOrThrow(), secret)
        }

    private suspend fun authenticate(
        credential: StoredCredential?,
        secret: CharArray,
    ): SignInResult {
        val timestamp = now()

        // An unknown user still costs a verification, so the two cases take similar time and the
        // screen cannot be used to discover who exists.
        if (credential == null) {
            hasher.verify(secret, DECOY_SALT, DECOY_HASH)
            return SignInResult.BadCredentials
        }

        credential.lockedUntil?.let { until ->
            if (timestamp < until) return SignInResult.Locked(until)
        }

        val salt = credential.secretSalt.decodeSalt()
        val matches = hasher.verify(secret, salt, credential.secretHash)

        if (!matches) {
            val attempts = credential.failedAttempts + 1
            val lockUntil = LockoutPolicy.lockUntil(attempts, timestamp)
            users.recordFailure(credential.user.id, attempts, lockUntil).getOrThrow()
            return lockUntil?.let { SignInResult.Locked(it) } ?: SignInResult.BadCredentials
        }

        users.clearFailures(credential.user.id).getOrThrow()

        // Forced after an admin reset: the new secret is known to whoever set it, so it cannot be
        // the one the account keeps.
        if (credential.user.mustChangeSecret) return SignInResult.MustChangeSecret(credential.user)

        sessions.signIn(credential.user, timestamp)
        return SignInResult.Success(credential.user)
    }

    private companion object {
        /** Cost-matching material for the unknown-user path. Never a real credential. */
        val DECOY_SALT = ByteArray(16) { it.toByte() }
        const val DECOY_HASH = "0000000000000000000000000000000000000000000="
    }
}
