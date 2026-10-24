package com.alsoug.keswa.core.sync

import androidx.room.PooledConnection
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.alsoug.keswa.core.coroutines.rethrowIfCancellation
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.entities.SyncSupersededEntity
import com.alsoug.keswa.core.domain.IdGenerator
import kotlinx.serialization.json.Json

/**
 * Writes rows from the log into a Keswa database.
 *
 * Used by both sides and deliberately so: on the till it applies what was pulled, and on the server
 * it materialises what was pushed. The same rules have to hold in both directions, and one
 * implementation is the only way to be sure they do.
 *
 * **Order does not have to be right.** A row whose parent has not arrived fails its foreign key,
 * is put back, and is tried again on the next pass; the loop stops when a pass achieves nothing.
 * That is what makes partial and out-of-order delivery — the normal case on a shop's wifi — correct
 * by construction rather than by care, and it is why the push side does not need to know that a
 * sale is a header plus lines plus payments plus movements.
 */
class LogApplier(
    private val database: KeswaDatabase,
    private val ids: IdGenerator,
    private val now: () -> Long,
    private val json: Json = Json,
) {

    data class Result(
        val applied: Int,
        /** Rows whose parents had still not arrived. They are kept and retried, never dropped. */
        val deferred: List<LoggedRow>,
        val superseded: Int,
    )

    suspend fun apply(rows: List<LoggedRow>, respectLocalEdits: Boolean): Result {
        if (rows.isEmpty()) return Result(0, emptyList(), 0)

        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                database.syncDao().beginApplying()
                try {
                    settle(this, rows, respectLocalEdits)
                } finally {
                    // Also cleared on every database open, so a crash in here costs one session of
                    // enqueuing rather than this device silently never syncing its own work again.
                    database.syncDao().endApplying()
                }
            }
        }
    }

    private suspend fun settle(
        connection: PooledConnection,
        rows: List<LoggedRow>,
        respectLocalEdits: Boolean,
    ): Result {
        var remaining = rows.sortedWith(compareBy({ SYNC_APPLY_ORDER[it.row.table] ?: Int.MAX_VALUE }, { it.seq }))
        var applied = 0
        var superseded = 0

        while (remaining.isNotEmpty()) {
            val stillDeferred = mutableListOf<LoggedRow>()
            var progressed = false

            for (logged in remaining) {
                val table = syncTable(logged.row.table)
                if (table == null) {
                    // A table this version does not know about. Dropping it would lose a shop's
                    // data on a mixed-version estate, so it waits for an upgrade instead.
                    stillDeferred += logged
                    continue
                }

                if (respectLocalEdits &&
                    table.kind == SyncKind.RECORD &&
                    database.syncDao().isPending(table.name, logged.row.id)
                ) {
                    progressed = true
                    continue
                }

                val previous = if (table.kind == SyncKind.RECORD) {
                    RowCodec.read(connection, table, logged.row.id)
                } else {
                    null
                }

                // An empty column map is a row the sending device no longer has: the outbox
                // named it, the table had already dropped it, and that is what a delete looks like
                // when nothing stores tombstones.
                val outcome = runCatching {
                    if (logged.row.columns.isEmpty()) {
                        RowCodec.delete(connection, table, logged.row.id)
                    } else {
                        RowCodec.write(connection, logged.row, table)
                    }
                }.onFailure { it.rethrowIfCancellation() }

                when {
                    outcome.isFailure -> {
                        val failure = outcome.exceptionOrNull()
                        if (isUniqueClash(failure)) {
                            // Two devices created the same SKU while both were offline. It cannot
                            // be designed away, and deferring it for ever would stall this device's
                            // cursor forever over an event that happens once a year.
                            if (resolveUniqueClash(connection, table, logged, failure)) superseded++
                            progressed = true
                        } else {
                            stillDeferred += logged
                        }
                    }
                    outcome.getOrDefault(false) -> {
                        applied++
                        progressed = true
                        if (previous != null && previous != logged.row) {
                            recordSuperseded(previous, logged)
                            superseded++
                        }
                    }
                    // Applied cleanly and changed nothing: an event already held, or a document
                    // that was already further along. Settled either way.
                    else -> progressed = true
                }
            }

            if (!progressed) return Result(applied, stillDeferred, superseded)
            remaining = stillDeferred
        }

        return Result(applied, emptyList(), superseded)
    }

    /**
     * Settles a clash on a unique index other than the primary key — `variant.sku`,
     * `app_user.username`, `variant(productId, colourId)`.
     *
     * **The smaller id wins.** Arbitrary, and deliberately so: it is the only rule that needs no
     * clock, no sequence and no conversation, so two devices reach the same answer without either
     * knowing what the other decided. Anything based on arrival order diverges, because each device
     * meets the two rows in the opposite order.
     *
     * The loser is recorded rather than dropped, because a shop that finds one of its two SKUs gone
     * is owed an explanation.
     */
    private suspend fun resolveUniqueClash(
        connection: PooledConnection,
        table: SyncTable,
        logged: LoggedRow,
        failure: Throwable?,
    ): Boolean {
        val clashing = clashingColumns(failure)
            .mapNotNull { column -> logged.row.columns[column]?.let { column to it } }
            .toMap()
        val held = RowCodec.findBy(connection, table, clashing) ?: return false
        if (held.id == logged.row.id) return false

        if (held.id <= logged.row.id) {
            // What is already here wins; the arriving row is the one that goes on the record.
            recordSuperseded(logged.row, logged)
            return true
        }

        RowCodec.delete(connection, table, held.id)
        RowCodec.write(connection, logged.row, table)
        recordSuperseded(held, logged)
        return true
    }

    private fun isUniqueClash(failure: Throwable?): Boolean =
        failure?.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true

    /** SQLite names them: `UNIQUE constraint failed: variant.sku, variant.productId`. */
    private fun clashingColumns(failure: Throwable?): List<String> =
        failure?.message
            ?.substringAfter("UNIQUE constraint failed:", "")
            ?.substringBefore(')')
            ?.split(',')
            ?.mapNotNull { it.trim().substringAfter('.', "").ifEmpty { null } }
            .orEmpty()

    private suspend fun recordSuperseded(previous: SyncRow, logged: LoggedRow) {
        database.syncDao().recordSuperseded(
            SyncSupersededEntity(
                id = ids.newId(),
                tableName = previous.table,
                rowId = previous.id,
                previousJson = json.encodeToString(previous),
                supersededAt = now(),
                byDeviceId = logged.deviceId,
            ),
        )
    }
}
