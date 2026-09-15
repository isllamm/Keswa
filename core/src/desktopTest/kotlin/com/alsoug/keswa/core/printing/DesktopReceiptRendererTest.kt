package com.alsoug.keswa.core.printing

import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.printing.escpos.EscPos
import com.alsoug.keswa.core.printing.model.Receipt
import com.alsoug.keswa.core.printing.model.ReceiptLine
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopReceiptRendererTest {

    private val renderer = DesktopReceiptRenderer()

    private val receipt = Receipt(
        shopName = "KESWA",
        shopNameAr = "كسوة للملابس",
        addressLine = "Downtown, Cairo",
        lines = listOf(
            ReceiptLine(1, "Oxford shirt / Navy", "قميص أكسفورد / كحلي", Money.parse("499.00")!!),
            ReceiptLine(2, "Cotton t-shirt / White", "تيشيرت قطن / أبيض", Money.parse("500.00")!!),
        ),
        subtotal = Money.parse("999.00")!!,
        discount = Money.parse("49.95")!!,
        vat = Money.parse("116.59")!!,
        total = Money.parse("949.05")!!,
        itemCount = 3,
        saleId = "SALE-2026-09-14-0189",
        timestamp = "14 Sep 2026 · 21:32",
    )

    @Test
    fun `a receipt renders at the paper width with ink on it`() {
        val bitmap = renderer.render(receipt, MonoBitmap.WIDTH_80MM)

        assertEquals(MonoBitmap.WIDTH_80MM, bitmap.width)
        assertEquals(72, bitmap.rowBytes)
        assertTrue(bitmap.height > 300, "a receipt with two lines and a QR should be tall: ${bitmap.height}")

        val inked = bitmap.data.count { it != 0.toByte() }
        assertTrue(inked > 200, "expected substantial ink, got $inked non-empty bytes")
    }

    @Test
    fun `narrow paper renders narrower, not clipped`() {
        val wide = renderer.render(receipt, MonoBitmap.WIDTH_80MM)
        val narrow = renderer.render(receipt, MonoBitmap.WIDTH_58MM)

        assertEquals(MonoBitmap.WIDTH_58MM, narrow.width)
        assertEquals(48, narrow.rowBytes)
        // Same content, less width — so it needs at least as many rows
        assertTrue(narrow.height >= wide.height - 4, "narrow=${narrow.height} wide=${wide.height}")
    }

    @Test
    fun `the rendered receipt encodes into a well-formed ESC-POS document`() {
        val bitmap = renderer.render(receipt)
        val document = EscPos.document(bitmap)

        val expectedPayload = bitmap.rowBytes * bitmap.height
        // reset(2) + raster header(8) + payload + feed(3) + cut(4)
        assertEquals(2 + 8 + expectedPayload + 3 + 4, document.size)
    }

    @Test
    fun `margins stay clear so nothing is clipped at the paper edge`() {
        val bitmap = renderer.render(receipt)
        for (y in 0 until bitmap.height) {
            assertTrue(!bitmap[0, y], "ink at the very left edge, row $y")
            assertTrue(!bitmap[bitmap.width - 1, y], "ink at the very right edge, row $y")
        }
    }

    /**
     * Writes what the printer would burn, so a human can confirm the Arabic is shaped and ordered
     * correctly. Assertions cannot check that — only eyes can.
     */
    @Test
    fun `render proof sheets for visual inspection`() {
        val out = File("build/print-proofs").apply { mkdirs() }
        write(renderer.renderTestPage(), File(out, "test-page.png"))
        write(renderer.render(receipt), File(out, "receipt.png"))
        assertTrue(File(out, "test-page.png").length() > 0)
        assertTrue(File(out, "receipt.png").length() > 0)
    }

    private fun write(bitmap: MonoBitmap, file: File) {
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                image.setRGB(x, y, if (bitmap[x, y]) 0x000000 else 0xFFFFFF)
            }
        }
        ImageIO.write(image, "png", file)
    }
}
