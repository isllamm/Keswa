package com.alsoug.keswa.features.auth.domain

import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.auth.domain.usecase.BootstrapFirstAdminUseCase
import com.alsoug.keswa.features.auth.domain.usecase.CreateUserUseCase
import com.alsoug.keswa.features.auth.domain.usecase.NeedsFirstRunSetupUseCase
import com.alsoug.keswa.features.auth.domain.usecase.RecoverWithCodeUseCase
import com.alsoug.keswa.features.auth.domain.usecase.RecoveryResult
import com.alsoug.keswa.features.auth.domain.usecase.ResetUserSecretUseCase
import com.alsoug.keswa.features.auth.domain.usecase.SignInResult
import com.alsoug.keswa.features.auth.domain.usecase.SignInUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AccountUseCasesTest {

    private val users = FakeUserRepository()
    private val hasher = FakeHasher()
    private val sessions = InMemorySessionManager()
    private val recovery = FakeRecoveryStore()

    private val bootstrap = BootstrapFirstAdminUseCase(
        users, hasher, SequentialIds(), recovery, newRecoveryCode = { "KESWA-RECOVERY-0001" },
    )
    private val signIn = SignInUseCase(users, hasher, sessions) { 1_000L }

    @Test
    fun `a fresh install needs setup, and stops needing it once an owner exists`() = runTest {
        val needs = NeedsFirstRunSetupUseCase(users)
        assertTrue(needs().getOrThrow())

        bootstrap("hala", "Hala Fouad", "keswa2026".toCharArray()).getOrThrow()

        assertTrue(!needs().getOrThrow())
    }

    @Test
    fun `bootstrap creates an admin who can sign in`() = runTest {
        val result = bootstrap("hala", "Hala Fouad", "keswa2026".toCharArray()).getOrThrow()

        assertEquals(UserRole.ADMIN, result.owner.role)
        assertEquals("KESWA-RECOVERY-0001", result.recoveryCode)
        assertIs<SignInResult.Success>(signIn("hala", "keswa2026".toCharArray()).getOrThrow())
    }

    @Test
    fun `bootstrap refuses a second time, and refuses a weak password`() = runTest {
        assertTrue(bootstrap("hala", "Hala", "short".toCharArray()).isFailure)

        bootstrap("hala", "Hala", "keswa2026".toCharArray()).getOrThrow()

        assertTrue(bootstrap("other", "Other", "keswa2026".toCharArray()).isFailure)
    }

    @Test
    fun `a seller cannot create users, even with the UI bypassed entirely`() = runTest {
        // Given a signed-in seller — no screen involved, the use case called directly
        bootstrap("hala", "Hala", "keswa2026".toCharArray()).getOrThrow()
        signIn("hala", "keswa2026".toCharArray()).getOrThrow()
        val createUser = CreateUserUseCase(users, hasher, SequentialIds("new"), sessions)
        createUser("mona", "Mona", "منى", UserRole.SELLER, "1234".toCharArray()).getOrThrow()
        sessions.signIn(users.stored.values.first { it.user.role == UserRole.SELLER }.user, 0)

        // When the seller tries to add an account
        val attempt = createUser("tarek", "Tarek", "طارق", UserRole.SELLER, "5678".toCharArray())

        // Then the domain refuses, regardless of what any screen would have shown
        assertTrue(attempt.isFailure)
        assertIs<Error.ForbiddenAccess>(attempt.exceptionOrNull())
    }

    @Test
    fun `a seller cannot reset anyone's credential`() = runTest {
        bootstrap("hala", "Hala", "keswa2026".toCharArray()).getOrThrow()
        sessions.signIn(
            User("s1", "mona", "Mona", "منى", UserRole.SELLER),
            atMillis = 0,
        )

        val attempt = ResetUserSecretUseCase(users, hasher, sessions)("user-1", "0000".toCharArray())

        assertTrue(attempt.isFailure)
        assertIs<Error.ForbiddenAccess>(attempt.exceptionOrNull())
    }

    @Test
    fun `an admin reset forces a change at next sign-in`() = runTest {
        bootstrap("hala", "Hala", "keswa2026".toCharArray()).getOrThrow()
        signIn("hala", "keswa2026".toCharArray()).getOrThrow()
        val createUser = CreateUserUseCase(users, hasher, SequentialIds("new"), sessions)
        val seller = createUser("mona", "Mona", "منى", UserRole.SELLER, "1234".toCharArray()).getOrThrow()

        ResetUserSecretUseCase(users, hasher, sessions)(seller.id, "0000".toCharArray()).getOrThrow()

        val result = signIn.byUserId(seller.id, "0000".toCharArray()).getOrThrow()
        assertIs<SignInResult.MustChangeSecret>(result)
    }

    @Test
    fun `the recovery code sets a new admin password`() = runTest {
        bootstrap("hala", "Hala Fouad", "keswa2026".toCharArray()).getOrThrow()
        val recover = RecoverWithCodeUseCase(users, hasher, recovery)

        val result = recover(
            "hala",
            "KESWA-RECOVERY-0001".toCharArray(),
            "brandnewpass".toCharArray(),
        ).getOrThrow()

        assertIs<RecoveryResult.Reset>(result)
        assertIs<SignInResult.Success>(signIn("hala", "brandnewpass".toCharArray()).getOrThrow())
    }

    @Test
    fun `a wrong recovery code changes nothing`() = runTest {
        bootstrap("hala", "Hala", "keswa2026".toCharArray()).getOrThrow()
        val recover = RecoverWithCodeUseCase(users, hasher, recovery)

        val result = recover("hala", "WRONG".toCharArray(), "brandnewpass".toCharArray()).getOrThrow()

        assertIs<RecoveryResult.WrongCode>(result)
        assertIs<SignInResult.Success>(signIn("hala", "keswa2026".toCharArray()).getOrThrow())
    }
}
