package com.alsoug.keswa.features.auth.domain

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.StoredCredential
import com.alsoug.keswa.core.platform.IPasswordHasher
import com.alsoug.keswa.features.auth.domain.usecase.RecoveryCodeStore

/** ADR-019: hand-written fakes. */

class SequentialIds(private val prefix: String = "user") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}

/**
 * A hasher that is honest about shape but not about cost — it derives deterministically so tests
 * are fast and reproducible. The real key-derivation cost is asserted separately, in
 * `JvmPasswordHasherTest`, where it belongs.
 */
class FakeHasher : IPasswordHasher {
    override suspend fun hash(secret: CharArray, salt: ByteArray): String =
        "hash(${secret.concatToString()}:${salt.joinToString("")})"

    override suspend fun verify(secret: CharArray, salt: ByteArray, expected: String): Boolean =
        hash(secret, salt) == expected

    override fun newSalt(): ByteArray = ByteArray(4) { 1 }
}

class FakeUserRepository : IUserRepository {

    val stored = mutableMapOf<String, StoredCredential>()

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
    ): Result<User> {
        val user = User(id, username.lowercase(), displayName, displayNameAr, role, mustChangeSecret)
        stored[id] = StoredCredential(user, secretHash, secretSalt, secretKind, 0, null)
        return Result.success(user)
    }

    override suspend fun findByUsername(username: String): Result<StoredCredential?> =
        Result.success(stored.values.firstOrNull { it.user.username == username.lowercase() })

    override suspend fun findById(id: String): Result<StoredCredential?> =
        Result.success(stored[id])

    override suspend fun sellers(): Result<List<User>> =
        Result.success(stored.values.map { it.user }.filter { it.role == UserRole.SELLER })

    override suspend fun countActive(): Result<Int> = Result.success(stored.size)

    override suspend fun countActiveAdmins(): Result<Int> =
        Result.success(stored.values.count { it.user.role == UserRole.ADMIN })

    override suspend fun recordFailure(
        userId: String,
        attempts: Int,
        lockedUntil: Long?,
    ): Result<Unit> {
        stored[userId]?.let {
            stored[userId] = it.copy(failedAttempts = attempts, lockedUntil = lockedUntil)
        }
        return Result.success(Unit)
    }

    override suspend fun clearFailures(userId: String): Result<Unit> {
        stored[userId]?.let { stored[userId] = it.copy(failedAttempts = 0, lockedUntil = null) }
        return Result.success(Unit)
    }

    override suspend fun replaceSecret(
        userId: String,
        secretHash: String,
        secretSalt: String,
        mustChangeSecret: Boolean,
    ): Result<Unit> {
        stored[userId]?.let {
            stored[userId] = it.copy(
                user = it.user.copy(mustChangeSecret = mustChangeSecret),
                secretHash = secretHash,
                secretSalt = secretSalt,
                failedAttempts = 0,
                lockedUntil = null,
            )
        }
        return Result.success(Unit)
    }
}

class FakeRecoveryStore : RecoveryCodeStore {
    private var value: Pair<String, String>? = null
    override suspend fun store(hash: String, salt: String) { value = hash to salt }
    override suspend fun read(): Pair<String, String>? = value
}
