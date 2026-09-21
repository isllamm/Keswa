package com.alsoug.keswa.core.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KD-008's arithmetic. Getting this wrong misstates every margin the shop ever reports, quietly.
 */
class MovingAverageTest {

    @Test
    fun `equal quantities average to the midpoint`() {
        val cost = movingAverageCost(
            onHand = 10,
            currentCost = Money.ofPounds(100),
            receivedQuantity = 10,
            receiptCost = Money.ofPounds(140),
        )

        assertEquals(Money.ofPounds(120), cost)
    }

    @Test
    fun `the larger side pulls harder`() {
        // 30 at 100 and 10 at 140 → 110, not 120.
        val cost = movingAverageCost(
            onHand = 30,
            currentCost = Money.ofPounds(100),
            receivedQuantity = 10,
            receiptCost = Money.ofPounds(140),
        )

        assertEquals(Money.ofPounds(110), cost)
    }

    @Test
    fun `an empty shelf takes the delivery's cost outright`() {
        // The first receipt of a new SKU: there is nothing to average against.
        val cost = movingAverageCost(
            onHand = 0,
            currentCost = Money.ZERO,
            receivedQuantity = 5,
            receiptCost = Money.ofPounds(90),
        )

        assertEquals(Money.ofPounds(90), cost)
    }

    @Test
    fun `negative stock takes the delivery's cost rather than inventing one`() {
        // Phase 5 allows selling below stock, so this is reachable. Averaging against −2 produces a
        // number with no meaning, and a negative cost price is worse than simply taking the newest.
        val cost = movingAverageCost(
            onHand = -2,
            currentCost = Money.ofPounds(100),
            receivedQuantity = 5,
            receiptCost = Money.ofPounds(90),
        )

        assertEquals(Money.ofPounds(90), cost)
    }

    @Test
    fun `a receipt at the same price changes nothing`() {
        val cost = movingAverageCost(
            onHand = 7,
            currentCost = Money.ofPounds(123),
            receivedQuantity = 13,
            receiptCost = Money.ofPounds(123),
        )

        assertEquals(Money.ofPounds(123), cost)
    }

    @Test
    fun `an uneven division rounds half to even`() {
        // 1 at 1.00 and 2 at 1.01 → 3.02 / 3 = 1.00666…, which rounds to 1.01.
        assertEquals(
            Money.ofPiastres(101),
            movingAverageCost(1, Money.ofPiastres(100), 2, Money.ofPiastres(101)),
        )

        // Exactly half: 1 at 1.00 and 1 at 1.01 → 2.01 / 2 = 1.005, and HALF_EVEN keeps the last
        // digit even, so 1.00 rather than 1.01.
        assertEquals(
            Money.ofPiastres(100),
            movingAverageCost(1, Money.ofPiastres(100), 1, Money.ofPiastres(101)),
        )
    }

    @Test
    fun `a receipt must add stock`() {
        assertFailsWith<IllegalArgumentException> {
            movingAverageCost(10, Money.ofPounds(100), 0, Money.ofPounds(120))
        }
        assertFailsWith<IllegalArgumentException> {
            movingAverageCost(10, Money.ofPounds(100), -5, Money.ofPounds(120))
        }
    }

    @Test
    fun `successive deliveries drift towards the newest price`() {
        var onHand = 0
        var cost = Money.ZERO

        listOf(100L to 10, 120L to 10, 140L to 10).forEach { (price, quantity) ->
            cost = movingAverageCost(onHand, cost, quantity, Money.ofPounds(price))
            onHand += quantity
        }

        // (1000 + 1200 + 1400) / 30 = 120
        assertEquals(Money.ofPounds(120), cost)
        assertEquals(30, onHand)
    }
}
