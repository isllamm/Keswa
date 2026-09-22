package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Rows this device has written and the server has not yet seen.
 *
 * Filled by SQLite triggers rather than by repository code, which is the whole point: no feature
 * knows sync exists, and none of the ledger tables is ever updated to carry a `syncedAt` flag.
 * Marking a stock movement as synced would be an `UPDATE` on an append-only table, and a rule with
 * one exception is not a rule.
 *
 * **Not unique on `(tableName, rowId)`, and that is a scar.** The first version was, so the
 * trigger's `INSERT OR REPLACE` would coalesce repeated edits into one row to send. It does not
 * work: SQLite ignores a trigger body's conflict algorithm and uses the *outer* statement's
 * instead, so `OR REPLACE` became Room's `ABORT` and posting a stock receipt failed outright. The
 * index is gone and the push coalesces in Kotlin, where the rule is visible.
 *
 * Duplicates are also what makes a row edited *during* a push survive it: the second entry gets a
 * higher `seq` than the watermark being drained, so the edit is still there to send.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["tableName", "rowId"])],
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val tableName: String,
    val rowId: String,
    val enqueuedAt: Long,
)

/**
 * How far through the server's log this device has read.
 *
 * One row, one cursor. A sequence the server assigns rather than a timestamp this machine reads:
 * a shop PC whose clock steps backwards would otherwise open a window of rows that no pull ever
 * returns, and nothing would say so.
 */
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val id: String,
    val lastSeq: Long,
)

/**
 * Flags the triggers read, chiefly `applying`.
 *
 * While a pull is being written, the triggers must not enqueue what they see — a row arriving from
 * the server would otherwise be sent straight back to it, for ever. Cleared on every database open
 * as well as at the end of an apply, because a crash half-way through would otherwise leave this
 * device silently no longer syncing anything it does.
 */
@Entity(tableName = "sync_control")
data class SyncControlEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * A record that lost.
 *
 * Last-write-wins is the only honest answer for freely-editable configuration, but a shop that
 * finds yesterday's price back again needs to be able to see that it was overwritten and by which
 * machine. The losing value is kept rather than dropped.
 */
@Entity(
    tableName = "sync_superseded",
    indices = [Index(value = ["tableName", "rowId"])],
)
data class SyncSupersededEntity(
    @PrimaryKey val id: String,
    val tableName: String,
    val rowId: String,
    val previousJson: String,
    val supersededAt: Long,
    val byDeviceId: String,
)
