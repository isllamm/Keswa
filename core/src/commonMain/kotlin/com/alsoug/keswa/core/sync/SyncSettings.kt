package com.alsoug.keswa.core.sync

import com.alsoug.keswa.core.database.dao.SettingDao
import com.alsoug.keswa.core.database.entities.AppSettingEntity
import com.alsoug.keswa.core.domain.IdGenerator

/**
 * This device's own answers: who it is, which server it talks to, and which block of receipt
 * numbers it sells from.
 *
 * Deliberately **not** part of `ShopSettings`. Those are the shop's, the same on every machine and
 * a candidate for syncing one day; these are this machine's and must never leave it — pushing one
 * till's device id onto another would be indistinguishable from the two tills becoming one.
 */
class SyncSettings(
    private val dao: SettingDao,
    private val ids: IdGenerator,
) {

    /**
     * Created on first read and never changed.
     *
     * Generated here rather than handed out at enrolment, so a till has an identity before it has
     * met a server — which it needs, because the receipt-number block is keyed on it.
     */
    suspend fun deviceId(): String =
        dao.get(DEVICE_ID) ?: ids.newId().also { dao.put(AppSettingEntity(DEVICE_ID, it)) }

    suspend fun deviceName(): String = dao.get(DEVICE_NAME) ?: DEFAULT_NAME

    suspend fun setDeviceName(name: String) = dao.put(AppSettingEntity(DEVICE_NAME, name))

    suspend fun serverUrl(): String? = dao.get(SERVER_URL)?.trim()?.ifEmpty { null }

    suspend fun setServerUrl(url: String) = dao.put(AppSettingEntity(SERVER_URL, url.trim()))

    /**
     * Zero until this device enrols, which is exactly right: a shop with one till has always been
     * ordinal 0, and its receipt numbering carries on across enrolment without a jump.
     */
    suspend fun ordinal(): Int = dao.get(ORDINAL)?.toIntOrNull() ?: 0

    suspend fun setOrdinal(ordinal: Int) = dao.put(AppSettingEntity(ORDINAL, ordinal.toString()))

    suspend fun isEnrolled(): Boolean = dao.get(ORDINAL) != null

    /** The million this device sells from. See 9i for why this is a block and not a column. */
    suspend fun receiptBlock(): LongRange {
        val start = ordinal().toLong() * BLOCK_SIZE
        return start until (start + BLOCK_SIZE)
    }

    private companion object {
        const val DEVICE_ID = "sync.deviceId"
        const val DEVICE_NAME = "sync.deviceName"
        const val SERVER_URL = "sync.serverUrl"
        const val ORDINAL = "sync.ordinal"
        const val DEFAULT_NAME = "Till"

        /** A million per device is about four centuries of a busy shop. */
        const val BLOCK_SIZE = 1_000_000L
    }
}
