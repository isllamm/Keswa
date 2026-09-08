package keswa.app.desktop.platform

import keswa.domain.auth.HashedPin
import keswa.domain.auth.PasswordHasher
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id via Bouncy Castle — pure JVM, no native library to package alongside the app. See
 * ADR-008: this protects against casual PIN guessing, not a determined attacker with disk access.
 */
class JvmArgon2Hasher : PasswordHasher {

    override fun hash(plainPin: String): HashedPin {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return HashedPin(
            hash = Base64.getEncoder().encodeToString(deriveHash(plainPin, salt)),
            salt = Base64.getEncoder().encodeToString(salt),
        )
    }

    override fun verify(plainPin: String, hash: String, salt: String): Boolean {
        val saltBytes = Base64.getDecoder().decode(salt)
        val expected = Base64.getDecoder().decode(hash)
        val actual = deriveHash(plainPin, saltBytes)
        return expected.contentEquals(actual)
    }

    private fun deriveHash(plainPin: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(1)
            .withMemoryAsKB(MEMORY_KB)
            .withIterations(ITERATIONS)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val result = ByteArray(HASH_BYTES)
        generator.generateBytes(plainPin.toByteArray(Charsets.UTF_8), result)
        return result
    }

    private companion object {
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
        const val MEMORY_KB = 19_456 // ~19 MB — OWASP baseline for Argon2id
        const val ITERATIONS = 2
    }
}
