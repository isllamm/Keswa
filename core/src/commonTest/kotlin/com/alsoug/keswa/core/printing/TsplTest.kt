package com.alsoug.keswa.core.printing

import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.core.printing.tspl.Tspl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TsplTest {

    private val spec = LabelSpec()

    @Test
    fun `a label declares media, content and copies in order`() {
        val label = Tspl.label(
            spec = spec,
            barcode = "2000000000015",
            price = "EGP 499.00",
            sku = "OXF-NAV",
            latinName = "Oxford shirt",
        ).decodeToString()

        val lines = label.trim().lines().map { it.trim() }
        assertEquals("SIZE 40 mm,30 mm", lines[0])
        assertEquals("GAP 2 mm,0 mm", lines[1])
        assertEquals("DENSITY 8", lines[2])
        assertEquals("SPEED 4", lines[3])
        assertEquals("DIRECTION 1", lines[4])
        assertEquals("CLS", lines[5])
        assertTrue(label.contains("""BARCODE 10,110,"EAN13",60,1,0,2,4,"2000000000015""""))
        assertTrue(label.contains("""TEXT 10,78,"2",0,2,2,"EGP 499.00""""))
        assertTrue(label.trim().endsWith("PRINT 1,1"))
    }

    @Test
    fun `quotes in a name cannot break out of the TSPL string`() {
        val label = Tspl.text(x = 0, y = 0, content = """Big "sale" tee""")
        assertTrue(label.contains("""\"sale\""""), label)
    }

    @Test
    fun `a bitmap is sent inverted, because TSPL treats a set bit as white`() {
        // Given one black dot in an 8x1 bitmap
        val bitmap = MonoBitmap(8, 1)
        bitmap[0, 0] = true

        val bytes = Tspl.bitmap(x = 1, y = 2, bitmap = bitmap)
        val header = bytes.decodeToString(0, "BITMAP 1,2,1,1,0,".length)
        val payload = bytes.copyOfRange("BITMAP 1,2,1,1,0,".length, bytes.size - 2)

        assertEquals("BITMAP 1,2,1,1,0,", header)
        // 0x80 black becomes 0x7F once inverted for TSPL
        assertEquals("7F", payload.hex())
    }

    @Test
    fun `inversion can be turned off for printers that follow the other convention`() {
        val bitmap = MonoBitmap(8, 1)
        bitmap[0, 0] = true
        val bytes = Tspl.bitmap(x = 0, y = 0, bitmap = bitmap, invert = false)
        val payload = bytes.copyOfRange("BITMAP 0,0,1,1,0,".length, bytes.size - 2)
        assertEquals("80", payload.hex())
    }

    @Test
    fun `label width in dots follows the configured dpi`() {
        assertEquals(40 * 203 / 25, LabelSpec().widthDots)
        assertEquals(60 * 300 / 25, LabelSpec(widthMm = 60, dpi = 300).widthDots)
    }

    @Test
    fun `copies must be at least one`() {
        assertFailsWith<IllegalArgumentException> {
            Tspl.label(spec, "2000000000015", "0.00", "SKU", copies = 0)
        }
    }
}
