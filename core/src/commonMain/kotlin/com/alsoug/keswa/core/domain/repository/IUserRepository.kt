package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole

/** A stored account, including the material only the auth layer may see. */
data class StoredCredential(
    val user: User,
    val secretHash: String,
    val secretSalt: String,
    val secretKind: SecretKind,
    val failedAttempts: Int,
    val lockedUntil: Long?,
)

interface IUserRepository {

    suspend fun create(
        id: String,
        username: String,
        displayName: String,
        displayNameAr: String,
        role: UserRole,
        secretKind: SecretKind,
        secretHash: String,
        secretSalt: String,
        mustChangeSecret: Boolean = false,
    ): Result<User>

    suspend fun findByUsername(username: String): Result<StoredCredential?>

    suspend fun findById(id: String): Result<StoredCredential?>

    suspend fun sellers(): Result<List<User>>

    suspend fun countActive(): Result<Int>

    suspend fun countActiveAdmins(): Result<Int>

    suspend fun recordFailure(userId: String, attempts: Int, lockedUntil: Long?): Result<Unit>

    suspend fun clearFailures(userId: String): Result<Unit>

    suspend fun replaceSecret(
        userId: String,
        secretHash: String,
        secretSalt: String,
        mustChangeSecret: Boolean,
    ): Result<Unit>
}
