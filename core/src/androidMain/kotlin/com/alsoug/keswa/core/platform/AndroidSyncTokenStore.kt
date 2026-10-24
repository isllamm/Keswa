package com.alsoug.keswa.core.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.withContext

/**
 * The device token, sealed with a key the Keystore will not hand back.
 *
 * KD-007 gives Android the stronger treatment and desktop the weaker one, which is not
 * inconsistency: the handheld is the device that gets left on a counter, and the Keystore is
 * already there and costs nothing. The ciphertext lives in preferences; the key never leaves
 * hardware.
 */
@OptIn(ExperimentalEncodingApi::class)
class AndroidSyncTokenStore(
    context: Context,
    private val dispatchers: DispatcherProvider,
) : ISyncTokenStore {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun store(token: String) = withContext(dispatchers.io) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(token.encodeToByteArray())
        preferences.edit()
            .putString(NONCE, Base64.encode(cipher.iv))
            .putString(TOKEN, Base64.encode(sealed))
            .apply()
    }

    override suspend fun read(): String? = withContext(dispatchers.io) {
        val nonce = preferences.getString(NONCE, null) ?: return@withContext null
        val sealed = preferences.getString(TOKEN, null) ?: return@withContext null
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, Base64.decode(nonce)))
            }
            cipher.doFinal(Base64.decode(sealed)).decodeToString()
        }.getOrNull()
    }

    override suspend fun clear() = withContext(dispatchers.io) {
        preferences.edit().remove(NONCE).remove(TOKEN).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "keswa.sync.device"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES = "keswa.sync"
        const val NONCE = "nonce"
        const val TOKEN = "token"
        const val TAG_BITS = 128
    }
}
