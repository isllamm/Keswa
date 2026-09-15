package com.alsoug.keswa.core.printing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonoBitmapTest {

    @Test
    fun `rows are padded up to whole bytes`() {
        // Given widths either side of a byte boundary
        assertEquals(1, MonoBitmap(8, 1).rowBytes)
        assertEquals(2, MonoBitmap(9, 1).rowBytes)
        // 12 dots still occupies 2 bytes — the last 4 bits are padding the printer expects
        assertEquals(2, MonoBitmap(12, 1).rowBytes)
        assertEquals(72, MonoBitmap(MonoBitmap.WIDTH_80MM, 1).rowBytes)
        assertEquals(48, MonoBitmap(MonoBitmap.WIDTH_58MM, 1).rowBytes)
    }

    @Test
    fun `pixels pack most significant bit first`() {
        // Given the leftmost dot of a row
        val bitmap = MonoBitmap(8, 1)
        bitmap[0, 0] = true

        // Then it is the high bit, which is the order the raster command reads
        assertEquals("80", bitmap.data.hex())

        bitmap[7, 0] = true
        assertEquals("81", bitmap.data.hex())
    }

    @Test
    fun `padding bits stay clear when the width is not a multiple of eight`() {
        // Given a 12-dot row with every real dot set
        val bitmap = MonoBitmap(12, 1)
        repeat(12) { bitmap[it, 0] = true }

        // Then the four padding bits remain zero — white paper, not a black smear
        assertEquals("FF F0", bitmap.data.hex())
    }

    @Test
    fun `reads and writes outside the bitmap are ignored rather than crashing`() {
        val bitmap = MonoBitmap(8, 1)
        bitmap[99, 0] = true
        bitmap[-1, 0] = true
        assertEquals("00", bitmap.data.hex())
        assertFalse(bitmap[99, 0])
        assertFalse(bitmap[0, 99])
    }

    @Test
    fun `set and clear round-trip`() {
        val bitmap = MonoBitmap(16, 2)
        bitmap[3, 1] = true
        assertTrue(bitmap[3, 1])
        bitmap[3, 1] = false
        assertFalse(bitmap[3, 1])
        assertEquals("00 00 00 00", bitmap.data.hex())
    }

    @Test
    fun `a bitmap must have positive dimensions`() {
        assertFailsWith<IllegalArgumentException> { MonoBitmap(0, 10) }
        assertFailsWith<IllegalArgumentException> { MonoBitmap(10, -1) }
    }
}
