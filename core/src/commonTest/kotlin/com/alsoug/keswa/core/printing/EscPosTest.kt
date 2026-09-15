package com.alsoug.keswa.core.printing

import com.alsoug.keswa.core.printing.escpos.EscPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EscPosTest {

    @Test
    fun `control commands match the recorded bytes`() {
        assertEquals("1B 40", EscPos.initialise().hex())
        assertEquals("1B 64 03", EscPos.feed(3).hex())
        assertEquals("1B 70 00 19 FA", EscPos.openDrawer().hex())
        // feed 4 then partial cut
        assertEquals("1B 64 04 1D 56 42 00", EscPos.cut().hex())
    }

    @Test
    fun `raster header carries width in bytes and height in dots`() {
        // Given a 16 x 2 bitmap — 2 bytes per row
        val bitmap = MonoBitmap(16, 2)
        bitmap[0, 0] = true
        bitmap[15, 1] = true

        val bytes = EscPos.raster(bitmap)

        // GS v 0, mode 0, xL xH = 2, yL yH = 2, then the packed rows
        assertEquals("1D 76 30 00 02 00 02 00 80 00 00 01", bytes.hex())
    }

    @Test
    fun `raster sends the padded width, not the dot width`() {
        // Given 12 dots across — the case that silently corrupts output if width is sent in dots
        val bitmap = MonoBitmap(12, 1)
        repeat(12) { bitmap[it, 0] = true }

        val bytes = EscPos.raster(bitmap)

        // Width is 2 bytes, and the padded row travels intact
        assertEquals("1D 76 30 00 02 00 01 00 FF F0", bytes.hex())
    }

    @Test
    fun `a full-width receipt declares 72 bytes per row`() {
        val bitmap = MonoBitmap(MonoBitmap.WIDTH_80MM, 300)

        val header = EscPos.raster(bitmap).take(8).toByteArray()

        // 72 = 0x48 across, 300 = 0x012C tall, little-endian
        assertEquals("1D 76 30 00 48 00 2C 01", header.hex())
    }

    @Test
    fun `a document resets, prints and cuts in that order`() {
        val bitmap = MonoBitmap(8, 1)

        val bytes = EscPos.document(bitmap).hex()

        assertTrue(bytes.startsWith("1B 40"), "must reset first: $bytes")
        assertTrue(bytes.contains("1D 76 30"), "must contain the raster image: $bytes")
        assertTrue(bytes.endsWith("1D 56 42 00"), "must cut last: $bytes")
    }

    @Test
    fun `a document can kick the drawer after cutting`() {
        val bytes = EscPos.document(MonoBitmap(8, 1), openDrawer = true).hex()
        assertTrue(bytes.endsWith("1B 70 00 19 FA"))
    }

    @Test
    fun `feed refuses values the protocol cannot encode`() {
        assertFailsWith<IllegalArgumentException> { EscPos.feed(-1) }
        assertFailsWith<IllegalArgumentException> { EscPos.feed(256) }
    }
}
