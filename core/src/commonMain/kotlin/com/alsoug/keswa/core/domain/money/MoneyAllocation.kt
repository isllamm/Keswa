package com.alsoug.keswa.core.domain.money

/**
 * Divides [numerator] by [denominator], rounding half to even.
 *
 * HALF_EVEN matches the semantics `kmp_cashimobile` already established for money formatting, and
 * avoids the upward bias that half-up accumulates across many lines.
 */
private fun divideHalfEven(numerator: Long, denominator: Long): Long {
    require(denominator > 0) { "denominator must be positive" }

    val quotient = numerator / denominator
    val remainder = numerator % denominator
    if (remainder == 0L) return quotient

    val twiceRemainder = 2 * (if (remainder < 0) -remainder else remainder)
    val awayFromZero = if (numerator < 0) quotient - 1 else quotient + 1

    return when {
        twiceRemainder > denominator -> awayFromZero
        twiceRemainder < denominator -> quotient
        // Exactly half — keep the last digit even.
        quotient % 2 == 0L -> quotient
        else -> awayFromZero
    }
}

/**
 * Takes [basisPoints] hundredths of a percent of this amount, rounding half to even.
 *
 * Basis points rather than a percentage so the caller cannot pass a fraction: 14% VAT is `1_400`,
 * a 5% discount is `500`.
 */
fun Money.percentage(basisPoints: Int): Money =
    Money.ofPiastres(divideHalfEven(piastres * basisPoints, 10_000L))

/**
 * Extracts the tax already included in this amount at [basisPoints].
 *
 * Egyptian retail prices are VAT-inclusive, so the tax line on a receipt is the amount *minus*
 * the amount divided by (1 + rate) — not the amount times the rate, which over-states it.
 */
fun Money.taxIncludedAt(basisPoints: Int): Money {
    val net = Money.ofPiastres(divideHalfEven(piastres * 10_000L, 10_000L + basisPoints))
    return this - net
}

/**
 * Splits this amount across [weights] so the parts sum **exactly** back to the original.
 *
 * Uses the largest-remainder method: the piastres that rounding would lose go to the parts with
 * the largest remainders rather than vanishing. Without this an order-level discount spread over
 * lines leaves the receipt total disagreeing with the sum of its lines — the classic POS bug.
 *
 * Ties go to the earlier line, so the result is deterministic for a given input.
 *
 * Assumes amounts within a shop's realistic range; `amount × weight` is not overflow-guarded.
 */
fun Money.allocate(weights: List<Int>): List<Money> {
    require(weights.isNotEmpty()) { "weights must not be empty" }
    require(weights.all { it >= 0 }) { "weights must not be negative" }

    val totalWeight = weights.sumOf { it.toLong() }
    require(totalWeight > 0) { "weights must sum to more than zero" }

    val negative = piastres < 0
    val amount = if (negative) -piastres else piastres

    val parts = LongArray(weights.size)
    val remainders = LongArray(weights.size)
    var allocated = 0L

    for (index in weights.indices) {
        val numerator = amount * weights[index]
        parts[index] = numerator / totalWeight
        remainders[index] = numerator % totalWeight
        allocated += parts[index]
    }

    val order = weights.indices.sortedWith(
        compareByDescending<Int> { remainders[it] }.thenBy { it },
    )
    var leftover = amount - allocated
    var cursor = 0
    while (leftover > 0) {
        parts[order[cursor % order.size]] += 1
        leftover -= 1
        cursor += 1
    }

    return parts.map { Money.ofPiastres(if (negative) -it else it) }
}
