package com.alsoug.keswa.core.sync

import androidx.room.PooledConnection
import androidx.sqlite.SQLiteStatement

/**
 * Reads and writes any syncable row generically, at the SQLite value level.
 *
 * One implementation covers twenty-four tables and will cover whatever Phase 11 adds, because it
 * asks the database what the columns are rather than being told. The alternative — a mapper per
 * table — is four hundred lines whose failure mode is a column somebody forgot, which shows up as
 * a field that silently never syncs.
 */
object RowCodec {

    // SQLite's fundamental types, as getColumnType reports them.
    private const val SQLITE_INTEGER = 1
    private const val SQLITE_FLOAT = 2
    private const val SQLITE_TEXT = 3
    private const val SQLITE_BLOB = 4
    private const val SQLITE_NULL = 5

    suspend fun read(connection: PooledConnection, table: SyncTable, id: String): SyncRow? =
        connection.usePrepared(
            "SELECT * FROM `${table.name}` WHERE `${table.idColumn}` = ?",
        ) { statement ->
            statement.bindText(1, id)
            if (!statement.step()) return@usePrepared null
            SyncRow(
                table = table.name,
                id = id,
                columns = (0 until statement.getColumnCount()).associate { index ->
                    statement.getColumnName(index) to statement.valueAt(index)
                },
            )
        }

    /**
     * Applies a row, by the rule its pile earns.
     *
     * - An **event** is written if absent and otherwise left exactly as it is. It is immutable, so
     *   receiving it twice must not rewrite it.
     * - A **document** is written if absent, and otherwise updated only when the arriving row is
     *   further along than the stored one — a void beats a completed sale, in either arrival order
     *   and however many times it arrives.
     * - A **record** is written outright. Convergence comes from the log's order, which every
     *   device reads identically, so no timestamp comparison is needed and no two devices can end
     *   up disagreeing about which edit was last.
     *
     * Returns whether the row changed anything, which is how the caller knows a record was
     * superseded and worth recording.
     */
    suspend fun write(connection: PooledConnection, row: SyncRow, table: SyncTable): Boolean {
        val columns = row.columns.keys.toList()
        if (columns.isEmpty()) return false

        val names = columns.joinToString(", ") { "`$it`" }
        val placeholders = columns.joinToString(", ") { "?" }
        val assignments = columns
            .filter { it != table.idColumn }
            .joinToString(", ") { "`$it` = excluded.`$it`" }

        val onConflict = when (table.kind) {
            SyncKind.EVENT -> "DO NOTHING"
            SyncKind.DOCUMENT ->
                if (assignments.isEmpty() || table.progressWhen == null) "DO NOTHING"
                else "DO UPDATE SET $assignments WHERE ${table.progressWhen}"
            SyncKind.RECORD ->
                if (assignments.isEmpty()) "DO NOTHING" else "DO UPDATE SET $assignments"
        }

        // Upsert rather than INSERT OR REPLACE: REPLACE deletes the old row first, which would fire
        // `ON DELETE CASCADE` and take an assortment pack's lines with it.
        val sql = "INSERT INTO `${row.table}` ($names) VALUES ($placeholders) " +
            "ON CONFLICT(`${table.idColumn}`) $onConflict"

        connection.usePrepared(sql) { statement ->
            columns.forEachIndexed { index, column ->
                statement.bindValue(index + 1, row.columns.getValue(column))
            }
            statement.step()
        }
        return connection.changes() > 0
    }

    /**
     * Removes a record that another device deleted.
     *
     * Only ever reached for [SyncKind.RECORD]: a row the outbox names but the table no longer holds
     * is how a deletion travels, and nothing in the shop's history is deletable.
     */
    suspend fun delete(connection: PooledConnection, table: SyncTable, id: String): Boolean {
        connection.usePrepared("DELETE FROM `${table.name}` WHERE `${table.idColumn}` = ?") { statement ->
            statement.bindText(1, id)
            statement.step()
        }
        return connection.changes() > 0
    }

    /**
     * Finds the row already holding a set of unique values, so a clash can be settled rather than
     * deferred.
     *
     * **All** the columns together, never one at a time. SQLite reports a composite index as
     * `variant.productId, variant.colourId`, and matching on `productId` alone finds any variant of
     * that product — which is how a resolution deletes a row that had nothing to do with the clash.
     */
    suspend fun findBy(
        connection: PooledConnection,
        table: SyncTable,
        values: Map<String, SyncValue>,
    ): SyncRow? {
        if (values.isEmpty()) return null
        val columns = values.keys.toList()
        val predicate = columns.joinToString(" AND ") { "`$it` = ?" }
        val id = connection.usePrepared(
            "SELECT `${table.idColumn}` FROM `${table.name}` WHERE $predicate",
        ) { statement ->
            columns.forEachIndexed { index, column ->
                statement.bindValue(index + 1, values.getValue(column))
            }
            if (statement.step()) statement.getText(0) else null
        } ?: return null
        return read(connection, table, id)
    }

    /** Whether a row with this id is already present, which the applier needs before it defers one. */
    suspend fun exists(connection: PooledConnection, table: SyncTable, id: String): Boolean =
        connection.usePrepared(
            "SELECT 1 FROM `${table.name}` WHERE `${table.idColumn}` = ?",
        ) { statement ->
            statement.bindText(1, id)
            statement.step()
        }

    private fun SQLiteStatement.valueAt(index: Int): SyncValue = when (getColumnType(index)) {
        SQLITE_NULL -> SyncValue(SyncValueType.NULL)
        SQLITE_INTEGER -> SyncValue(SyncValueType.INTEGER, getLong(index).toString())
        SQLITE_FLOAT -> SyncValue(SyncValueType.REAL, getDouble(index).toString())
        SQLITE_TEXT -> SyncValue(SyncValueType.TEXT, getText(index))
        SQLITE_BLOB -> SyncValue(SyncValueType.BLOB, getBlob(index).toHex())
        else -> SyncValue(SyncValueType.NULL)
    }

    internal fun SQLiteStatement.bindValue(index: Int, value: SyncValue) {
        val text = value.value
        when {
            value.type == SyncValueType.NULL || text == null -> bindNull(index)
            value.type == SyncValueType.INTEGER -> bindLong(index, text.toLong())
            value.type == SyncValueType.REAL -> bindDouble(index, text.toDouble())
            value.type == SyncValueType.BLOB -> bindBlob(index, text.fromHex())
            else -> bindText(index, text)
        }
    }
}

/**
 * How many rows the last statement changed.
 *
 * An upsert whose `WHERE` refused the update reports zero, which is how a document that was already
 * further along is told apart from one that has just moved.
 */
private suspend fun PooledConnection.changes(): Int =
    usePrepared("SELECT changes()") { statement ->
        if (statement.step()) statement.getLong(0).toInt() else 0
    }

// Hex rather than base64: this schema has no BLOB columns today, and hex needs no experimental
// encoding API to stay correct if one ever arrives.
private fun ByteArray.toHex(): String =
    joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun String.fromHex(): ByteArray =
    ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
