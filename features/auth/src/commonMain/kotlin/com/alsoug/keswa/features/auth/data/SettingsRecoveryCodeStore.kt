package com.alsoug.keswa.features.auth.data

import com.alsoug.keswa.core.database.dao.SettingDao
import com.alsoug.keswa.core.database.entities.AppSettingEntity
import com.alsoug.keswa.core.platform.IPasswordHasher
import com.alsoug.keswa.features.auth.domain.usecase.RecoveryCodeStore

/**
 * Keeps the recovery code's verifier beside the other settings.
 *
 * Only the derived value is stored, exactly as for a password — a recovery code that could be read
 * out of the database would be worse than none, since it bypasses the password entirely.
 */
class SettingsRecoveryCodeStore(
    private val dao: SettingDao,
) : RecoveryCodeStore {

    override suspend fun store(hash: String, salt: String) {
        dao.putAll(
            listOf(
                AppSettingEntity(HASH_KEY, hash),
                AppSettingEntity(SALT_KEY, salt),
            ),
        )
    }

    override suspend fun read(): Pair<String, String>? {
        val hash = dao.get(HASH_KEY) ?: return null
        val salt = dao.get(SALT_KEY) ?: return null
        return hash to salt
    }

    private companion object {
        const val HASH_KEY = "recovery.code.hash"
        const val SALT_KEY = "recovery.code.salt"
    }
}

/**
 * Generates the code shown once at setup.
 *
 * Draws its randomness from [IPasswordHasher.newSalt], which is already backed by a cryptographic
 * source — a recovery code bypasses the password, so it must not come from a predictable generator.
 *
 * Encoded without `I`, `O`, `0` or `1`, because this gets written on paper and read back later.
 */
class RecoveryCodeGenerator(private val hasher: IPasswordHasher) {

    fun newCode(): String {
        val bytes = hasher.newSalt()
        val body = bytes.take(GROUPS * GROUP_SIZE / 2).flatMap { byte ->
            val value = byte.toInt() and 0xFF
            listOf(ALPHABET[value shr 4], ALPHABET[(value and 0xF) + 16 - 16])
        }
        return body.chunked(GROUP_SIZE) { it.joinToString("") }.joinToString("-", prefix = "KSW-")
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHJKLMNPQR"
        const val GROUPS = 3
        const val GROUP_SIZE = 4
    }
}
