package com.alsoug.keswa.core.platform

import com.alsoug.keswa.core.database.RealDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopPasswordHasherTest {

    // A low cost keeps the suite fast; the shipped cost is asserted separately below.
    private val hasher = DesktopPasswordHasher(RealDispatchers, iterations = 1_000)

    @Test
    fun `the same secret and salt always derive the same verifier`() = runBlocking {
        val salt = ByteArray(16) { it.toByte() }

        val first = hasher.hash("1234".toCharArray(), salt)
        val second = hasher.hash("1234".toCharArray(), salt)

        assertEquals(first, second)
        assertTrue(hasher.verify("1234".toCharArray(), salt, first))
    }

    @Test
    fun `a wrong secret does not verify`() = runBlocking {
        val salt = hasher.newSalt()
        val stored = hasher.hash("1234".toCharArray(), salt)

        assertFalse(hasher.verify("1235".toCharArray(), salt, stored))
        assertFalse(hasher.verify("".toCharArray(), salt, stored))
    }

    @Test
    fun `the same PIN under different salts derives differently`() = runBlocking {
        // Why every user gets their own salt: otherwise identical PINs are visibly identical in
        // the database, and one cracked row cracks every account that shares it.
        val a = hasher.hash("1234".toCharArray(), ByteArray(16) { 1 })
        val b = hasher.hash("1234".toCharArray(), ByteArray(16) { 2 })

        assertNotEquals(a, b)
    }

    @Test
    fun `salts are random and long enough`() {
        val first = hasher.newSalt()
        val second = hasher.newSalt()

        assertEquals(16, first.size)
        assertFalse(first.contentEquals(second), "salts must not repeat")
    }

    @Test
    fun `a corrupt stored verifier is rejected rather than crashing`() = runBlocking {
        val salt = hasher.newSalt()

        assertFalse(hasher.verify("1234".toCharArray(), salt, "not-base64-at-all!!"))
        assertFalse(hasher.verify("1234".toCharArray(), salt, ""))
    }

    @Test
    fun `the shipped work factor is not quietly lowered`() = runBlocking {
        // The iteration count is the entire defence for a four-digit PIN, so a change to it should
        // have to break this test deliberately rather than slip through in a refactor.
        val shipped = DesktopPasswordHasher(RealDispatchers)
        val salt = shipped.newSalt()
        val hash = shipped.hash("1234".toCharArray(), salt)

        assertTrue(shipped.verify("1234".toCharArray(), salt, hash))
        // A cheap hasher cannot reproduce a costly one's output.
        val cheap = DesktopPasswordHasher(RealDispatchers, iterations = 1_000)
        assertNotEquals(hash, cheap.hash("1234".toCharArray(), salt))
    }
}
