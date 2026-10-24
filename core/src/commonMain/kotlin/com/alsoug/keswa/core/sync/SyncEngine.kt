package com.alsoug.keswa.core.sync

import androidx.room.useWriterConnection
import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.dao.SyncDao
import com.alsoug.keswa.core.database.entities.SyncCursorEntity
import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.LogLevel
import com.alsoug.keswa.core.platform.ISyncTokenStore

sealed interface SyncOutcome {

    /** No server has been configured, which is the normal state of a shop with one till. */
    data object NotConfigured : SyncOutcome

    /** Configured, but this device has not enrolled yet. */
    data object NotEnrolled : SyncOutcome

    data class Completed(
        val pushed: Int,
        val pulled: Int,
        val deferred: Int,
        val superseded: Int,
        val cursor: Long,
        val hasMore: Boolean,
    ) : SyncOutcome
}

/**
 * One pass: send what this device has done, then take in what the others have.
 *
 * Push first on purpose. A device that has been offline holds the only copy of what it sold, and
 * getting that to the server is worth more than learning about a price change a moment sooner.
 */
class SyncEngine(
    private val database: KeswaDatabase,
    private val api: ISyncApi,
    private val applier: LogApplier,
    private val settings: SyncSettings,
    private val tokens: ISyncTokenStore,
    private val platform: IPlatformProvider,
) {

    suspend fun enrol(baseUrl: String, code: String): Result<Unit> = runCatchingCancellable {
        val response = api.enrol(
            baseUrl = baseUrl,
            request = EnrolRequest(
                code = code,
                deviceId = settings.deviceId(),
                deviceName = settings.deviceName(),
            ),
        )
        tokens.store(response.token)
        settings.setServerUrl(baseUrl)
        settings.setOrdinal(response.ordinal)
        platform.log(LogLevel.INFO, TAG, "enrolled as ordinal ${response.ordinal}")
    }

    suspend fun syncOnce(batchSize: Int = BATCH): Result<SyncOutcome> = runCatchingCancellable {
        val baseUrl = settings.serverUrl() ?: return@runCatchingCancellable SyncOutcome.NotConfigured
        val token = tokens.read() ?: return@runCatchingCancellable SyncOutcome.NotEnrolled

        val pushed = push(baseUrl, token, batchSize)
        val pull = pull(baseUrl, token, batchSize)

        SyncOutcome.Completed(
            pushed = pushed,
            pulled = pull.applied,
            deferred = pull.deferred,
            superseded = pull.superseded,
            cursor = pull.cursor,
            hasMore = pull.hasMore,
        )
    }

    private suspend fun push(baseUrl: String, token: String, batchSize: Int): Int {
        val pending = database.syncDao().pending(batchSize)
        if (pending.isEmpty()) return 0

        // A price edited five times before the next sync is one row to send, not five: the row
        // carries its current state either way. Coalesced here rather than by a unique index on the
        // outbox, because SQLite will not let a trigger choose its own conflict algorithm.
        val rows = database.useWriterConnection { transactor ->
            pending
                .distinctBy { it.tableName to it.rowId }
                .mapNotNull { entry ->
                    val table = syncTable(entry.tableName) ?: return@mapNotNull null
                    // Gone from the table but still named by the outbox: a deleted record, sent as
                    // a row with no columns.
                    RowCodec.read(transactor, table, entry.rowId)
                        ?: SyncRow(table = entry.tableName, id = entry.rowId, columns = emptyMap())
                }
        }

        api.push(baseUrl, token, rows)

        // Bounded by the seq that was read, not by clearing the table: a row edited *while* the
        // push was in flight has been enqueued again with a higher seq, and must survive.
        database.syncDao().drainThrough(pending.last().seq)
        return rows.size
    }

    private data class PullResult(
        val applied: Int,
        val deferred: Int,
        val superseded: Int,
        val cursor: Long,
        val hasMore: Boolean,
    )

    private suspend fun pull(baseUrl: String, token: String, batchSize: Int): PullResult {
        val from = database.syncDao().cursor() ?: 0L
        val response = api.pull(baseUrl, token, from, batchSize)

        // Its own rows come back in the log; applying them would be harmless but pointless, and
        // skipping them keeps the deferred set honest.
        val deviceId = settings.deviceId()
        val incoming = response.rows.filter { it.deviceId != deviceId }

        val result = applier.apply(incoming, respectLocalEdits = true)

        // Stop at the earliest row that could not be applied yet, rather than stepping over it.
        // Re-reading a handful of rows next pass costs nothing — they apply idempotently — whereas
        // a cursor that steps past a deferred row has silently lost it for good.
        val cursor = result.deferred.minOfOrNull { it.seq - 1 } ?: response.nextSeq
        if (cursor > from) {
            database.syncDao().setCursor(SyncCursorEntity(SyncDao.CURSOR_ID, cursor))
        }

        if (result.deferred.isNotEmpty()) {
            platform.log(
                LogLevel.INFO,
                TAG,
                "${result.deferred.size} rows waiting on rows that have not arrived yet",
            )
        }

        return PullResult(
            applied = result.applied,
            deferred = result.deferred.size,
            superseded = result.superseded,
            cursor = cursor,
            hasMore = response.hasMore,
        )
    }

    private companion object {
        const val TAG = "Sync"

        /**
         * Big enough that a week offline drains in a few passes, small enough that a slow
         * connection is not asked to carry one enormous request it cannot finish.
         */
        const val BATCH = 500
    }
}
