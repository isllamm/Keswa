package com.alsoug.keswa.features.auth.domain

import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.model.can
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.auth.domain.usecase.SignInResult
import com.alsoug.keswa.features.auth.domain.usecase.SignInUseCase
import com.alsoug.keswa.features.auth.domain.usecase.encodeSalt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SignInUseCaseTest {

    private val users = FakeUserRepository()
    private val hasher = FakeHasher()
    private val sessions = InMemorySessionManager()
    private var now = 1_000_000L

    private val signIn = SignInUseCase(users, hasher, sessions) { now }

    private suspend fun givenSeller(pin: String = "1234", id: String = "u1") {
        val salt = hasher.newSalt()
        users.create(
            id = id,
            username = "mona",
            displayName = "Mona Adel",
            displayNameAr = "منى عادل",
            role = UserRole.SELLER,
            secretKind = SecretKind.PIN,
            secretHash = hasher.hash(pin.toCharArray(), salt),
            secretSalt = salt.encodeSalt(),
        )
    }

    @Test
    fun `the right PIN opens a session`() = runTest {
        givenSeller()

        val result = signIn.byUserId("u1", "1234".toCharArray()).getOrThrow()

        val success = assertIs<SignInResult.Success>(result)
        assertEquals("Mona Adel", success.user.displayName)
        assertEquals(success.user.id, sessions.current.value?.user?.id)
    }

    @Test
    fun `a seller's session can sell but not manage the catalogue`() = runTest {
        givenSeller()
        signIn.byUserId("u1", "1234".toCharArray()).getOrThrow()

        val session = sessions.current.value
        assertTrue(session.can(Permission.SELL))
        assertTrue(session.can(Permission.COUNT_STOCK))
        assertFalse(session.can(Permission.MANAGE_CATALOGUE))
        // The one that matters commercially: staff move between shops on the same street.
        assertFalse(session.can(Permission.VIEW_COST_AND_MARGIN))
    }

    @Test
    fun `a wrong PIN leaves no session and counts the attempt`() = runTest {
        givenSeller()

        val result = signIn.byUserId("u1", "9999".toCharArray()).getOrThrow()

        assertIs<SignInResult.BadCredentials>(result)
        assertNull(sessions.current.value)
        assertEquals(1, users.stored.getValue("u1").failedAttempts)
    }

    @Test
    fun `five wrong PINs lock the account`() = runTest {
        givenSeller()

        repeat(4) { signIn.byUserId("u1", "0000".toCharArray()).getOrThrow() }
        val fifth = signIn.byUserId("u1", "0000".toCharArray()).getOrThrow()

        val locked = assertIs<SignInResult.Locked>(fifth)
        assertEquals(now + 5 * 60_000L, locked.untilMillis)
    }

    @Test
    fun `a locked account refuses even the correct PIN until the wait expires`() = runTest {
        givenSeller()
        repeat(5) { signIn.byUserId("u1", "0000".toCharArray()).getOrThrow() }

        // Correct PIN, but still inside the lockout
        assertIs<SignInResult.Locked>(signIn.byUserId("u1", "1234".toCharArray()).getOrThrow())
        assertNull(sessions.current.value)

        // And once it expires, the right PIN works again
        now += 5 * 60_000L + 1
        assertIs<SignInResult.Success>(signIn.byUserId("u1", "1234".toCharArray()).getOrThrow())
    }

    @Test
    fun `the lockout is stored, so restarting the app does not clear it`() = runTest {
        givenSeller()
        repeat(5) { signIn.byUserId("u1", "0000".toCharArray()).getOrThrow() }

        // A restart loses the session but not the record — the counter is in the database
        val persisted = users.stored.getValue("u1")
        assertEquals(5, persisted.failedAttempts)
        assertEquals(now + 5 * 60_000L, persisted.lockedUntil)
    }

    @Test
    fun `a successful sign-in clears the failure count`() = runTest {
        givenSeller()
        repeat(3) { signIn.byUserId("u1", "0000".toCharArray()).getOrThrow() }

        signIn.byUserId("u1", "1234".toCharArray()).getOrThrow()

        assertEquals(0, users.stored.getValue("u1").failedAttempts)
    }

    @Test
    fun `an unknown username is indistinguishable from a wrong password`() = runTest {
        givenSeller()

        val unknown = signIn("nobody", "whatever".toCharArray()).getOrThrow()
        val wrong = signIn.byUserId("u1", "0000".toCharArray()).getOrThrow()

        // Same answer either way — otherwise the screen enumerates who works here
        assertIs<SignInResult.BadCredentials>(unknown)
        assertIs<SignInResult.BadCredentials>(wrong)
        assertEquals(unknown::class, wrong::class)
    }

    @Test
    fun `an account flagged for change signs in to a change screen, not a till`() = runTest {
        givenSeller()
        users.replaceSecret("u1", users.stored.getValue("u1").secretHash, users.stored.getValue("u1").secretSalt, mustChangeSecret = true)

        val result = signIn.byUserId("u1", "1234".toCharArray()).getOrThrow()

        assertIs<SignInResult.MustChangeSecret>(result)
        assertNull(sessions.current.value, "no session until the credential is replaced")
    }
}
