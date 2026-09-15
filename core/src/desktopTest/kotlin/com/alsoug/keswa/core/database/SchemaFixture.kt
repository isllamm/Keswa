package com.alsoug.keswa.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds a database at an **older** schema version, from the JSON Room exported at the time.
 *
 * This is the missing half of KD-002. Room can only ever create the current schema, so without
 * this a migration is untestable until a real shop is already carrying the old one — which is
 * exactly when a mistake is unrecoverable, because there is no server to re-fetch from.
 *
 * Reproduces what Room itself writes: every table and index from `createSql`, the `user_version`
 * pragma, and the `room_master_table` identity hash Room validates on open. Get that hash wrong and
 * Room refuses to open rather than migrating, which is a useful failure — it means the fixture is
 * lying about what version it built.
 */
object SchemaFixture {

    private const val ROOM_MASTER_ID = 42

    fun createDatabaseAtVersion(file: File, schemaJson: String) {
        val database = Json.parseToJsonElement(schemaJson).jsonObject
            .getValue("database").jsonObject
        val version = database.getValue("version").jsonPrimitive.int
        val identityHash = database.getValue("identityHash").jsonPrimitive.content

        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            database.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                connection.execSQL(entity.jsonObject.sqlFor("createSql", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(index.jsonObject.sqlFor("createSql", table))
                }
            }
            connection.execSQL("PRAGMA user_version = $version")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES($ROOM_MASTER_ID, '$identityHash')",
            )
        }
    }

    fun exportedSchema(version: Int): String {
        val file = File("schemas/com.alsoug.keswa.core.database.KeswaDatabase/$version.json")
        check(file.exists()) {
            "no exported schema for version $version at ${file.absolutePath} — is exportSchema still on?"
        }
        return file.readText()
    }

    /** Runs raw SQL against an existing file, for seeding a fixture at the old schema. */
    fun execute(file: File, statements: List<String>) {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            statements.forEach { connection.execSQL(it) }
        }
    }

    private fun kotlinx.serialization.json.JsonObject.sqlFor(key: String, table: String): String =
        getValue(key).jsonPrimitive.content.replace("\${TABLE_NAME}", table)
}
