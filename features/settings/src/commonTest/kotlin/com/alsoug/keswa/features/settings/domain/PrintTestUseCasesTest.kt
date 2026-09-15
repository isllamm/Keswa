package com.alsoug.keswa.features.settings.domain

import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.platform.IPrinterTransport
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.printing.MonoBitmap
import com.alsoug.keswa.core.printing.model.Receipt
import com.alsoug.keswa.features.settings.domain.usecase.PrintResult
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestLabelUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestPageUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SaveSettingsUseCase
import com.alsoug.keswa.features.settings.domain.usecase.TransportFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class FakeSettingsRepository(var settings: ShopSettings = ShopSettings()) : ISettingsRepository {
    override suspend fun get(): Result<ShopSettings> = Result.success(settings)
    override suspend fun save(settings: ShopSettings): Result<Unit> {
        this.settings = settings
        return Result.success(Unit)
    }
}

private class RecordingTransport(private val failWith: Throwable? = null) : IPrinterTransport {
    val written = mutableListOf<ByteArray>()
    var openCount = 0
    var closeCount = 0
    override suspend fun open(): Result<Unit> =
        failWith?.let { Result.failure(it) } ?: Result.success(Unit).also { openCount++ }
    override suspend fun write(bytes: ByteArray): Result<Unit> {
        written += bytes
        return Result.success(Unit)
    }
    override suspend fun close() { closeCount++ }
}

private class StubRenderer : IReceiptRenderer {
    var requestedWidth: Int = 0
    override fun render(receipt: Receipt, widthDots: Int) = MonoBitmap(widthDots, 4)
    override fun renderTestPage(widthDots: Int): MonoBitmap {
        requestedWidth = widthDots
        return MonoBitmap(widthDots, 4)
    }
}

class PrintTestPageUseCaseTest {

    private val renderer = StubRenderer()

    @Test
    fun `a configured printer receives a complete ESC-POS document`() = runTest {
        // Given a printer at a known address
        val repository = FakeSettingsRepository(
            ShopSettings(receiptHost = "192.168.1.50", receiptPort = 9100),
        )
        val transport = RecordingTransport()

        val result = PrintTestPageUseCase(repository, renderer) { _, _ -> transport }()

        assertIs<PrintResult.Printed>(result.getOrThrow())
        val bytes = transport.written.single()
        // reset … raster … cut
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
        assertTrue(bytes.size > 8)
        // and the socket was closed afterwards, so the next print can connect
        assertEquals(1, transport.closeCount)
    }

    @Test
    fun `the paper width from settings reaches the renderer`() = runTest {
        val repository = FakeSettingsRepository(
            ShopSettings(receiptHost = "10.0.0.5", paperWidthDots = MonoBitmap.WIDTH_58MM),
        )

        PrintTestPageUseCase(repository, renderer) { _, _ -> RecordingTransport() }()

        assertEquals(MonoBitmap.WIDTH_58MM, renderer.requestedWidth)
    }

    @Test
    fun `an unconfigured printer says so rather than failing`() = runTest {
        val repository = FakeSettingsRepository(ShopSettings(receiptHost = ""))

        val result = PrintTestPageUseCase(repository, renderer) { _, _ -> RecordingTransport() }()

        assertIs<PrintResult.NotConfigured>(result.getOrThrow())
    }

    @Test
    fun `an unreachable printer is an outcome, not an exception`() = runTest {
        // Given the printer is switched off
        val repository = FakeSettingsRepository(ShopSettings(receiptHost = "192.168.1.50"))
        val dead = RecordingTransport(failWith = IllegalStateException("connection refused"))

        val result = PrintTestPageUseCase(repository, renderer) { _, _ -> dead }()

        // Then the caller gets a describable failure it can put on screen
        val unreachable = assertIs<PrintResult.Unreachable>(result.getOrThrow())
        assertTrue(unreachable.detail.contains("refused"))
        assertTrue(result.isSuccess, "a dead printer is not a programming error")
    }

    @Test
    fun `the configured address is the one dialled`() = runTest {
        val repository = FakeSettingsRepository(
            ShopSettings(receiptHost = "10.1.2.3", receiptPort = 9101),
        )
        var dialled: Pair<String, Int>? = null
        val factory = TransportFactory { host, port ->
            dialled = host to port
            RecordingTransport()
        }

        PrintTestPageUseCase(repository, renderer, factory)()

        assertEquals("10.1.2.3" to 9101, dialled)
    }
}

class PrintTestLabelUseCaseTest {

    @Test
    fun `a test label carries a valid barcode and the configured media size`() = runTest {
        val repository = FakeSettingsRepository(
            ShopSettings(labelHost = "192.168.1.51", labelWidthMm = 50, labelHeightMm = 25),
        )
        val transport = RecordingTransport()

        val result = PrintTestLabelUseCase(repository) { _, _ -> transport }()

        assertIs<PrintResult.Printed>(result.getOrThrow())
        val label = transport.written.single().decodeToString()
        assertTrue(label.contains("SIZE 50 mm,25 mm"), label)
        assertTrue(label.contains("2000000000015"), label)
        assertTrue(label.trimEnd().endsWith("PRINT 1,1"), label)
    }

    @Test
    fun `no label printer configured is reported, not attempted`() = runTest {
        val repository = FakeSettingsRepository(ShopSettings(labelHost = ""))
        val transport = RecordingTransport()

        val result = PrintTestLabelUseCase(repository) { _, _ -> transport }()

        assertIs<PrintResult.NotConfigured>(result.getOrThrow())
        assertTrue(transport.written.isEmpty())
    }
}

class SaveSettingsUseCaseTest {

    @Test
    fun `nonsense ports are refused before they reach the database`() = runTest {
        val repository = FakeSettingsRepository()
        val save = SaveSettingsUseCase(repository)

        assertTrue(save(ShopSettings(receiptPort = 0)).isFailure)
        assertTrue(save(ShopSettings(receiptPort = 70_000)).isFailure)
        assertTrue(save(ShopSettings(labelPort = -1)).isFailure)
        assertTrue(save(ShopSettings(paperWidthDots = 0)).isFailure)
    }

    @Test
    fun `a valid configuration is stored`() = runTest {
        val repository = FakeSettingsRepository()

        val result = SaveSettingsUseCase(repository)(
            ShopSettings(receiptHost = "192.168.1.50", labelHost = "192.168.1.51"),
        )

        assertTrue(result.isSuccess)
        assertEquals("192.168.1.50", repository.settings.receiptHost)
    }
}
