package keswa.data.auth

import keswa.core.common.Clock
import keswa.data.db.KeswaDatabase
import keswa.domain.auth.AppUser
import keswa.domain.auth.AppUserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import migrations.App_user

class SqlAppUserRepository(
    private val database: KeswaDatabase,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    private val tenantId: suspend () -> String,
    private val storeId: suspend () -> String,
) : AppUserRepository {

    override suspend fun findActiveForCurrentStore(): List<AppUser> = withContext(ioDispatcher) {
        database.appUserQueries.selectActiveByStore(tenantId(), storeId()).executeAsList().map { it.toDomain() }
    }

    override suspend fun findById(userId: String): AppUser? = withContext(ioDispatcher) {
        database.appUserQueries.selectById(userId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun recordFailedAttempt(userId: String, lockedUntil: Long?) {
        withContext(ioDispatcher) {
            database.appUserQueries.recordFailedAttempt(
                lockedUntil = lockedUntil,
                updatedAt = clock.nowEpochMillis(),
                id = userId,
            )
        }
    }

    override suspend fun resetFailedAttempts(userId: String) {
        withContext(ioDispatcher) {
            database.appUserQueries.resetFailedAttempts(updatedAt = clock.nowEpochMillis(), id = userId)
        }
    }

    override suspend fun permissionsForRole(roleCode: String): Set<String> = withContext(ioDispatcher) {
        database.rolePermissionQueries.selectByRole(roleCode).executeAsList().toSet()
    }
}

private fun App_user.toDomain() = AppUser(
    id = id,
    tenantId = tenant_id,
    storeId = store_id,
    fullName = full_name,
    username = username,
    pinHash = pin_hash,
    pinSalt = pin_salt,
    roleCode = role_code,
    isActive = is_active,
    mustChangePin = must_change_pin,
    lockedUntil = locked_until,
    failedAttempts = failed_attempts.toInt(),
)
