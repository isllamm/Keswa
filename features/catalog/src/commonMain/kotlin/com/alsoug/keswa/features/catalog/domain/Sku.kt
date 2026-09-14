package com.alsoug.keswa.features.catalog.domain

/**
 * Human-readable SKUs, derived from the product and colour names.
 *
 * Readable rather than opaque because a SKU is what staff read off a tag when the scanner fails —
 * `TSH-NAV` tells them something, a UUID does not. Uniqueness is still enforced by the database's
 * unique index; [disambiguate] handles the collision case.
 */
object Sku {

    fun of(productName: String, colourName: String): String =
        "${code(productName, 3)}-${code(colourName, 3)}"

    /** Appends a numeric suffix for the rare genuine collision ("Tee"/"Teal" style). */
    fun disambiguate(base: String, attempt: Int): String =
        if (attempt <= 1) base else "$base-$attempt"

    private fun code(source: String, length: Int): String {
        val letters = source.filter { it.isLetterOrDigit() }.uppercase()
        return if (letters.isEmpty()) "X".repeat(length) else letters.take(length).padEnd(length, 'X')
    }
}
