package com.alsoug.keswa.core.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyAllocationTest {

    @Test
    fun `percentage rounds half to even`() {
        // Given amounts whose half-percent lands exactly on a half piastre
        // 5 piastres at 50% = 2.5 -> 2 (2 is already even)
        assertEquals(2L, Money.ofPiastres(5).percentage(5_000).piastres)
        // 15 piastres at 50% = 7.5 -> 8 (7 is odd, so round away)
        assertEquals(8L, Money.ofPiastres(15).percentage(5_000).piastres)
    }

    @Test
    fun `percentage rounds half to even for negative amounts too`() {
        assertEquals(-2L, Money.ofPiastres(-5).percentage(5_000).piastres)
        assertEquals(-8L, Money.ofPiastres(-15).percentage(5_000).piastres)
    }

    @Test
    fun `percentage rounds up above the half and down below it`() {
        // 3 piastres at 19% = 0.57 -> 1
        assertEquals(1L, Money.ofPiastres(3).percentage(1_900).piastres)
        // 3 piastres at 14% = 0.42 -> 0
        assertEquals(0L, Money.ofPiastres(3).percentage(1_400).piastres)
    }

    @Test
    fun `percentage is exact when no rounding is needed`() {
        // A 5% discount on EGP 100.00
        assertEquals(500L, Money.ofPiastres(10_000).percentage(500).piastres)
        assertEquals(13L, Money.ofPiastres(26).percentage(5_000).piastres)
    }

    @Test
    fun `tax included at 14 percent extracts rather than adds`() {
        // Given an Egyptian retail price, which is VAT-inclusive
        val gross = Money.parse("749.00")!!

        // When the included VAT is extracted
        val vat = gross.taxIncludedAt(1_400)

        // Then it is the gross minus gross/1.14 — 91.98, not 14% of gross (104.86)
        assertEquals("91.98", vat.format())
    }

    @Test
    fun `allocate always sums back to the original`() {
        // Given amounts and weightings chosen to force rounding
        val cases = listOf(
            Money.ofPiastres(100) to listOf(1, 1, 1),
            Money.ofPiastres(1) to listOf(1, 1, 1),
            Money.ofPiastres(10_000) to listOf(3, 3, 3, 1),
            Money.ofPiastres(999) to listOf(7, 11, 13),
            Money.ofPiastres(74_900) to listOf(49_900, 25_000),
            Money.ofPiastres(-1_337) to listOf(5, 3, 1),
        )

        cases.forEach { (total, weights) ->
            // When the amount is split
            val parts = total.allocate(weights)

            // Then the parts reconcile exactly — no piastre created or lost
            val sum = parts.fold(Money.ZERO) { acc, part -> acc + part }
            assertEquals(total, sum, "allocate($weights) of $total did not reconcile")
            assertEquals(weights.size, parts.size)
        }
    }

    @Test
    fun `allocate gives leftover piastres to the largest remainders`() {
        // Given one piastre split three ways
        val parts = Money.ofPiastres(100).allocate(listOf(1, 1, 1))

        // Then the odd piastre goes to the earliest lines, deterministically
        assertEquals(listOf(34L, 33L, 33L), parts.map { it.piastres })
    }

    @Test
    fun `allocate refuses inputs it cannot split`() {
        assertFailsWith<IllegalArgumentException> { Money.ofPiastres(100).allocate(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Money.ofPiastres(100).allocate(listOf(0, 0)) }
        assertFailsWith<IllegalArgumentException> { Money.ofPiastres(100).allocate(listOf(1, -1)) }
    }
}
