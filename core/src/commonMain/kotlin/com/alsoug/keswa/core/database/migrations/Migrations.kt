package com.alsoug.keswa.core.database.migrations

import androidx.room.migration.Migration

/**
 * Every migration this database has ever shipped, in order.
 *
 * KD-002: the local database is the source of truth, so `fallbackToDestructiveMigration` is a
 * blocker and each version bump adds a hand-written entry here plus a test that seeds the previous
 * schema, migrates, and asserts the rows survived intact.
 *
 * Empty at version 1 — there is nothing before it.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
