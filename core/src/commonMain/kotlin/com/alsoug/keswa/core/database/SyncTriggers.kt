package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.sync.SYNC_TABLES
import com.alsoug.keswa.core.sync.SyncKind
import com.alsoug.keswa.core.sync.SyncTable
import com.alsoug.keswa.core.sync.SyncTrigger

/**
 * The DDL that fills the outbox, generated from the one registry in `core/sync/SyncTables.kt`.
 *
 * Room does not manage triggers, which means they have to be created in **two** places — the v8
 * migration for a shop that upgrades, and `onCreate` for a machine installing fresh — and a
 * mismatch between the two is invisible: the app works, and syncs nothing. Generating both from
 * one list is what makes that impossible, and `SyncTriggersTest` asserts the pair stays matched.
 *
 * Every trigger carries the same guard. While a pull is being applied, `sync_control.applying` is
 * set and nothing is enqueued — otherwise a row arriving from the server is immediately queued to
 * be sent back to it, and two devices pass the same row between them for ever.
 */
private const val NOT_APPLYING = "(SELECT value FROM sync_control WHERE `key` = 'applying') IS NULL"

private const val NOW_MILLIS = "CAST(strftime('%s','now') AS INTEGER) * 1000"

/**
 * Plain `INSERT`, never `INSERT OR REPLACE`.
 *
 * SQLite discards a trigger body's conflict algorithm and applies the one from the statement that
 * fired the trigger, so `OR REPLACE` here silently became Room's `ABORT` — and posting a stock
 * receipt, which enqueues a header already queued by its own update, failed outright. Repeated
 * entries are coalesced by the push instead.
 */
private fun enqueue(table: String, idExpression: String): String =
    "INSERT INTO sync_outbox (tableName, rowId, enqueuedAt) " +
        "VALUES ('$table', $idExpression, $NOW_MILLIS);"

private fun enqueueChildren(child: SyncTable.Child): String =
    "INSERT INTO sync_outbox (tableName, rowId, enqueuedAt) " +
        "SELECT '${child.table}', `${child.idColumn}`, $NOW_MILLIS FROM ${child.table} " +
        "WHERE ${child.parentColumn} = NEW.id;"

private fun onWrite(table: SyncTable, event: String, suffix: String): String =
    "CREATE TRIGGER IF NOT EXISTS sync_out_${table.name}_$suffix " +
        "AFTER $event ON ${table.name} " +
        "WHEN $NOT_APPLYING " +
        "BEGIN ${enqueue(table.name, "NEW.`${table.idColumn}`")} END"

/**
 * Records can genuinely be deleted — an assortment pack drops a colour — and the outbox is
 * already the right place to say so. The row is gone by the time the push reads it, and a row
 * that the outbox names but the table no longer holds *is* the tombstone, derived rather than
 * stored. Events and documents get no such trigger: nothing in the shop's history is deletable,
 * and the append-only guards exist to keep it that way.
 */
private fun onDelete(table: SyncTable): String =
    "CREATE TRIGGER IF NOT EXISTS sync_out_${table.name}_del " +
        "AFTER DELETE ON ${table.name} " +
        "WHEN $NOT_APPLYING " +
        "BEGIN ${enqueue(table.name, "OLD.`${table.idColumn}`")} END"

/**
 * A draft is one person's unfinished work and is the other place the schema genuinely deletes
 * rows. Nothing about a receipt or a count leaves this device until it is posted, at which point
 * the header and every line go together.
 */
private fun onPost(table: SyncTable): String =
    "CREATE TRIGGER IF NOT EXISTS sync_out_${table.name}_post " +
        "AFTER UPDATE OF status ON ${table.name} " +
        "WHEN NEW.status = 'POSTED' AND OLD.status <> 'POSTED' AND $NOT_APPLYING " +
        "BEGIN ${enqueue(table.name, "NEW.`${table.idColumn}`")} " +
        "${table.posts.joinToString(" ") { enqueueChildren(it) }} END"

/** Every trigger, in a stable order, for the migration and for `onCreate` alike. */
val SYNC_TRIGGERS: List<String> = SYNC_TABLES.flatMap { table ->
    when (table.trigger) {
        SyncTrigger.ON_WRITE -> listOfNotNull(
            onWrite(table, "INSERT", "ins"),
            onWrite(table, "UPDATE", "upd"),
            onDelete(table).takeIf { table.kind == SyncKind.RECORD },
        )
        SyncTrigger.ON_POST -> listOf(onPost(table))
        SyncTrigger.WITH_PARENT -> emptyList()
    }
}

/** Used by the migration test, which has to prove the upgrade path installed them. */
val SYNC_TRIGGER_NAMES: List<String> = SYNC_TABLES.flatMap { table ->
    when (table.trigger) {
        SyncTrigger.ON_WRITE -> listOfNotNull(
            "sync_out_${table.name}_ins",
            "sync_out_${table.name}_upd",
            "sync_out_${table.name}_del".takeIf { table.kind == SyncKind.RECORD },
        )
        SyncTrigger.ON_POST -> listOf("sync_out_${table.name}_post")
        SyncTrigger.WITH_PARENT -> emptyList()
    }
}
