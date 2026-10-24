package com.alsoug.keswa.core.printing

import com.alsoug.keswa.core.coroutines.RealDispatchers
import com.alsoug.keswa.core.printing.escpos.EscPos
import com.alsoug.keswa.core.printing.transport.TcpTransport
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Proves the transport against a real socket, with no printer.
 *
 * A networked ESC/POS printer is, from the app's side, a TCP server on port 9100 that reads bytes
 * and never replies. A `ServerSocket` is an accurate stand-in for everything except the paper, so
 * this covers the whole transport before the hardware arrives.
 *
 * Uses `runBlocking`, not `runTest`, deliberately. `runTest` advances a **virtual** clock, so the
 * transport's `withTimeout` elapses instantly and every connection times out before a real socket
 * can answer — which first showed up as a test that passed or failed depending on the race. Real
 * I/O needs a real clock. (`runBlocking` is banned in production code, not in tests.)
 */
class TcpTransportTest {

    private val servers = mutableListOf<ServerSocket>()

    @AfterTest
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
    }

    /**
     * Accepts [connections] connections in turn, reading each to EOF and accumulating — a real
     * printer serves one document per connection and the till prints many through one transport.
     */
    private fun startPrinterStub(connections: Int = 1): Pair<Int, AtomicReference<ByteArray>> {
        val socket = ServerSocket(0)
        servers += socket
        val received = AtomicReference(ByteArray(0))
        thread(isDaemon = true) {
            repeat(connections) {
                runCatching {
                    socket.accept().use { client ->
                        val bytes = client.getInputStream().readBytes()
                        received.updateAndGet { it + bytes }
                    }
                }
            }
        }
        return socket.localPort to received
    }

    @Test
    fun `a document arrives byte for byte`() = runBlocking {
        // Given a printer listening, and a receipt to send
        val (port, received) = startPrinterStub()
        val bitmap = MonoBitmap(16, 2).apply { this[0, 0] = true }
        val document = EscPos.document(bitmap)

        // When it is sent
        val transport = TcpTransport("127.0.0.1", port, RealDispatchers)
        val result = transport.send(document)

        // Then every byte landed, unchanged
        assertTrue(result.isSuccess, "send failed: ${result.exceptionOrNull()}")
        awaitBytes(received, document.size)
        assertEquals(document.hex(), received.get().hex())
    }

    @Test
    fun `a printer that is switched off fails fast instead of hanging the till`() = runBlocking {
        // Given a port with nothing listening — a closed socket refuses immediately
        val free = ServerSocket(0).use { it.localPort }

        val transport = TcpTransport(
            host = "127.0.0.1",
            port = free,
            dispatchers = RealDispatchers,
            connectTimeoutMillis = 1_000,
        )
        val result = transport.open()

        // Then it is a Result.failure, not an exception escaping into the sell screen
        assertTrue(result.isFailure)
    }

    @Test
    fun `writing without opening is reported, not thrown`() = runBlocking {
        val transport = TcpTransport("127.0.0.1", 9100, RealDispatchers)
        val result = transport.write(byteArrayOf(1, 2, 3))
        assertTrue(result.isFailure)
    }

    @Test
    fun `one transport prints document after document`() = runBlocking {
        // Given a printer that will serve two documents, and one configured transport
        val (port, received) = startPrinterStub(connections = 2)
        val transport = TcpTransport("127.0.0.1", port, RealDispatchers)

        // When the same transport sends twice — the till's normal day
        assertTrue(transport.send(EscPos.initialise()).isSuccess, "first send failed")
        awaitBytes(received, 2)
        assertTrue(transport.send(EscPos.cut()).isSuccess, "second send failed")
        awaitBytes(received, 9)

        // Then both arrived in order: send() re-opens rather than reusing a closed channel
        assertEquals("1B 40 1B 64 04 1D 56 42 00", received.get().hex())
    }

    /** The stub reads on another thread; give it a bounded moment to catch up. */
    private fun awaitBytes(sink: AtomicReference<ByteArray>, expected: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (sink.get().size < expected && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
    }
}
