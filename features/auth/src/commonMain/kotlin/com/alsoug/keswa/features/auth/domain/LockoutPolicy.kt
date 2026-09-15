package com.alsoug.keswa.features.auth.domain

/**
 * How long an account is locked after repeated failures.
 *
 * A four-digit PIN has ten thousand combinations, so the key-derivation cost alone is not enough —
 * someone with the machine and patience gets there. Lockout is what makes guessing impractical, and
 * escalation is what stops a slow, spread-out attack.
 *
 * Pure and separate so the policy can be reasoned about and changed without touching sign-in.
 */
object LockoutPolicy {

    const val MAX_ATTEMPTS = 5

    private const val BASE_LOCK_MILLIS = 5 * 60 * 1000L
    private const val MAX_LOCK_MILLIS = 60 * 60 * 1000L

    /**
     * Returns when the account unlocks, or null if [attempts] has not reached the threshold.
     *
     * Doubles every [MAX_ATTEMPTS] failures — 5 minutes, then 10, then 20 — capped at an hour so
     * an account is never permanently unusable by someone simply mashing a keypad.
     */
    fun lockUntil(attempts: Int, nowMillis: Long): Long? {
        if (attempts < MAX_ATTEMPTS) return null
        val steps = attempts / MAX_ATTEMPTS
        var duration = BASE_LOCK_MILLIS
        repeat(steps - 1) { duration = (duration * 2).coerceAtMost(MAX_LOCK_MILLIS) }
        return nowMillis + duration.coerceAtMost(MAX_LOCK_MILLIS)
    }
}
