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
        .addCallback(ForeignKeyEnforcement)
        .build()

/**
 * SQLite disables foreign keys per connection by default, which would silently turn every
 * `onDelete = RESTRICT` in the schema into no protection at all.
 */
private object ForeignKeyEnforcement : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
    }
}
