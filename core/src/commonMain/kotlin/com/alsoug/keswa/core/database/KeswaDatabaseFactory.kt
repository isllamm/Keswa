package com.alsoug.keswa.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.database.migrations.ALL_MIGRATIONS

/**
 * Applies the shared configuration to a platform-specific builder.
 *
 * Mirrors `kmp_cashimobile`'s `getRoomDatabase(builder)` bridge, with one deliberate difference:
 * real migrations instead of `fallbackToDestructiveMigration` — see KD-002.
 */
fun getKeswaDatabase(
    builder: RoomDatabase.Builder<KeswaDatabase>,
    dispatchers: DispatcherProvider,
): KeswaDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(dispatchers.io)
        .addMigrations(*ALL_MIGRATIONS)
        .addCallback(DatabaseCallbacks)
        .build()

private object DatabaseCallbacks : RoomDatabase.Callback() {

    /**
     * Room builds tables and indices from the entity model, but it has no notion of a trigger — so
     * a machine installing fresh would have every sync table and nothing to fill them. The same
     * list runs here and in `MIGRATION_7_8`, because a fresh install that quietly syncs nothing
     * looks exactly like one that works.
     */
    override fun onCreate(connection: SQLiteConnection) {
        SYNC_TRIGGERS.forEach(connection::execSQL)
    }

    override fun onOpen(connection: SQLiteConnection) {
        // SQLite disables foreign keys per connection by default, which would silently turn every
        // `onDelete = RESTRICT` in the schema into no protection at all.
        connection.execSQL("PRAGMA foreign_keys = ON")

        // The triggers stand down while a pull is being written, so a row arriving from the server
        // is not immediately queued to be sent back. A crash mid-apply would otherwise leave the
        // flag set for good, and this device would go on working while silently recording nothing
        // it did. Clearing it on open bounds that to one session.
        connection.execSQL("DELETE FROM sync_control WHERE `key` = 'applying'")
    }
}
