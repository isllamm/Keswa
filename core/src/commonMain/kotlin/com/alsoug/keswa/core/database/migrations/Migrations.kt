package com.alsoug.keswa.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 1 → 2: adds `app_setting`.
 *
 * Purely additive, so nothing existing is touched — which is the point. The DDL is copied verbatim
 * from Room's exported `2.json` rather than written by hand: Room validates the schema's identity
 * hash on open, and a column declared even slightly differently fails at startup.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_setting` " +
                "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}

/**
 * Every migration this database has ever shipped, in order.
 *
 * KD-002: the local database is the source of truth until sync arrives, so
 * `fallbackToDestructiveMigration` is a blocker and each version bump adds a hand-written entry
 * here plus a test that seeds the previous schema, migrates, and asserts the rows survived intact.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
)
