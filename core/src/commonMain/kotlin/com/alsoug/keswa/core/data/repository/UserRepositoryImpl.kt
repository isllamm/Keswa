package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.database.dao.UserDao
import com.alsoug.keswa.core.database.entities.AppUserEntity
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.StoredCredential

class UserRepositoryImpl(
    private val dao: UserDao,
    private val now: () -> Long,
) : IUserRepository {

    override suspend fun create(
        id: String,
        username: String,
        displayName: String,
        displayNameAr: String,
        role: UserRole,
        secretKind: SecretKind,
        secretHash: String,
        secretSalt: String,
        mustChangeSecret: Boolean,
    ): Result<User> = runCatchingCancellable {
        val timestamp = now()
        val entity = AppUserEntity(
            id = id,
            username = username.trim().lowercase(),
            displayName = displayName,
            displayNameAr = displayNameAr,
            role = role,
            secretHash = secretHash,
            secretSalt = secretSalt,
            secretKind = secretKind,
            isActive = true,
            mustChangeSecret = mustChangeSecret,
            failedAttempts = 0,
            lockedUntil = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        dao.upsert(entity)
        entity.toUser()
    }

    override suspend fun findByUsername(username: String): Result<StoredCredential?> =
        runCatchingCancellable {
            dao.findByUsername(username.trim().lowercase())?.toCredential()
        }

    override suspend fun findById(id: String): Result<StoredCredential?> =
        runCatchingCancellable { dao.getById(id)?.toCredential() }

    override suspend fun sellers(): Result<List<User>> =
        runCatchingCancellable { dao.getByRole(UserRole.SELLER).map { it.toUser() } }

    override suspend fun countActive(): Result<Int> = runCatchingCancellable { dao.countActive() }

    override suspend fun countActiveAdmins(): Result<Int> =
        runCatchingCancellable { dao.countActiveAdmins() }

    override suspend fun recordFailure(
        userId: String,
        attempts: Int,
        lockedUntil: Long?,
    ): Result<Unit> = runCatchingCancellable {
        val existing = requireNotNull(dao.getById(userId)) { "user not found: $userId" }
        dao.update(
            existing.copy(failedAttempts = attempts, lockedUntil = lockedUntil, updatedAt = now()),
        )
    }

    override suspend fun clearFailures(userId: String): Result<Unit> = runCatchingCancellable {
        val existing = requireNotNull(dao.getById(userId)) { "user not found: $userId" }
        dao.update(existing.copy(failedAttempts = 0, lockedUntil = null, updatedAt = now()))
    }

    override suspend fun replaceSecret(
        userId: String,
        secretHash: String,
        secretSalt: String,
        mustChangeSecret: Boolean,
    ): Result<Unit> = runCatchingCancellable {
        val existing = requireNotNull(dao.getById(userId)) { "user not found: $userId" }
        dao.update(
            existing.copy(
                secretHash = secretHash,
                secretSalt = secretSalt,
                mustChangeSecret = mustChangeSecret,
                failedAttempts = 0,
                lockedUntil = null,
                updatedAt = now(),
            ),
        )
    }

    private fun AppUserEntity.toUser() = User(
        id = id,
        username = username,
        displayName = displayName,
        displayNameAr = displayNameAr,
        role = role,
        mustChangeSecret = mustChangeSecret,
    )

    private fun AppUserEntity.toCredential() = StoredCredential(
        user = toUser(),
        secretHash = secretHash,
        secretSalt = secretSalt,
        secretKind = secretKind,
        failedAttempts = failedAttempts,
        lockedUntil = lockedUntil,
    )
}
