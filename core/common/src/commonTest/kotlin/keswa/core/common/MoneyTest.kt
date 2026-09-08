package keswa.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    private val egp = CurrencyCode.EGP

    @Test
    fun `addition and subtraction stay in minor units`() {
        val a = Money(1050, egp) // 10.50
        val b = Money(250, egp)  // 2.50
        assertEquals(Money(1300, egp), a + b)
        assertEquals(Money(800, egp), a - b)
    }

    @Test
    fun `mixed currency arithmetic is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Money(100, egp) + Money(100, CurrencyCode.USD)
        }
    }

    @Test
    fun `times multiplies by an integer quantity`() {
        assertEquals(Money(3000, egp), Money(1000, egp) * 3)
    }

    @Test
    fun `comparison respects minor units`() {
        assertTrue(Money(101, egp) > Money(100, egp))
        assertTrue(Money(99, egp) < Money(100, egp))
    }

    @Test
    fun `allocate splits an amount so parts sum exactly to the total`() {
        // 100 minor units split 1:1:1 -> largest-remainder gives 34/33/33, summing to 100.
        val parts = Money(100, egp).allocate(listOf(1, 1, 1))
        assertEquals(3, parts.size)
        assertEquals(100L, parts.sumOf { it.minorUnits })
        assertEquals(setOf(34L, 33L), parts.map { it.minorUnits }.toSet())
    }

    @Test
    fun `allocate weights by line value not equally`() {
        // 1000 split across weights 70:20:10 -> 700/200/100 exactly, no remainder in play.
        val parts = Money(1000, egp).allocate(listOf(70, 20, 10))
        assertEquals(listOf(700L, 200L, 100L), parts.map { it.minorUnits })
    }

    @Test
    fun `allocate handles a refund (negative total) by taking back from the largest shares`() {
        val parts = Money(-100, egp).allocate(listOf(1, 1, 1))
        assertEquals(-100L, parts.sumOf { it.minorUnits })
    }

    @Test
    fun `allocate rejects an all-zero weight list`() {
        assertFailsWith<IllegalArgumentException> {
            Money(100, egp).allocate(listOf(0, 0))
        }
    }

    @Test
    fun `zero and sign helpers`() {
        assertTrue(Money.zero(egp).isZero)
        assertTrue(Money(-1, egp).isNegative)
        assertTrue(Money(1, egp).isPositive)
    }

    @Test
    fun `iterable sum folds over a currency`() {
        val total = listOf(Money(100, egp), Money(250, egp), Money(50, egp)).sum(egp)
        assertEquals(Money(400, egp), total)
    }
}
