package keswa.domain.auth

import keswa.core.common.AppResult
import keswa.core.common.DeviceId
import keswa.core.common.DeviceIdProvider
import keswa.core.common.FixedClock
import keswa.domain.Principal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val USER_ID = "user-1"
private const val CORRECT_PIN = "1234"

private class FakeHasher : PasswordHasher {
    override fun hash(plainPin: String) = HashedPin(hash = plainPin, salt = "salt")
    override fun verify(plainPin: String, hash: String, salt: String) = plainPin == hash
}

private class FakeUsers(initial: AppUser) : AppUserRepository {
    var user = initial
        private set

    override suspend fun findActiveForCurrentStore() = listOf(user)
    override suspend fun findById(userId: String) = user.takeIf { it.id == userId }
    override suspend fun recordFailedAttempt(userId: String, lockedUntil: Long?) {
        user = user.copy(failedAttempts = user.failedAttempts + 1, lockedUntil = lockedUntil)
    }
    override suspend fun resetFailedAttempts(userId: String) {
        user = user.copy(failedAttempts = 0, lockedUntil = null)
    }
    override suspend fun permissionsForRole(roleCode: String) = setOf("SALE_CREATE")
}

private fun freshUser() = AppUser(
    id = USER_ID, tenantId = "t1", storeId = "s1", fullName = "Owner", username = "owner",
    pinHash = CORRECT_PIN, pinSalt = "salt", roleCode = "OWNER", isActive = true,
    mustChangePin = false, lockedUntil = null, failedAttempts = 0,
)

private fun harness(user: AppUser = freshUser()): Triple<UnlockWithPin, FakeUsers, FixedClock> {
    val users = FakeUsers(user)
    val clock = FixedClock(1_700_000_000_000L)
    val deviceId = object : DeviceIdProvider { override fun current() = DeviceId("device-1") }
    return Triple(UnlockWithPin(users, FakeHasher(), clock, deviceId), users, clock)
}

class UnlockWithPinTest {

    @Test
    fun `correct pin returns a principal with the user's role and permissions`() = runTest {
        val (unlock, _, _) = harness()
        val result = unlock(USER_ID, CORRECT_PIN)

        assertIs<AppResult.Ok<Principal>>(result)
        assertEquals("OWNER", result.value.roleCode)
        assertEquals(setOf("SALE_CREATE"), result.value.permissions)
        assertEquals("device-1", result.value.deviceId)
    }

    @Test
    fun `wrong pin is rejected without locking out on the first attempt`() = runTest {
        val (unlock, users, _) = harness()
        val result = unlock(USER_ID, "0000")

        assertEquals(AppResult.Err(AuthError.InvalidPin), result)
        assertEquals(1, users.user.failedAttempts)
        assertTrue(users.user.lockedUntil == null)
    }

    @Test
    fun `user locks out after five consecutive wrong attempts`() = runTest {
        val (unlock, users, clock) = harness()

        repeat(4) {
            val r = unlock(USER_ID, "wrong")
            assertEquals(AppResult.Err(AuthError.InvalidPin), r)
        }
        val fifth = unlock(USER_ID, "wrong")

        assertIs<AppResult.Err>(fifth)
        assertIs<AuthError.UserLockedOut>(fifth.error)
        assertEquals(5, users.user.failedAttempts)
        assertEquals(clock.nowEpochMillis() + UnlockWithPin.LOCKOUT_MILLIS, users.user.lockedUntil)
    }

    @Test
    fun `locked out user is rejected even with the correct pin until the lock expires`() = runTest {
        val lockedUntil = 1_700_000_000_000L + 60_000L
        val (unlock, _, clock) = harness(freshUser().copy(lockedUntil = lockedUntil))

        val stillLocked = unlock(USER_ID, CORRECT_PIN)
        assertEquals(AppResult.Err(AuthError.UserLockedOut(lockedUntil)), stillLocked)

        clock.set(lockedUntil + 1)
        val nowAllowed = unlock(USER_ID, CORRECT_PIN)
        assertIs<AppResult.Ok<Principal>>(nowAllowed)
    }

    @Test
    fun `a successful unlock resets the failed attempt counter`() = runTest {
        val (unlock, users, _) = harness(freshUser().copy(failedAttempts = 3))
        unlock(USER_ID, CORRECT_PIN)
        assertEquals(0, users.user.failedAttempts)
    }

    @Test
    fun `unknown user id fails closed`() = runTest {
        val (unlock, _, _) = harness()
        val result = unlock("no-such-user", CORRECT_PIN)
        assertEquals(AppResult.Err(AuthError.NoSuchUser), result)
    }
}
