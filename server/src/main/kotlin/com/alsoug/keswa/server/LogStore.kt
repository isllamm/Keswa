package com.alsoug.keswa.server

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.alsoug.keswa.core.sync.LoggedRow
import com.alsoug.keswa.core.sync.SyncRow
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The log, the devices, and nothing else.
 *
 * Deliberately raw SQLite rather than a second Room database. Two append-only tables and a device
 * registry do not need an ORM, a KSP round or a schema-export directory, and keeping the log
 * mechanically simple is what makes 9c's exit path credible: whatever replays this into Postgres
 * one day only has to read two tables.
 *
 * One connection behind a mutex. That is not a concession, it is the design — the log's order is
 * the total order every device agrees on, and a single writer is the cheapest way to have one.
 */
class LogStore(path: String, private val json: Json = Json) {

    private val connection: SQLiteConnection = BundledSQLiteDriver().open(path)
    private val lock = Mutex()

    init {
        connection.execSQL("PRAGMA journal_mode = WAL")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS change_log (" +
                "seq INTEGER PRIMARY KEY AUTOINCREMENT, tableName TEXT NOT NULL, " +
                "rowId TEXT NOT NULL, payload TEXT NOT NULL, deviceId TEXT NOT NULL, " +
                "receivedAt INTEGER NOT NULL)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_change_log_row ON change_log (tableName, rowId)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS device (" +
                "id TEXT PRIMARY KEY, name TEXT NOT NULL, tokenHash TEXT NOT NULL, " +
                "ordinal INTEGER NOT NULL, enrolledAt INTEGER NOT NULL, revokedAt INTEGER)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS enrolment_code (" +
                "code TEXT PRIMARY KEY, createdAt INTEGER NOT NULL, usedAt INTEGER, " +
                "usedByDeviceId TEXT)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS server_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        )
    }

    data class Device(val id: String, val name: String, val ordinal: Int, val revoked: Boolean)

    // ---- the log ------------------------------------------------------------------------------

    /**
     * Appends rows and returns the new high-water mark.
     *
     * Nothing is validated beyond its shape. The rules that decide whether a sale is legal ran on
     * the device, before the row existed, against the same use cases every other device runs — 9h.
     * Running them again here would be a second implementation of `CreditPolicy`, which is one
     * implementation and one thing that used to be it.
     */
    suspend fun append(deviceId: String, rows: List<SyncRow>, receivedAt: Long): Long = lock.withLock {
        connection.execSQL("BEGIN IMMEDIATE")
        try {
            connection.prepare(
                "INSERT INTO change_log (tableName, rowId, payload, deviceId, receivedAt) " +
                    "VALUES (?, ?, ?, ?, ?)",
            ).use { statement ->
                rows.forEach { row ->
                    statement.reset()
                    statement.bindText(1, row.table)
                    statement.bindText(2, row.id)
                    statement.bindText(3, json.encodeToString(row))
                    statement.bindText(4, deviceId)
                    statement.bindLong(5, receivedAt)
                    statement.step()
                }
            }
            connection.execSQL("COMMIT")
        } catch (failure: Throwable) {
            connection.execSQL("ROLLBACK")
            throw failure
        }
        // Read inside the lock we already hold — the mutex is not reentrant, and calling the public
        // accessor here would wait on this coroutine for ever.
        maxSeq()
    }

    suspend fun read(since: Long, limit: Int): List<LoggedRow> = lock.withLock {
        connection.prepare(
            "SELECT seq, deviceId, payload FROM change_log WHERE seq > ? ORDER BY seq LIMIT ?",
        ).use { statement ->
            statement.bindLong(1, since)
            statement.bindLong(2, limit.toLong())
            buildList {
                while (statement.step()) {
                    add(
                        LoggedRow(
                            seq = statement.getLong(0),
                            deviceId = statement.getText(1),
                            row = json.decodeFromString(statement.getText(2)),
                        ),
                    )
                }
            }
        }
    }

    suspend fun highWaterMark(): Long = lock.withLock { maxSeq() }

    private fun maxSeq(): Long =
        connection.prepare("SELECT COALESCE(MAX(seq), 0) FROM change_log").use { statement ->
            if (statement.step()) statement.getLong(0) else 0L
        }

    suspend fun materialisedThrough(): Long =
        state(MATERIALISED_THROUGH)?.toLongOrNull() ?: 0L

    suspend fun setMaterialisedThrough(seq: Long) = putState(MATERIALISED_THROUGH, seq.toString())

    // ---- devices ------------------------------------------------------------------------------

    suspend fun enrol(code: String, deviceId: String, deviceName: String, at: Long): Device? =
        lock.withLock {
            val usable = connection.prepare(
                "SELECT COUNT(*) FROM enrolment_code WHERE code = ? AND usedAt IS NULL",
            ).use { statement ->
                statement.bindText(1, code)
                statement.step() && statement.getLong(0) > 0
            }
            if (!usable) return@withLock null

            // Re-enrolling a device it already knows keeps its ordinal. A till that is reinstalled
            // must not start issuing receipt numbers from another device's block.
            val existing = deviceRow(deviceId)
            val ordinal = existing?.ordinal ?: nextOrdinal()

            connection.prepare(
                "INSERT INTO device (id, name, tokenHash, ordinal, enrolledAt, revokedAt) " +
                    "VALUES (?, ?, '', ?, ?, NULL) " +
                    "ON CONFLICT(id) DO UPDATE SET name = excluded.name, revokedAt = NULL",
            ).use { statement ->
                statement.bindText(1, deviceId)
                statement.bindText(2, deviceName)
                statement.bindLong(3, ordinal.toLong())
                statement.bindLong(4, at)
                statement.step()
            }
            connection.prepare(
                "UPDATE enrolment_code SET usedAt = ?, usedByDeviceId = ? WHERE code = ?",
            ).use { statement ->
                statement.bindLong(1, at)
                statement.bindText(2, deviceId)
                statement.bindText(3, code)
                statement.step()
            }
            Device(deviceId, deviceName, ordinal, revoked = false)
        }

    /** Only the hash is kept. A token this server cannot reproduce is a token a leak cannot reuse. */
    suspend fun setToken(deviceId: String, token: String) = lock.withLock {
        connection.prepare("UPDATE device SET tokenHash = ? WHERE id = ?").use { statement ->
            statement.bindText(1, hash(token))
            statement.bindText(2, deviceId)
            statement.step()
        }
        Unit
    }

    suspend fun deviceForToken(token: String): Device? = lock.withLock {
        connection.prepare(
            "SELECT id, name, ordinal, revokedAt FROM device WHERE tokenHash = ?",
        ).use { statement ->
            statement.bindText(1, hash(token))
            if (!statement.step()) return@withLock null
            Device(
                id = statement.getText(0),
                name = statement.getText(1),
                ordinal = statement.getLong(2).toInt(),
                revoked = !statement.isNull(3),
            )
        }
    }

    suspend fun revoke(deviceId: String, at: Long): Boolean = lock.withLock {
        connection.prepare("UPDATE device SET revokedAt = ? WHERE id = ? AND revokedAt IS NULL")
            .use { statement ->
                statement.bindLong(1, at)
                statement.bindText(2, deviceId)
                statement.step()
            }
        changes() > 0
    }

    suspend fun mintEnrolmentCode(code: String, at: Long) = lock.withLock {
        connection.prepare("INSERT INTO enrolment_code (code, createdAt) VALUES (?, ?)")
            .use { statement ->
                statement.bindText(1, code)
                statement.bindLong(2, at)
                statement.step()
            }
        Unit
    }

    fun close() = connection.close()

    // ---- plumbing -----------------------------------------------------------------------------

    private fun deviceRow(deviceId: String): Device? =
        connection.prepare("SELECT id, name, ordinal, revokedAt FROM device WHERE id = ?")
            .use { statement ->
                statement.bindText(1, deviceId)
                if (!statement.step()) return null
                Device(
                    statement.getText(0),
                    statement.getText(1),
                    statement.getLong(2).toInt(),
                    !statement.isNull(3),
                )
            }

    private fun nextOrdinal(): Int =
        connection.prepare("SELECT COALESCE(MAX(ordinal), -1) + 1 FROM device").use { statement ->
            if (statement.step()) statement.getLong(0).toInt() else 0
        }

    private fun changes(): Int =
        connection.prepare("SELECT changes()").use { statement ->
            if (statement.step()) statement.getLong(0).toInt() else 0
        }

    private suspend fun state(key: String): String? = lock.withLock {
        connection.prepare("SELECT value FROM server_state WHERE key = ?").use { statement ->
            statement.bindText(1, key)
            if (statement.step()) statement.getText(0) else null
        }
    }

    private suspend fun putState(key: String, value: String) = lock.withLock {
        connection.prepare("INSERT OR REPLACE INTO server_state (key, value) VALUES (?, ?)")
            .use { statement ->
                statement.bindText(1, key)
                statement.bindText(2, value)
                statement.step()
            }
        Unit
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.encodeToByteArray())
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private inline fun <R> SQLiteStatement.use(block: (SQLiteStatement) -> R): R =
        try {
            block(this)
        } finally {
            close()
        }

    private companion object {
        const val MATERIALISED_THROUGH = "materialisedThrough"
    }
}
