package com.alsoug.keswa.core.platform

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.withContext

/**
 * PBKDF2-HMAC-SHA256, from the JDK.
 *
 * Deliberately slow. That cost is the point: it is what stands between a stolen database file and
 * every PIN in it. Runs off the main thread because at this iteration count it is measured in
 * hundreds of milliseconds, which is exactly as it should be.
 */
class DesktopPasswordHasher(
    private val dispatchers: DispatcherProvider,
    private val iterations: Int = DEFAULT_ITERATIONS,
) : IPasswordHasher {

    override suspend fun hash(secret: CharArray, salt: ByteArray): String =
        withContext(dispatchers.default) { encode(derive(secret, salt)) }

    override suspend fun verify(secret: CharArray, salt: ByteArray, expected: String): Boolean =
        withContext(dispatchers.default) {
            val candidate = derive(secret, salt)
            val stored = runCatching { decode(expected) }.getOrNull() ?: return@withContext false
            constantTimeEquals(candidate, stored)
        }

    override fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    private fun derive(secret: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            // The spec keeps its own copy of the characters; clear it as soon as we are done.
            spec.clearPassword()
        }
    }

    /** No early exit: the loop always reads every byte, whatever it finds. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var difference = 0
        for (index in a.indices) difference = difference or (a[index].toInt() xor b[index].toInt())
        return difference == 0
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    private val random = SecureRandom()

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"

        /**
         * A four-digit PIN has ten thousand combinations, so the work factor is what makes a stolen
         * database expensive rather than instant. Raise it over time; never lower it.
         */
        const val DEFAULT_ITERATIONS = 210_000
        const val KEY_BITS = 256
        const val SALT_BYTES = 16
    }
}
