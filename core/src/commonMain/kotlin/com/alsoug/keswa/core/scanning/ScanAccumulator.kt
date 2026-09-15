package com.alsoug.keswa.core.scanning

/**
 * Tells a barcode scan apart from someone typing.
 *
 * A USB or Bluetooth scanner is an HID keyboard — no driver, no SDK, it simply types the code and
 * presses Enter. The only thing distinguishing it from a person is speed: a scanner emits
 * characters milliseconds apart, a human tens or hundreds.
 *
 * Pure logic with an injected clock, so the whole heuristic is testable against synthetic key
 * streams and the thresholds can be tuned against real hardware without touching the UI.
 */
class ScanAccumulator(
    private val maxGapMillis: Long = DEFAULT_MAX_GAP_MILLIS,
    private val minLength: Int = DEFAULT_MIN_LENGTH,
) {

    private val buffer = StringBuilder()
    private var lastAtMillis: Long = Long.MIN_VALUE

    /** What has been accumulated so far — for a UI that wants to show a partial scan. */
    val pending: String get() = buffer.toString()

    /**
     * Feeds one character. A gap wider than [maxGapMillis] means a human is typing, so whatever was
     * buffered is abandoned and this character starts afresh.
     */
    fun onCharacter(character: Char, atMillis: Long) {
        if (buffer.isNotEmpty() && atMillis - lastAtMillis > maxGapMillis) {
            buffer.clear()
        }
        buffer.append(character)
        lastAtMillis = atMillis
    }

    /**
     * Completes a scan, or returns null if what arrived was typing rather than a scan.
     *
     * Null covers three cases worth keeping distinct in your head: nothing buffered, too few
     * characters to be a barcode, and a pause before Enter that means a person pressed it.
     */
    fun onEnter(atMillis: Long): String? {
        val candidate = buffer.toString()
        val gap = atMillis - lastAtMillis
        buffer.clear()
        lastAtMillis = Long.MIN_VALUE
        return candidate.takeIf { it.length >= minLength && gap <= maxGapMillis }
    }

    /** Drops a partial scan — an interrupted swipe should not prefix the next one. */
    fun reset() {
        buffer.clear()
        lastAtMillis = Long.MIN_VALUE
    }

    companion object {
        /**
         * 30 ms between keystrokes. Cheap scanners vary, which is why this is configurable and the
         * first thing to tune on real hardware.
         */
        const val DEFAULT_MAX_GAP_MILLIS = 30L

        /** Shorter than this is a keypress, not a barcode. EAN-8 is the shortest real symbology. */
        const val DEFAULT_MIN_LENGTH = 6
    }
}
