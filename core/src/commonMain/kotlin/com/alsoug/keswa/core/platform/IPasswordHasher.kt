package com.alsoug.keswa.core.platform

/**
 * Derives a verifier from a PIN or password.
 *
 * A platform bridge (ADR-018) because there is no key-derivation function in Kotlin `commonMain`,
 * and writing one would be the worst possible place to be original.
 *
 * Three rules, all 🔴 blockers:
 *
 * 1. **Never a plain hash.** `SHA-256(pin)` over four digits is brute-forced in milliseconds. The
 *    iteration count is the entire defence for a short credential.
 * 2. **`CharArray`, not `String`.** Callers zero it after use; a `String` sits in the heap until
 *    garbage collection decides otherwise.
 * 3. **Never log a credential**, at any level, masked or not.
 */
interface IPasswordHasher {

    /** Returns an opaque, storable verifier — never the credential. */
    suspend fun hash(secret: CharArray, salt: ByteArray): String

    /**
     * Constant-time comparison against a stored verifier.
     *
     * Constant-time because an early-exit comparison leaks how much of the value matched, which
     * over enough attempts is a credential oracle.
     */
    suspend fun verify(secret: CharArray, salt: ByteArray, expected: String): Boolean

    /** A fresh random salt, one per user. */
    fun newSalt(): ByteArray
}
