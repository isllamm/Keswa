package com.alsoug.keswa.server

import com.alsoug.keswa.core.sync.EnrolRequest
import com.alsoug.keswa.core.sync.EnrolResponse
import com.alsoug.keswa.core.sync.PullResponse
import com.alsoug.keswa.core.sync.PushRequest
import com.alsoug.keswa.core.sync.PushResponse
import com.alsoug.keswa.core.sync.SyncError
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import io.ktor.server.application.ApplicationCall

/**
 * Four calls for a till, two for whoever administers the shop.
 *
 * The server validates shape, authenticity and ownership and stops there (9h). It does not check
 * credit limits, recompute totals or decide whether a return is inside its window — those ran on
 * the device, in `commonMain`, before the row existed.
 */
fun Route.syncRoutes(
    log: LogStore,
    materialiser: Materialiser,
    adminToken: String,
    now: () -> Long,
    newSecret: () -> String,
) {
    get("/v1/health") {
        call.respond(mapOf("status" to "ok", "highWaterMark" to log.highWaterMark().toString()))
    }

    post("/v1/enrol") {
        val request = call.receive<EnrolRequest>()
        val device = log.enrol(request.code, request.deviceId, request.deviceName, now())
        if (device == null) {
            call.respond(HttpStatusCode.Forbidden, SyncError("that enrolment code is not usable"))
            return@post
        }
        val token = newSecret()
        log.setToken(device.id, token)
        call.respond(EnrolResponse(token = token, ordinal = device.ordinal))
    }

    route("/v1/sync") {
        post("/push") {
            val device = call.authenticatedDevice(log) ?: return@post
            val request = call.receive<PushRequest>()

            val mark = log.append(device.id, request.rows, now())
            // Materialise straight away so the back office is current, but never inside the push:
            // the till's work is durable the moment it is in the log, and a materialisation that
            // fails must not tell a device its sales were rejected.
            runCatching { materialiser.run() }

            call.respond(PushResponse(accepted = request.rows.size, highWaterMark = mark))
        }

        get("/pull") {
            val device = call.authenticatedDevice(log) ?: return@get
            val since = call.parameters["since"]?.toLongOrNull() ?: 0L
            val limit = (call.parameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

            val window = log.read(since, limit)
            call.respond(
                PullResponse(
                    // A device is not sent its own rows back. `nextSeq` still comes from the whole
                    // window, so a batch that happens to be entirely this device's own work moves
                    // the cursor rather than stalling it.
                    rows = window.filter { it.deviceId != device.id },
                    nextSeq = window.lastOrNull()?.seq ?: since,
                    hasMore = window.size == limit,
                ),
            )
        }
    }

    route("/v1/admin") {
        post("/enrolment-code") {
            if (!call.isAdmin(adminToken)) {
                call.respond(HttpStatusCode.Unauthorized, SyncError("admin token required"))
                return@post
            }
            val code = newSecret().take(CODE_LENGTH).uppercase()
            log.mintEnrolmentCode(code, now())
            call.respond(mapOf("code" to code))
        }

        post("/revoke") {
            if (!call.isAdmin(adminToken)) {
                call.respond(HttpStatusCode.Unauthorized, SyncError("admin token required"))
                return@post
            }
            val deviceId = call.parameters["deviceId"].orEmpty()
            val revoked = log.revoke(deviceId, now())
            call.respond(mapOf("revoked" to revoked.toString()))
        }
    }
}

private fun ApplicationCall.bearer(): String? =
    request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()?.ifEmpty { null }

private fun ApplicationCall.isAdmin(adminToken: String): Boolean = bearer() == adminToken

/**
 * Resolves the calling device, or answers 401 and returns null.
 *
 * A revoked device is refused here and nowhere else, which is what makes revocation the control
 * that does the real work in KD-007: a stolen laptop stops syncing on its next attempt, and its
 * token is worth nothing without this server agreeing.
 */
private suspend fun ApplicationCall.authenticatedDevice(log: LogStore): LogStore.Device? {
    val token = bearer()
    val device = token?.let { log.deviceForToken(it) }
    if (device == null || device.revoked) {
        respond(HttpStatusCode.Unauthorized, SyncError("this device is not enrolled, or was revoked"))
        return null
    }
    return device
}

private const val DEFAULT_LIMIT = 500
private const val MAX_LIMIT = 2_000
private const val CODE_LENGTH = 8
