package keswa.core.common

/**
 * ISO 4217 currency code plus the data needed to convert minor units to a display amount.
 * [minorUnitExponent] is looked up here, not hardcoded at call sites, because it varies
 * (2 for EGP/USD, 3 for KWD/JOD, 0 for JPY) — see ADR-002.
 */
@JvmInline
value class CurrencyCode(val iso: String) {
    init {
        require(iso.length == 3 && iso.all { it.isUpperCase() && it.isLetter() }) {
            "Currency code must be 3 uppercase letters, got '$iso'"
        }
    }

    val minorUnitExponent: Int get() = EXPONENTS[iso] ?: DEFAULT_EXPONENT

    companion object {
        private const val DEFAULT_EXPONENT = 2

        // Extend as new currencies are actually needed. Absence just means the 2-decimal default applies.
        private val EXPONENTS: Map<String, Int> = mapOf(
            "EGP" to 2, "USD" to 2, "EUR" to 2, "GBP" to 2, "SAR" to 2, "AED" to 2, "QAR" to 2,
            "MAD" to 2, "DZD" to 2, "LBP" to 2, "TRY" to 2,
            "KWD" to 3, "BHD" to 3, "OMR" to 3, "JOD" to 3, // three-decimal currencies
            "JPY" to 0, "KRW" to 0, // zero-decimal currencies
        )

        val EGP = CurrencyCode("EGP")
        val USD = CurrencyCode("USD")
    }
}
