package com.alsoug.keswa.core.sync

import kotlinx.serialization.Serializable

/**
 * The wire contract, shared by the till and the server at compile time.
 *
 * Rows travel as SQLite values rather than as entities. Reading a row with `SELECT *` and writing
 * it back by binding means the payload is exactly what storage holds — no entity mapper in the
 * path, no enum or boolean or `Money` convention to keep in step on both sides, and no chance of a
 * new column being added to the schema and quietly not syncing. It also means a sync row is
 * meaningful without the Kotlin class that produced it, which is what makes 9c's exit path (replay
 * the log into something else) real rather than theoretical.
 */
@Serializable
enum class SyncValueType { TEXT, INTEGER, REAL, BLOB, NULL }

/**
 * One column value, tagged with its storage type and carried as text.
 *
 * Text rather than a JSON number on purpose: a JSON parser that reads `9007199254740993` as a
 * double loses a piastre, and this schema is full of `Long` money columns where that is exactly
 * the wrong trade.
 */
@Serializable
data class SyncValue(
    val type: SyncValueType,
    val value: String? = null,
)

@Serializable
data class SyncRow(
    val table: String,
    val id: String,
    val columns: Map<String, SyncValue>,
)

/** A row as the server holds it: the row itself, its place in the log, and who wrote it. */
@Serializable
data class LoggedRow(
    val seq: Long,
    val deviceId: String,
    val row: SyncRow,
)

@Serializable
data class PushRequest(val rows: List<SyncRow>)

@Serializable
data class PushResponse(val accepted: Int, val highWaterMark: Long)

@Serializable
data class PullResponse(
    val rows: List<LoggedRow>,
    val nextSeq: Long,
    val hasMore: Boolean,
)

/**
 * The device names itself.
 *
 * [deviceId] is generated locally at first launch, not handed out by the server, so a till has
 * an identity before it has ever met one. The server registers it rather than issuing it, which
 * is one less id to reconcile and one less state a half-finished enrolment can be left in.
 */
@Serializable
data class EnrolRequest(val code: String, val deviceId: String, val deviceName: String)

/**
 * [ordinal] is what keeps two tills from issuing the same receipt number (9i): each device sells
 * from its own block of a million, and the first device to enrol takes ordinal 0 so a shop already
 * trading sees its numbering continue undisturbed.
 */
@Serializable
data class EnrolResponse(
    val token: String,
    val ordinal: Int,
)

@Serializable
data class SyncError(val message: String)
