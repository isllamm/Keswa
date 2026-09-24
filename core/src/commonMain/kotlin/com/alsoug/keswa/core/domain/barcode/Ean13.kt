package com.alsoug.keswa.core.domain.barcode

/**
 * EAN-13 barcodes for codes the shop prints itself.
 *
 * A real EAN-13 rather than an arbitrary string, for two reasons: every cheap scanner reads one
 * without configuration, and the **in-store prefix range 20–29** is reserved by GS1 for exactly
 * this, so a generated code can never collide with a manufacturer's GTIN.
 */
object Ean13 {

    const val LENGTH = 13
    private const val DATA_LENGTH = 12
    private val IN_STORE_PREFIXES = (20..29).map { it.toString() }

    /**
     * The check digit for 12 data digits: alternating weights of 1 and 3 from the left, then
     * whatever takes the total to the next multiple of ten.
     */
    fun checkDigit(data: String): Int {
        require(data.length == DATA_LENGTH) { "EAN-13 needs $DATA_LENGTH data digits, got ${data.length}" }
        require(data.all { it.isDigit() }) { "EAN-13 data must be digits only" }

        val sum = data.foldIndexed(0) { index, acc, char ->
            acc + (char - '0') * if (index % 2 == 0) 1 else 3
        }
        return (10 - sum % 10) % 10
    }

    /** Appends the check digit to 12 data digits. */
    fun complete(data: String): String = data + checkDigit(data)

    fun isValid(barcode: String): Boolean =
        barcode.length == LENGTH &&
            barcode.all { it.isDigit() } &&
            checkDigit(barcode.take(DATA_LENGTH)) == (barcode.last() - '0')

    fun isInStore(barcode: String): Boolean =
        isValid(barcode) && IN_STORE_PREFIXES.any { barcode.startsWith(it) }

    /**
     * Builds an in-store code from [prefix] and a [sequence], zero-padded to fill the data digits.
     *
     * [sequence] is the shop's own counter — one per variant it prints a tag for.
     */
    fun inStore(sequence: Long, prefix: String = IN_STORE_PREFIXES.first()): String {
        require(prefix in IN_STORE_PREFIXES) { "prefix must be in the in-store range 20–29" }
        val body = sequence.toString()
        val padding = DATA_LENGTH - prefix.length
        require(body.length <= padding) { "sequence $sequence does not fit in $padding digits" }
        return complete(prefix + body.padStart(padding, '0'))
    }
}
