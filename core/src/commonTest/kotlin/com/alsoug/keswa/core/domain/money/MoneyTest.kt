package com.alsoug.keswa.core.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `adding tenths is exact where floating point is not`() {
        // Given the canonical floating-point failure: 0.1 + 0.2 != 0.3
        val tenth = Money.parse("0.10")!!
        val fifth = Money.parse("0.20")!!

        // When they are added
        val sum = tenth + fifth

        // Then the result is exactly thirty piastres
        assertEquals(30L, sum.piastres)
        assertEquals("0.30", sum.format())
    }

    @Test
    fun `parse accepts grouped, signed and short-fraction input`() {
        assertEquals(123456L, Money.parse("1,234.56")!!.piastres)
        assertEquals(-1250L, Money.parse("-12.5")!!.piastres)
        assertEquals(4000L, Money.parse("40")!!.piastres)
        assertEquals(0L, Money.parse("0.00")!!.piastres)
    }

    @Test
    fun `parse rejects malformed input rather than guessing`() {
        // A third decimal is refused, not silently rounded — quietly changing a typed number
        // is worse than refusing it.
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("--5"))
    }

    @Test
    fun `format round-trips through parse`() {
        // Given a spread of magnitudes including negatives and group boundaries
        val samples = listOf(0L, 5L, 99L, 100L, 999_99L, 1_000_00L, 1_234_567_89L, -1_234_56L)

        samples.forEach { piastres ->
            val money = Money.ofPiastres(piastres)

            // When formatted and parsed back
            val restored = Money.parse(money.format())

            // Then nothing is lost
            assertEquals(money, restored, "round trip failed for $piastres")
        }
    }

    @Test
    fun `format groups thousands and keeps two decimals`() {
        assertEquals("0.00", Money.ZERO.format())
        assertEquals("0.05", Money.ofPiastres(5).format())
        assertEquals("1,234.56", Money.ofPiastres(123456).format())
        assertEquals("-1,234.56", Money.ofPiastres(-123456).format())
        assertEquals("1,000,000.00", Money.ofPounds(1_000_000).format())
    }

    @Test
    fun `arithmetic holds at the extremes`() {
        // Given the largest representable amount
        val max = Money.ofPiastres(Long.MAX_VALUE)

        // Then it still formats without overflowing into a negative
        assertTrue(max.format().startsWith("92,233,720,368,547,758.07"))

        // And the most negative value — which has no positive counterpart — formats correctly
        assertEquals("-92,233,720,368,547,758.08", Money.ofPiastres(Long.MIN_VALUE).format())
    }

    @Test
    fun `comparison and sign helpers behave`() {
        assertTrue(Money.ofPiastres(100) > Money.ofPiastres(99))
        assertTrue(Money.ZERO.isZero)
        assertTrue(Money.ofPiastres(-1).isNegative)
        assertEquals(Money.ofPiastres(-100), -Money.ofPiastres(100))
    }
}
