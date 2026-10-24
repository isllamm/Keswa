package com.alsoug.keswa.core.database

import androidx.room.useWriterConnection
import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.sync.LogApplier
import com.alsoug.keswa.core.sync.LoggedRow
import com.alsoug.keswa.core.sync.RowCodec
import com.alsoug.keswa.core.sync.SyncRow
import com.alsoug.keswa.core.sync.syncTable

/**
 * Two tills and the log between them, with the network taken out.
 *
 * Small enough to read in one sitting and faithful in the ways that matter: rows leave through the
 * outbox exactly as the engine sends them, the log assigns the sequence, and the applier is the
 * production one. What it leaves out is HTTP, which has no bearing on whether two devices agree.
 */
class SyncPeer(val deviceId: String) {

    val database: KeswaDatabase = createTestDatabase()
    private val applier = LogApplier(database, UuidIdGenerator(), { CLOCK })

    var cursor: Long = 0
        private set

    /** What the triggers have queued, in order, as rows — draining the outbox as the push does. */
    suspend fun drainOutbox(): List<SyncRow> {
        val pending = database.syncDao().pending(limit = 1_000)
        if (pending.isEmpty()) return emptyList()

        val rows = database.useWriterConnection { transactor ->
            pending.distinctBy { it.tableName to it.rowId }.map { entry ->
                val table = requireNotNull(syncTable(entry.tableName)) { "unknown table ${entry.tableName}" }
                RowCodec.read(transactor, table, entry.rowId)
                    ?: SyncRow(entry.tableName, entry.rowId, emptyMap())
            }
        }
        database.syncDao().drainThrough(pending.last().seq)
        return rows
    }

    suspend fun apply(rows: List<LoggedRow>): LogApplier.Result =
        applier.apply(rows.filter { it.deviceId != deviceId }, respectLocalEdits = true)

    fun advanceTo(seq: Long) { cursor = seq }

    fun close() = database.close()

    companion object {
        const val CLOCK = 1_757_000_000_000L
    }
}

/** The server's log, reduced to the one thing that matters here: it assigns the order. */
class SyncHub {

    private val entries = mutableListOf<LoggedRow>()

    val size: Int get() = entries.size

    suspend fun collectFrom(peer: SyncPeer) {
        peer.drainOutbox().forEach { row ->
            entries += LoggedRow(seq = entries.size + 1L, deviceId = peer.deviceId, row = row)
        }
    }

    /** Everything the peer has not seen, applied, with the cursor moved as the engine moves it. */
    suspend fun deliverTo(peer: SyncPeer): LogApplier.Result {
        val window = entries.filter { it.seq > peer.cursor }
        val result = peer.apply(window)
        peer.advanceTo(result.deferred.minOfOrNull { it.seq - 1 } ?: (window.lastOrNull()?.seq ?: peer.cursor))
        return result
    }

    fun rows(): List<LoggedRow> = entries.toList()
}

/** Pushes both ways until nothing moves, which is what a quiet minute in a shop looks like. */
suspend fun settle(hub: SyncHub, vararg peers: SyncPeer) {
    repeat(peers.size + 2) {
        peers.forEach { hub.collectFrom(it) }
        peers.forEach { hub.deliverTo(it) }
    }
}
