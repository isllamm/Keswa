package com.alsoug.keswa.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * The four calls the till makes.
 *
 * An interface so the engine's tests can drive it without a socket, and so the engine never holds
 * an [HttpClient] — the same reason every other platform capability in `:core` is an interface.
 */
interface ISyncApi {
    suspend fun enrol(baseUrl: String, request: EnrolRequest): EnrolResponse
    suspend fun push(baseUrl: String, token: String, rows: List<SyncRow>): PushResponse
    suspend fun pull(baseUrl: String, token: String, since: Long, limit: Int): PullResponse
}

/**
 * Thrown when the server refuses this device.
 *
 * Distinct from any other failure because the response is different: a revoked or unknown token is
 * not something retrying will fix, and the engine must stop rather than back off and try again for
 * ever. The outbox is left untouched — a device that is refused has not lost its work.
 */
class SyncNotAuthorised(message: String) : Exception(message)

class KtorSyncApi(private val client: HttpClient) : ISyncApi {

    override suspend fun enrol(baseUrl: String, request: EnrolRequest): EnrolResponse =
        client.post("${baseUrl.trimEnd('/')}/v1/enrol") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.orThrow().body()

    override suspend fun push(baseUrl: String, token: String, rows: List<SyncRow>): PushResponse =
        client.post("${baseUrl.trimEnd('/')}/v1/sync/push") {
            header(AUTHORIZATION, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(PushRequest(rows))
        }.orThrow().body()

    override suspend fun pull(baseUrl: String, token: String, since: Long, limit: Int): PullResponse =
        client.get("${baseUrl.trimEnd('/')}/v1/sync/pull") {
            header(AUTHORIZATION, "Bearer $token")
            parameter("since", since)
            parameter("limit", limit)
        }.orThrow().body()

    private fun HttpResponse.orThrow(): HttpResponse = when (status) {
        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
            throw SyncNotAuthorised("this device is not enrolled, or its token has been revoked")
        else -> this
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
