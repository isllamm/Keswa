package com.alsoug.keswa.core.printing.transport

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.platform.IPrinterTransport
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sends bytes to a network printer over raw TCP, port 9100.
 *
 * Written once in `commonMain` and runs unchanged on desktop and Android — with network printers
 * this is the **entire** transport layer, with no platform-specific code anywhere (KD-005, D8).
 *
 * Timeouts are not optional. A printer that is switched off but still holds its DHCP lease accepts
 * a connection attempt and never answers; without a timeout that hangs the sell screen mid-sale.
 *
 * Those timeouts run on the ambient clock, so a test that drives this under `runTest` will see them
 * fire immediately against virtual time. Exercise it with `runBlocking` instead — see
 * `TcpTransportTest`.
 */
class TcpTransport(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val dispatchers: DispatcherProvider,
    private val connectTimeoutMillis: Long = 4_000,
    private val writeTimeoutMillis: Long = 10_000,
) : IPrinterTransport {

    private var selector: SelectorManager? = null
    private var socket: Socket? = null
    private var output: ByteWriteChannel? = null

    override suspend fun open(): Result<Unit> = runCatchingCancellable {
        close()
        val manager = SelectorManager(dispatchers.io)
        selector = manager
        val connected = withTimeout(connectTimeoutMillis.milliseconds) {
            aSocket(manager).tcp().connect(host, port)
        }
        socket = connected
        output = connected.openWriteChannel(autoFlush = true)
    }.onFailure { close() }

    override suspend fun write(bytes: ByteArray): Result<Unit> = runCatchingCancellable {
        val channel = output ?: error("transport is not open")
        withTimeout(writeTimeoutMillis.milliseconds) {
            channel.writeFully(bytes)
            channel.flush()
        }
    }

    override suspend fun close() {
        runCatching { output?.flushAndClose() }
        runCatching { socket?.close() }
        runCatching { selector?.close() }
        output = null
        socket = null
        selector = null
    }

    companion object {
        /** The de facto raw-printing port; every networked ESC/POS and TSPL printer listens here. */
        const val DEFAULT_PORT = 9100
    }
}
