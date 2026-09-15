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
 * 2 → 3: adds `app_user`.
 *
 * Additive again, and deliberately so — the shop's trading history predates its user accounts, and
 * an upgrade must never put that at risk to introduce a login. DDL copied verbatim from Room's
 * exported `3.json`, index included: Room validates the schema's identity hash on open, so a
 * hand-written approximation fails at startup rather than at review.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_user` (" +
                "`id` TEXT NOT NULL, `username` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                "`displayNameAr` TEXT NOT NULL, `role` TEXT NOT NULL, `secretHash` TEXT NOT NULL, " +
                "`secretSalt` TEXT NOT NULL, `secretKind` TEXT NOT NULL, `isActive` INTEGER NOT NULL, " +
                "`mustChangeSecret` INTEGER NOT NULL, `failedAttempts` INTEGER NOT NULL, " +
                "`lockedUntil` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_app_user_username` ON `app_user` (`username`)",
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
    MIGRATION_2_3,
)
