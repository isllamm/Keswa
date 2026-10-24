package com.alsoug.keswa.features.sell.domain.usecase

import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.money.allocate
import com.alsoug.keswa.core.domain.money.taxIncludedAt
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.model.BasketTotals
import com.alsoug.keswa.features.sell.domain.model.LineTotals

/**
 * Turns a cart into money.
 *
 * ADR-021 in its most literal form: a ViewModel must not do this. Every figure on the screen, the
 * receipt and the sale row comes from one function, so they cannot disagree.
 *
 * The order is fixed and matters:
 *
 * ```
 * line gross     = unitPrice × quantity
 * line net       = gross − lineDiscount
 * subtotal       = Σ line gross
 * order discount = allocated across lines by line-net weight
 * line total     = line net − allocated share
 * total          = Σ line total          == subtotal − (Σ lineDiscount + orderDiscount)
 * tax            = total.taxIncludedAt(rate), then allocated across lines by line-total weight
 * ```
 *
 * Both allocations use largest-remainder, so the lines always sum back to the header. A receipt
 * whose lines add to a piastre less than the total printed beneath them is the classic POS bug,
 * and it is a rounding decision made in the wrong place.
 *
 * Tax is *extracted* rather than added: Egyptian retail prices are VAT-inclusive.
 */
class CalculateBasketTotalUseCase {

    operator fun invoke(basket: Basket, vatBasisPoints: Int): BasketTotals {
        if (basket.isEmpty) return BasketTotals.EMPTY

        val subtotal = basket.lines.fold(Money.ZERO) { sum, line -> sum + line.gross }
        val lineDiscounts = basket.lines.fold(Money.ZERO) { sum, line -> sum + line.lineDiscount }

        val netWeights = basket.lines.map { it.net.piastres.toInt() }
        val allocatedDiscount = allocateOrZero(basket.orderDiscount, netWeights)

        val lineTotals = basket.lines.mapIndexed { index, line ->
            line.net - allocatedDiscount[index]
        }
        val total = lineTotals.fold(Money.ZERO) { sum, amount -> sum + amount }

        val tax = if (vatBasisPoints > 0) total.taxIncludedAt(vatBasisPoints) else Money.ZERO
        val allocatedTax = allocateOrZero(tax, lineTotals.map { it.piastres.toInt() })

        return BasketTotals(
            subtotal = subtotal,
            discount = lineDiscounts + basket.orderDiscount,
            tax = tax,
            total = total,
            itemCount = basket.itemCount,
            lines = basket.lines.mapIndexed { index, line ->
                LineTotals(
                    line = line,
                    orderDiscount = allocatedDiscount[index],
                    lineTotal = lineTotals[index],
                    tax = allocatedTax[index],
                )
            },
        )
    }

    /**
     * `Money.allocate` needs weights that sum to more than zero, which a cart of free items or a
     * zero discount does not give it. Both are ordinary states at a till, not errors.
     */
    private fun allocateOrZero(amount: Money, weights: List<Int>): List<Money> =
        if (amount.isZero || weights.sumOf { it.toLong() } <= 0L) {
            List(weights.size) { Money.ZERO }
        } else {
            amount.allocate(weights)
        }
}
