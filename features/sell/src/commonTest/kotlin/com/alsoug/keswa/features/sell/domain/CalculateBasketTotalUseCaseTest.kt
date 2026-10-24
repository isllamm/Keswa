package com.alsoug.keswa.features.sell.domain

import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.usecase.CalculateBasketTotalUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The check that matters most in the whole phase.
 *
 * A till that writes half a sale is recoverable; a till that loses a piastre in rounding is not
 * noticed for months and then cannot be explained.
 */
class CalculateBasketTotalUseCaseTest {

    private val calculate = CalculateBasketTotalUseCase()

    /** One line per pair, so a repeated price does not quietly merge into the line before it. */
    private fun basket(vararg prices: Pair<Long, Int>): Basket =
        prices.foldIndexed(Basket()) { index, basket, (pounds, quantity) ->
            basket.add(sellable(variantId = "var-$index", price = Money.ofPounds(pounds)), quantity)
        }

    @Test
    fun `an empty basket has nothing in it`() {
        val totals = calculate(Basket(), vatBasisPoints = 1_400)

        assertEquals(Money.ZERO, totals.total)
        assertEquals(0, totals.itemCount)
        assertTrue(totals.lines.isEmpty())
    }

    @Test
    fun `lines always sum to the header`() {
        // Given three prices that do not divide evenly by any discount
        val basket = basket(180L to 2, 340L to 3, 95L to 1).withOrderDiscount(Money.ofPounds(100), "admin")

        val totals = calculate(basket, vatBasisPoints = 1_400)

        val lineSum = totals.lines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal }
        val taxSum = totals.lines.fold(Money.ZERO) { sum, line -> sum + line.tax }
        assertEquals(totals.total, lineSum, "lines must add up to the printed total")
        assertEquals(totals.tax, taxSum, "the VAT lines must add up to the VAT on the header")
    }

    @Test
    fun `subtotal minus discount is exactly the total`() {
        val basket = basket(180L to 2, 340L to 3)
            .withLineDiscount(0, Money.ofPounds(30), "admin")
            .withOrderDiscount(Money.ofPounds(77), "admin")

        val totals = calculate(basket, vatBasisPoints = 0)

        assertEquals(totals.subtotal - totals.discount, totals.total)
    }

    @Test
    fun `an order discount that cannot divide evenly still sums back to the whole`() {
        // 10 across three equal lines: 3.34 / 3.33 / 3.33, never 3.33 three times.
        val basket = basket(100L to 1, 100L to 1, 100L to 1).withOrderDiscount(Money.ofPiastres(1_000), "admin")

        val totals = calculate(basket, vatBasisPoints = 0)

        val allocated = totals.lines.fold(Money.ZERO) { sum, line -> sum + line.orderDiscount }
        assertEquals(Money.ofPiastres(1_000), allocated)
        // Deterministic: the leftover piastre goes to the earliest line, not wherever it lands.
        assertEquals(
            listOf(334L, 333L, 333L),
            totals.lines.map { it.orderDiscount.piastres },
        )
    }

    @Test
    fun `VAT is extracted from the price, not added to it`() {
        // Egyptian retail prices are VAT-inclusive: 114 at 14% carries 14 of tax, not 15.96.
        val basket = basket(114L to 1)

        val totals = calculate(basket, vatBasisPoints = 1_400)

        assertEquals(Money.ofPounds(114), totals.total)
        assertEquals(Money.ofPounds(14), totals.tax)
    }

    @Test
    fun `an unregistered shop shows no VAT at all`() {
        val totals = calculate(basket(180L to 2), vatBasisPoints = 0)

        assertEquals(Money.ZERO, totals.tax)
        assertEquals(Money.ofPounds(360), totals.total)
    }

    @Test
    fun `a free basket does not fall over on allocation`() {
        // Weights summing to zero would make largest-remainder allocation meaningless. Giving
        // something away is an ordinary state at a till, not an error.
        val basket = basket(0L to 1, 0L to 2)

        val totals = calculate(basket, vatBasisPoints = 1_400)

        assertEquals(Money.ZERO, totals.total)
        assertEquals(Money.ZERO, totals.tax)
        assertEquals(3, totals.itemCount)
    }

    @Test
    fun `a line discount comes off before the order discount is spread`() {
        val basket = basket(100L to 1, 100L to 1)
            .withLineDiscount(0, Money.ofPounds(50), "admin")
            .withOrderDiscount(Money.ofPounds(30), "admin")

        val totals = calculate(basket, vatBasisPoints = 0)

        // Nets are 50 and 100, so the 30 splits 10/20 rather than 15/15.
        assertEquals(listOf(1_000L, 2_000L), totals.lines.map { it.orderDiscount.piastres })
        assertEquals(Money.ofPounds(120), totals.total)
    }

    @Test
    fun `scanning the same item twice bumps the quantity rather than adding a line`() {
        val item = sellable()
        val basket = Basket().add(item).add(item)

        assertEquals(1, basket.lines.size)
        assertEquals(2, basket.lines.single().quantity)
    }

    @Test
    fun `an overridden line stays separate from the same item at list price`() {
        // Two customers, one haggled: those must not merge, or the discount silently spreads.
        val item = sellable()
        val basket = Basket().add(item).withUnitPrice(0, Money.ofPounds(150), "admin").add(item)

        assertEquals(2, basket.lines.size)
        assertEquals(Money.ofPounds(150), basket.lines[0].unitPrice)
        assertEquals(Money.ofPounds(180), basket.lines[1].unitPrice)
    }

    @Test
    fun `removing a line shrinks an order discount that no longer fits`() {
        val basket = basket(100L to 1, 100L to 1).withOrderDiscount(Money.ofPounds(150), "admin")

        val smaller = basket.removeAt(1)

        assertEquals(Money.ofPounds(100), smaller.orderDiscount)
        assertEquals(Money.ZERO, calculate(smaller, vatBasisPoints = 0).total)
    }
}
