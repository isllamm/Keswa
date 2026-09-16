package com.alsoug.keswa.core.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LockoutPolicyTest {

    private val now = 1_000_000L
    private val minute = 60_000L

    @Test
    fun `below the threshold nothing is locked`() {
        (0 until LockoutPolicy.MAX_ATTEMPTS).forEach { attempts ->
            assertNull(LockoutPolicy.lockUntil(attempts, now), "locked too early at $attempts")
        }
    }

    @Test
    fun `the fifth failure locks for five minutes`() {
        assertEquals(now + 5 * minute, LockoutPolicy.lockUntil(5, now))
    }

    @Test
    fun `each further run of failures doubles the wait`() {
        assertEquals(now + 10 * minute, LockoutPolicy.lockUntil(10, now))
        assertEquals(now + 20 * minute, LockoutPolicy.lockUntil(15, now))
        assertEquals(now + 40 * minute, LockoutPolicy.lockUntil(20, now))
    }

    @Test
    fun `the wait is capped so an account is never bricked by a keypad masher`() {
        assertEquals(now + 60 * minute, LockoutPolicy.lockUntil(25, now))
        assertEquals(now + 60 * minute, LockoutPolicy.lockUntil(500, now))
    }
}
