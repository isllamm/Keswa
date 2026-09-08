package keswa.core.common

/**
 * An exact monetary amount: integer minor units plus a currency. Never a `Double` — see ADR-002.
 * Formatting (grouping, symbol, digit style) happens only at the UI/export rendering edge;
 * this type is deliberately silent about presentation.
 */
data class Money(val minorUnits: Long, val currency: CurrencyCode) {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits + other.minorUnits, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits - other.minorUnits, currency)
    }

    operator fun unaryMinus(): Money = Money(-minorUnits, currency)

    operator fun times(quantity: Int): Money = Money(minorUnits * quantity, currency)

    operator fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isNegative: Boolean get() = minorUnits < 0L
    val isPositive: Boolean get() = minorUnits > 0L

    /**
     * Splits this amount across [weights] (e.g. line totals a document discount is allocated
     * over) so the parts sum to exactly this amount — the largest-remainder method from ADR-002.
     * Every weight must be non-negative and at least one must be positive.
     */
    fun allocate(weights: List<Long>): List<Money> {
        require(weights.isNotEmpty()) { "cannot allocate across zero parts" }
        require(weights.all { it >= 0 }) { "allocation weights must be non-negative" }
        val totalWeight = weights.sum()
        require(totalWeight > 0) { "allocation weights must sum to more than zero" }

        val rawShares = weights.map { (minorUnits * it).toDouble() / totalWeight }
        val floors = rawShares.map { kotlin.math.floor(it).toLong() }
        var remainder = minorUnits - floors.sum()

        val remainders = rawShares.mapIndexed { index, raw -> index to (raw - floors[index]) }
            .sortedByDescending { it.second }

        val amounts = floors.toMutableList()
        var i = 0
        while (remainder > 0 && i < remainders.size) {
            amounts[remainders[i].first] += 1
            remainder -= 1
            i += 1
        }
        // remainder < 0 only if minorUnits itself is negative (a full refund/void); take back
        // from the largest shares first so no single line swings unexpectedly.
        i = 0
        while (remainder < 0 && i < remainders.size) {
            amounts[remainders[i].first] -= 1
            remainder += 1
            i += 1
        }
        return amounts.map { Money(it, currency) }
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "currency mismatch: ${currency.iso} vs ${other.currency.iso}"
        }
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0, currency)
    }
}

fun Iterable<Money>.sum(currency: CurrencyCode): Money =
    fold(Money.zero(currency)) { acc, m -> acc + m }
