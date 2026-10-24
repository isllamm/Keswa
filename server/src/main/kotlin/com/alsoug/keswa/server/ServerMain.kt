package com.alsoug.keswa.server

import com.alsoug.keswa.core.coroutines.DefaultDispatcherProvider
import com.alsoug.keswa.core.database.getDatabaseBuilder
import com.alsoug.keswa.core.database.getKeswaDatabase
import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.platform.LogLevel
import com.alsoug.keswa.core.sync.LogApplier
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import java.io.File
import java.security.SecureRandom
import kotlin.system.exitProcess

/**
 * The shop's server: one process, two SQLite files, no ceremony.
 *
 * `keswa.db` is the shop as the back office will read it, built by `:core`'s own migrations from
 * `:core`'s own entities — the server is a till that never sells (9c). `sync-log.db` is the log it
 * was built from, and is the thing to back up: the other file can be rebuilt from it.
 */
fun main() {
    val directory = File(System.getenv("KESWA_SERVER_HOME") ?: "${System.getProperty("user.home")}/keswa-server")
    directory.mkdirs()

    val port = System.getenv("KESWA_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val platform = ServerPlatformProvider()

    // Required rather than minted. A server that generates its own admin token on each restart is
    // a server whose token changes under whoever wrote it down, and one that defaults to a
    // constant is one that anybody on the shop's wifi can enrol a device against.
    val adminToken = System.getenv("KESWA_ADMIN_TOKEN")
    if (adminToken.isNullOrBlank()) {
        platform.log(
            LogLevel.ERROR,
            TAG,
            "KESWA_ADMIN_TOKEN is not set. Generate a long random value, keep it somewhere the " +
                "shop can find it, and start again with it in the environment.",
        )
        exitProcess(1)
    }

    val dispatchers = DefaultDispatcherProvider()
    val database = getKeswaDatabase(getDatabaseBuilder(directory), dispatchers)
    val log = LogStore(directory.resolve(LOG_DATABASE).absolutePath)
    val materialiser = Materialiser(
        log = log,
        applier = LogApplier(database, UuidIdGenerator(), System::currentTimeMillis),
    )

    platform.log(LogLevel.INFO, TAG, "serving on port $port from ${directory.absolutePath}")
    embeddedServer(CIO, port = port) {
        syncModule(log, materialiser, adminToken)
    }.start(wait = true)
}

fun Application.syncModule(
    log: LogStore,
    materialiser: Materialiser,
    adminToken: String,
    now: () -> Long = System::currentTimeMillis,
    newSecret: () -> String = ::randomSecret,
) {
    install(ContentNegotiation) { json() }
    routing { syncRoutes(log, materialiser, adminToken, now, newSecret) }
}

/** 256 bits from the platform's CSPRNG, hex-encoded. Only its hash is ever stored. */
fun randomSecret(): String {
    val bytes = ByteArray(SECRET_BYTES).also(SecureRandom()::nextBytes)
    return bytes.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

private const val TAG = "Server"
private const val DEFAULT_PORT = 8_080
private const val SECRET_BYTES = 32
private const val LOG_DATABASE = "sync-log.db"
