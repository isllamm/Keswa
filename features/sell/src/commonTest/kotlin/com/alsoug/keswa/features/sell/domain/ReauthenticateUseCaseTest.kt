package com.alsoug.keswa.features.sell.domain

import com.alsoug.keswa.core.domain.auth.LockoutPolicy
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.features.sell.domain.usecase.ReauthResult
import com.alsoug.keswa.features.sell.domain.usecase.ReauthenticateUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * An approval dialog that never locks is an unlimited oracle for guessing the admin password,
 * reachable by anyone who can open a cart.
 */
class ReauthenticateUseCaseTest {

    private val users = FakeUserRepository()
    private val hasher = FakeHasher()
    private var now = 1_757_000_000_000L

    private val reauthenticate = ReauthenticateUseCase(users, hasher) { now }

    private suspend fun seed() {
        users.add("usr-admin", "owner", UserRole.ADMIN, "s3cret", hasher)
        users.add("usr-seller", "sara", UserRole.SELLER, "1234", hasher)
    }

    @Test
    fun `the right admin credential approves`() = runTest {
        seed()

        val result = reauthenticate("owner", "s3cret".toCharArray(), Permission.DISCOUNT_LINE)
            .getOrThrow()

        assertEquals("usr-admin", assertIs<ReauthResult.Approved>(result).user.id)
    }

    @Test
    fun `a seller's own credential cannot approve a discount`() = runTest {
        seed()

        val result = reauthenticate("sara", "1234".toCharArray(), Permission.DISCOUNT_LINE)
            .getOrThrow()

        // Said separately from a wrong password, because this one the cashier should stop retrying.
        assertIs<ReauthResult.NotPermitted>(result)
    }

    @Test
    fun `an unknown user and a wrong password are the same answer`() = runTest {
        seed()

        val unknown = reauthenticate("nobody", "s3cret".toCharArray(), Permission.DISCOUNT_LINE)
            .getOrThrow()
        val wrong = reauthenticate("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE)
            .getOrThrow()

        // Or the dialog becomes a way to discover who works here.
        assertIs<ReauthResult.BadCredentials>(unknown)
        assertIs<ReauthResult.BadCredentials>(wrong)
    }

    @Test
    fun `repeated guessing locks the account, on the same counter as sign-in`() = runTest {
        seed()

        repeat(LockoutPolicy.MAX_ATTEMPTS - 1) {
            assertIs<ReauthResult.BadCredentials>(
                reauthenticate("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE).getOrThrow(),
            )
        }

        val locked = reauthenticate("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE)
            .getOrThrow()

        assertIs<ReauthResult.Locked>(locked)
        // The counter lives on the user row, so it is the same one sign-in reads.
        assertEquals(LockoutPolicy.MAX_ATTEMPTS, users.stored.getValue("usr-admin").failedAttempts)
    }

    @Test
    fun `a locked account is refused even with the right password`() = runTest {
        seed()
        repeat(LockoutPolicy.MAX_ATTEMPTS) {
            reauthenticate("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE).getOrThrow()
        }

        assertIs<ReauthResult.Locked>(
            reauthenticate("owner", "s3cret".toCharArray(), Permission.DISCOUNT_LINE).getOrThrow(),
        )
    }

    @Test
    fun `a successful approval clears the failures behind it`() = runTest {
        seed()
        repeat(2) { reauthenticate("owner", "wrong".toCharArray(), Permission.DISCOUNT_LINE).getOrThrow() }

        reauthenticate("owner", "s3cret".toCharArray(), Permission.DISCOUNT_LINE).getOrThrow()

        assertEquals(0, users.stored.getValue("usr-admin").failedAttempts)
    }

    @Test
    fun `approving never opens a session`() = runTest {
        seed()

        reauthenticate("owner", "s3cret".toCharArray(), Permission.VOID_SALE).getOrThrow()

        // Nothing here touches ISessionManager: the seller is still the seller, and the approval
        // is recorded against the line rather than against who is signed in.
        assertEquals(2, users.stored.size)
    }
}
