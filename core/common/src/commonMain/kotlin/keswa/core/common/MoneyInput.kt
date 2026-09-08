package keswa.core.common

import kotlin.math.pow
import kotlin.math.round

/**
 * Parses a decimal amount typed into a form field ("25.5") into integer minor units. The one
 * place a `Double` is tolerable: transient text parsing, immediately converted to the integer
 * type everything else uses. Returns null for anything that isn't a plain decimal number.
 */
fun parseMoneyInput(text: String, minorUnitExponent: Int = 2): Long? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    if (value < 0) return null
    val scale = 10.0.pow(minorUnitExponent)
    return round(value * scale).toLong()
}
