package com.alsoug.keswa.core.printing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLabelNameRendererTest {

    private val renderer = DesktopLabelNameRenderer()

    @Test
    fun `Arabic draws black dots, is 304 wide and at most 40 high`() {
        val bitmap = renderer.render("قميص أكسفورد — كحلي", widthDots = 304, maxHeightDots = 40)

        assertEquals(304, bitmap.width)
        assertTrue(bitmap.height in 1..40, "expected height <= 40, got ${bitmap.height}")
        assertTrue(bitmap.hasAnyBlackDot(), "expected rendered Arabic text to draw black dots")
    }

    @Test
    fun `a very long name shrinks or truncates and still fits within 304 x 40`() {
        val longName = "قميص رجالي قطن مصري طويل الأكمام بطبعة كلاسيكية فاخرة — أزرق سماوي فاتح جدًا"
        val bitmap = renderer.render(longName, widthDots = 304, maxHeightDots = 40)

        assertEquals(304, bitmap.width)
        assertTrue(bitmap.height in 1..40, "expected height <= 40, got ${bitmap.height}")
        assertTrue(bitmap.hasAnyBlackDot(), "expected truncated text to draw black dots")
    }

    private fun MonoBitmap.hasAnyBlackDot(): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (this[x, y]) return true
            }
        }
        return false
    }
}
