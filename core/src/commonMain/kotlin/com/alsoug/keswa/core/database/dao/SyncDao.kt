package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.alsoug.keswa.core.database.entities.SyncCursorEntity
import com.alsoug.keswa.core.database.entities.SyncOutboxEntity
import com.alsoug.keswa.core.database.entities.SyncSupersededEntity

/**
 * The sync engine's own bookkeeping.
 *
 * Deletes are allowed here and only here: the outbox is a queue, not a ledger. A drained row is
 * not history being erased, it is a job that finished.
 */
@Dao
interface SyncDao {

    @Query("SELECT * FROM sync_outbox ORDER BY seq LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Long

    /**
     * Whether this device has an unsent change to the same row.
     *
     * If it has, an arriving record must not overwrite it: the local edit would be clobbered
     * and then pushed in its clobbered form, so the person who made it would watch it vanish
     * with nothing to show that it ever happened. Skipping lets the push send the local
     * version, which becomes the later entry in the log and therefore wins everywhere.
     */
    @Query("SELECT COUNT(*) > 0 FROM sync_outbox WHERE tableName = :tableName AND rowId = :rowId")
    suspend fun isPending(tableName: String, rowId: String): Boolean

    @Query("DELETE FROM sync_outbox WHERE seq <= :throughSeq AND tableName = :tableName AND rowId = :rowId")
    suspend fun drain(tableName: String, rowId: String, throughSeq: Long)

    /**
     * Bounded by `seq` rather than clearing the table, because a row edited *while* the push was in
     * flight has already been re-enqueued with a higher seq and must survive.
     */
    @Query("DELETE FROM sync_outbox WHERE seq <= :throughSeq")
    suspend fun drainThrough(throughSeq: Long)

    @Query("SELECT lastSeq FROM sync_cursor WHERE id = :id")
    suspend fun cursor(id: String = CURSOR_ID): Long?

    @Upsert
    suspend fun setCursor(cursor: SyncCursorEntity)

    @Insert
    suspend fun recordSuperseded(entry: SyncSupersededEntity)

    @Query("SELECT * FROM sync_superseded ORDER BY supersededAt DESC LIMIT :limit")
    suspend fun superseded(limit: Int = 100): List<SyncSupersededEntity>

    @Query("INSERT OR REPLACE INTO sync_control (`key`, value) VALUES ('applying', '1')")
    suspend fun beginApplying()

    @Query("DELETE FROM sync_control WHERE `key` = 'applying'")
    suspend fun endApplying()

    @Query("SELECT COUNT(*) > 0 FROM sync_control WHERE `key` = 'applying'")
    suspend fun isApplying(): Boolean

    companion object {
        const val CURSOR_ID: String = "default"
    }
}
