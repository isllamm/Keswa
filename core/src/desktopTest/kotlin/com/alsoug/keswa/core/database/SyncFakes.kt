package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.ISyncTokenStore
import com.alsoug.keswa.core.platform.LogLevel
import com.alsoug.keswa.core.sync.EnrolRequest
import com.alsoug.keswa.core.sync.EnrolResponse
import com.alsoug.keswa.core.sync.ISyncApi
import com.alsoug.keswa.core.sync.LoggedRow
import com.alsoug.keswa.core.sync.PullResponse
import com.alsoug.keswa.core.sync.PushResponse
import com.alsoug.keswa.core.sync.SyncRow

/** Hand-written, per ADR-019 — no mocking framework. */
class FakePlatform : IPlatformProvider {
    override val platformName = "test"
    override val platformVersion = "1"
    val lines = mutableListOf<String>()
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        lines += "$level $tag $message"
    }
}

class FakeTokenStore(private var token: String? = null) : ISyncTokenStore {
    override suspend fun store(token: String) { this.token = token }
    override suspend fun read(): String? = token
    override suspend fun clear() { token = null }
}

/**
 * A server that keeps everything it is given and hands back whatever is put in [log].
 *
 * [failures] makes the next N calls throw, which is how the scheduler's backoff gets exercised
 * without a socket or a clock.
 */
class FakeSyncApi(
    var log: MutableList<LoggedRow> = mutableListOf(),
    var failures: Int = 0,
    var failure: () -> Throwable = { RuntimeException("no route to host") },
) : ISyncApi {

    val pushed = mutableListOf<List<SyncRow>>()
    var pushCalls = 0
    var pullCalls = 0

    override suspend fun enrol(baseUrl: String, request: EnrolRequest): EnrolResponse =
        EnrolResponse(token = "token-for-${request.deviceId}", ordinal = 1)

    override suspend fun push(baseUrl: String, token: String, rows: List<SyncRow>): PushResponse {
        pushCalls++
        consumeFailure()
        pushed += rows
        rows.forEach { log += LoggedRow(log.size + 1L, "other-device", it) }
        return PushResponse(accepted = rows.size, highWaterMark = log.size.toLong())
    }

    override suspend fun pull(baseUrl: String, token: String, since: Long, limit: Int): PullResponse {
        pullCalls++
        consumeFailure()
        val window = log.filter { it.seq > since }.take(limit)
        return PullResponse(
            rows = window,
            nextSeq = window.lastOrNull()?.seq ?: since,
            hasMore = window.size == limit,
        )
    }

    private fun consumeFailure() {
        if (failures > 0) {
            failures--
            throw failure()
        }
    }
}

/** A movement for a variant this device has never heard of, which is what a deferred row is. */
fun orphanMovementColumns(): Map<String, com.alsoug.keswa.core.sync.SyncValue> {
    fun text(value: String) = com.alsoug.keswa.core.sync.SyncValue(
        com.alsoug.keswa.core.sync.SyncValueType.TEXT, value,
    )
    fun integer(value: Long) = com.alsoug.keswa.core.sync.SyncValue(
        com.alsoug.keswa.core.sync.SyncValueType.INTEGER, value.toString(),
    )
    val absent = com.alsoug.keswa.core.sync.SyncValue(com.alsoug.keswa.core.sync.SyncValueType.NULL)

    return mapOf(
        "id" to text("mov-orphan"),
        "variantId" to text("var-nobody-has-seen"),
        "locationId" to text(SHOP_ID),
        "quantity" to integer(3),
        "reason" to text("RECEIPT"),
        "refType" to absent,
        "refId" to absent,
        "occurredAt" to integer(1_757_000_000_000),
        "userId" to text("user-1"),
        "unitCostPiastres" to absent,
        "note" to absent,
    )
}
