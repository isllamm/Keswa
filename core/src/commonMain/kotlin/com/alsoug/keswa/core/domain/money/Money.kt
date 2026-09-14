package com.alsoug.keswa.core.domain.money

/**
 * An exact amount of money, held as integer minor units (piastres).
 *
 * KD-001: integer arithmetic cannot drift, so a till can compute line totals, discounts, VAT and
 * change due offline without losing a piastre. `Double` and `Float` are prohibited for money.
 *
 * Division is the only lossy operation and is not an operator — see `MoneyAllocation.kt`, where
 * every rounding decision is named.
 */
@JvmInline
value class Money private constructor(val piastres: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(piastres + other.piastres)

    operator fun minus(other: Money): Money = Money(piastres - other.piastres)

    operator fun times(quantity: Int): Money = Money(piastres * quantity)

    operator fun unaryMinus(): Money = Money(-piastres)

    override fun compareTo(other: Money): Int = piastres.compareTo(other.piastres)

    val isZero: Boolean get() = piastres == 0L

    val isNegative: Boolean get() = piastres < 0L

    /**
     * Formats as `#,##0.00` — grouped thousands, always two decimals, no currency symbol.
     * Works on digits rather than going through a floating-point type.
     */
    fun format(): String {
        val negative = piastres < 0
        // Long.MIN_VALUE has no positive counterpart; take the absolute value in unsigned space.
        val abs = if (negative) piastres.toULong().let { (0UL - it) } else piastres.toULong()
        val units = abs / 100UL
        val fraction = (abs % 100UL).toString().padStart(2, '0')

        val digits = units.toString()
        val grouped = buildString {
            for ((index, ch) in digits.withIndex()) {
                if (index > 0 && (digits.length - index) % GROUP_SIZE == 0) append(',')
                append(ch)
            }
        }
        return buildString {
            if (negative) append('-')
            append(grouped)
            append('.')
            append(fraction)
        }
    }

    override fun toString(): String = format()

    companion object {
        val ZERO: Money = Money(0)

        private const val GROUP_SIZE = 3
        private const val MINOR_UNITS = 100L

        /** The canonical constructor — the stored representation is always minor units. */
        fun ofPiastres(value: Long): Money = Money(value)

        fun ofPounds(value: Long): Money = Money(value * MINOR_UNITS)

        /**
         * Parses a decimal string such as `"1,234.56"`, `"-12.5"` or `"40"`, exactly.
         *
         * Returns `null` for anything that is not a well-formed amount with at most two decimal
         * places — including a third decimal, which is rejected rather than silently rounded,
         * because quietly changing a number the user typed is worse than refusing it.
         */
        fun parse(text: String): Money? {
            val cleaned = text.trim().replace(",", "").replace(" ", "")
            val match = PATTERN.matchEntire(cleaned) ?: return null
            val (sign, whole, fraction) = match.destructured

            val units = whole.toLongOrNull() ?: return null
            val minor = fraction.padEnd(2, '0').toLongOrNull() ?: return null

            val magnitude = units * MINOR_UNITS + minor
            return Money(if (sign == "-") -magnitude else magnitude)
        }

        private val PATTERN = Regex("""([+-])?(\d+)(?:\.(\d{1,2}))?""")
    }
}
