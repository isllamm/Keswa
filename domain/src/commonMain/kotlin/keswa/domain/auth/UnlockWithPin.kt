package keswa.domain.auth

import keswa.core.common.AppResult
import keswa.core.common.Clock
import keswa.core.common.DeviceIdProvider
import keswa.domain.Principal

/**
 * Verifies a PIN entered for a specific, already-selected user (the PIN screen shows a picker
 * first — see docs/presentation-architecture.md's PosContract example and ADR-008). Locks the
 * account out after [MAX_ATTEMPTS] consecutive failures.
 */
class UnlockWithPin(
    private val users: AppUserRepository,
    private val hasher: PasswordHasher,
    private val clock: Clock,
    private val deviceId: DeviceIdProvider,
) {
    suspend operator fun invoke(userId: String, pin: String): AppResult<Principal> {
        val user = users.findById(userId) ?: return AppResult.Err(AuthError.NoSuchUser)

        val lockedUntil = user.lockedUntil
        val now = clock.nowEpochMillis()
        if (lockedUntil != null && lockedUntil > now) {
            return AppResult.Err(AuthError.UserLockedOut(lockedUntil))
        }

        if (!hasher.verify(pin, user.pinHash, user.pinSalt)) {
            val attempts = user.failedAttempts + 1
            val newLockUntil = if (attempts >= MAX_ATTEMPTS) now + LOCKOUT_MILLIS else null
            users.recordFailedAttempt(user.id, newLockUntil)
            return AppResult.Err(
                if (newLockUntil != null) AuthError.UserLockedOut(newLockUntil) else AuthError.InvalidPin,
            )
        }

        users.resetFailedAttempts(user.id)
        val permissions = users.permissionsForRole(user.roleCode)
        return AppResult.Ok(
            Principal(
                userId = user.id,
                tenantId = user.tenantId,
                storeId = user.storeId,
                deviceId = deviceId.current().value,
                roleCode = user.roleCode,
                permissions = permissions,
            ),
        )
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 5 * 60_000L
    }
}
