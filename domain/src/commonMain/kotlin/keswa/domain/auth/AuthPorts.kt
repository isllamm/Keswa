package keswa.domain.auth

import keswa.core.common.AppError
import keswa.core.common.ErrorKey

/** PIN verification and hashing. JVM impl is Argon2id-backed — see ADR-008. */
interface PasswordHasher {
    fun hash(plainPin: String): HashedPin
    fun verify(plainPin: String, hash: String, salt: String): Boolean
}

data class HashedPin(val hash: String, val salt: String)

/** Repository port for [AppUser] — implemented against SQLDelight in :data. */
interface AppUserRepository {
    suspend fun findActiveForCurrentStore(): List<AppUser>
    suspend fun findById(userId: String): AppUser?
    suspend fun recordFailedAttempt(userId: String, lockedUntil: Long?)
    suspend fun resetFailedAttempts(userId: String)
    suspend fun permissionsForRole(roleCode: String): Set<String>
}

/** The single store this install belongs to. Real store selection arrives in Phase 5. */
interface StoreContext {
    suspend fun tenantId(): String
    suspend fun storeId(): String
}

sealed class AuthError(override val key: ErrorKey) : AppError {
    data object InvalidPin : AuthError(ErrorKey("error.auth.invalid_pin"))
    data class UserLockedOut(val lockedUntilEpochMillis: Long) : AuthError(ErrorKey("error.auth.locked_out"))
    data object NoSuchUser : AuthError(ErrorKey("error.auth.no_such_user"))
    data object NoUsersConfigured : AuthError(ErrorKey("error.auth.no_users_configured"))
}
