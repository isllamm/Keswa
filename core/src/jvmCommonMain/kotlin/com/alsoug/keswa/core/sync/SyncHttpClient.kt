package com.alsoug.keswa.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The client the sync engine talks through.
 *
 * In `jvmCommonMain` for the same reason `JvmPasswordHasher` is (KD-004, amended in Phase 6): both
 * targets are JVM, so one engine means one set of timeout behaviour rather than two that drift.
 *
 * Timeouts rather than none. A till whose shop router has gone quiet must find that out in seconds
 * and get on with selling; the retry that follows is the scheduler's business, and KD-009 bounds it.
 */
fun createSyncHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT
        requestTimeoutMillis = REQUEST_TIMEOUT
        socketTimeoutMillis = REQUEST_TIMEOUT
    }
}

private const val CONNECT_TIMEOUT = 5_000L

/** A week's backlog can be a large body on a slow line, so this is generous rather than snappy. */
private const val REQUEST_TIMEOUT = 60_000L
