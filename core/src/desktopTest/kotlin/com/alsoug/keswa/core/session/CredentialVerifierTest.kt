package com.alsoug.keswa.core.session

import com.alsoug.keswa.core.domain.auth.LockoutPolicy
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.StoredCredential
import com.alsoug.keswa.core.platform.IPasswordHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * An approval dialog that never locks is an unlimited oracle for guessing the admin password,
 * reachable by anyone who can open a cart — or bring something back.
 *
 * Lives in `:core` alongside the verifier, which two features now share.
 */
class CredentialVerifierTest {

    /** ADR-019: hand-written fakes. Honest about shape, not about key-derivation cost. */
    private class FakeHasher : IPasswordHasher {
        override suspend fun hash(secret: CharArray, salt: ByteArray): String =
            "hash(${secret.concatToString()})"

        override suspend fun verify(secret: CharArray, salt: ByteArray, expected: String): Boolean =
            hash(secret, salt) == expected

        override fun newSalt(): ByteArray = ByteArray(4) { 1 }
    }

    private class FakeUsers(private val hasher: FakeHasher) : IUserRepository {
        val stored = mutableMapOf<String, StoredCredential>()

        suspend fun add(id: String, username: String, role: UserRole, secret: String) {
            stored[id] = StoredCredential(
                user = User(id, username, username, username, role),
                secretHash = hasher.hash(secret.toCharArray(), ByteArray(0)),
                secretSalt = "01010101",
                secretKind = SecretKind.PASSWORD,
                failedAttempts = 0,
                lockedUntil = null,
            )
        }

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
        ): Result<User> = error("not used")

        override suspend fun findByUsername(username: String): Result<StoredCredential?> =
            Result.success(stored.values.firstOrNull { it.user.username == username })

        override suspend fun findById(id: String): Result<StoredCredential?> =
            Result.success(stored[id])

        override suspend fun sellers(): Result<List<User>> = Result.success(emptyList())

        override suspend fun countActive(): Result<Int> = Result.success(stored.size)

        override suspend fun countActiveAdmins(): Result<Int> = Result.success(1)

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
        ): Result<Unit> = error("not used")
    }

    private val hasher = FakeHasher()
    private val users = FakeUsers(hasher)
    private val now = 1_757_000_000_000L

    private val verifier = LockoutAwareCredentialVerifier(users, hasher) { now }

    private suspend fun seed() {
        users.add("usr-admin", "owner", UserRole.ADMIN, "s3cret")
        users.add("usr-seller", "sara", UserRole.SELLER, "1234")
    }

    @Test
    fun `the right admin credential approves`() = runTest {
        seed()

        val result = verifier.approve("owner", "s3cret".toCharArray(), Permission.DISCOUNT_LINE)

        assertEquals("usr-admin", assertIs<ApprovalResult.Approved>(result).user.id)
    }

    @Test
    fun `a seller's own credential cannot approve a discount`() = runTest {
        seed()

        val result = verifier.approve("sara", "1234".toCharArray(), Permission.DISCOUNT_LINE)

        // Said separately from a wrong password, because this one the cashier should stop retrying.
        assertIs<ApprovalResult.NotPermitted>(result)
    }

    @Test
    fun `a seller cannot approve a late return either`() = runTest {
        seed()

        assertIs<ApprovalResult.NotPermitted>(
            verifier.approve("sara", "1234".toCharArray(), Permission.REFUND_ANY),
        )
        assertIs<ApprovalResult.Approved>(
            verifier.approve("owner", "s3cret".toCharArray(), Permission.REFUND_ANY),
        )
    }

    @Test
    fun `an unknown user and a wrong password are the same answer`() = runTest {
        seed()

        // Or the dialog becomes a way to discover who works here.
        assertIs<ApprovalResult.BadCredentials>(
            verifier.approve("nobody", "s3cret".toCharArray(), Permission.DISCOUNT_LINE),
        )
        assertIs<ApprovalResult.BadCredentials>(
            verifier.approve("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE),
        )
    }

    @Test
    fun `repeated guessing locks the account, on the same counter as sign-in`() = runTest {
        seed()

        repeat(LockoutPolicy.MAX_ATTEMPTS - 1) {
            assertIs<ApprovalResult.BadCredentials>(
                verifier.approve("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE),
            )
        }

        assertIs<ApprovalResult.Locked>(
            verifier.approve("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE),
        )
        // The counter lives on the user row, so it is the same one sign-in reads.
        assertEquals(LockoutPolicy.MAX_ATTEMPTS, users.stored.getValue("usr-admin").failedAttempts)
    }

    @Test
    fun `a locked account is refused even with the right password`() = runTest {
        seed()
        repeat(LockoutPolicy.MAX_ATTEMPTS) {
            verifier.approve("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE)
        }

        assertIs<ApprovalResult.Locked>(
            verifier.approve("owner", "s3cret".toCharArray(), Permission.DISCOUNT_LINE),
        )
    }

    @Test
    fun `a successful approval clears the failures behind it`() = runTest {
        seed()
        repeat(2) { verifier.approve("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE) }

        verifier.approve("owner", "s3cret".toCharArray(), Permission.DISCOUNT_LINE)

        assertEquals(0, users.stored.getValue("usr-admin").failedAttempts)
    }
}
