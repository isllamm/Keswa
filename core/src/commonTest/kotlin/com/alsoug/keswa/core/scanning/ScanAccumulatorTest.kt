package com.alsoug.keswa.core.scanning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScanAccumulatorTest {

    private val accumulator = ScanAccumulator()

    /** Types [text] with a fixed gap between keys, then Enter after the same gap. */
    private fun type(text: String, gapMillis: Long, startAt: Long = 1_000): String? {
        var now = startAt
        text.forEach { character ->
            accumulator.onCharacter(character, now)
            now += gapMillis
        }
        return accumulator.onEnter(now)
    }

    @Test
    fun `a fast burst ending in Enter is a scan`() {
        // Given a scanner emitting an EAN-13 at 5 ms per character
        val scan = type("2000000000015", gapMillis = 5)

        assertEquals("2000000000015", scan)
    }

    @Test
    fun `human typing at the same keyboard is not a scan`() {
        // Given someone typing the same digits by hand
        val scan = type("2000000000015", gapMillis = 120)

        // Then nothing is emitted — only the last character survived each slow gap
        assertNull(scan)
    }

    @Test
    fun `a pause before Enter means a person pressed it`() {
        // Given a fast burst, then a hesitation
        var now = 1_000L
        "2000000000015".forEach { accumulator.onCharacter(it, now); now += 5 }
        now += 400

        assertNull(accumulator.onEnter(now))
    }

    @Test
    fun `an interrupted scan does not prefix the next one`() {
        // Given a scan that was cut short
        var now = 1_000L
        "20000".forEach { accumulator.onCharacter(it, now); now += 5 }

        // When the operator walks away and scans something else a second later
        now += 1_000
        "5901234123457".forEach { accumulator.onCharacter(it, now); now += 5 }
        val scan = accumulator.onEnter(now)

        // Then only the second barcode comes through
        assertEquals("5901234123457", scan)
    }

    @Test
    fun `too few characters is a keypress, not a barcode`() {
        assertNull(type("123", gapMillis = 5))
        assertNull(type("", gapMillis = 5))
    }

    @Test
    fun `reset drops a partial scan`() {
        accumulator.onCharacter('2', 1_000)
        accumulator.onCharacter('0', 1_005)
        assertEquals("20", accumulator.pending)

        accumulator.reset()

        assertEquals("", accumulator.pending)
        assertNull(accumulator.onEnter(1_010))
    }

    @Test
    fun `back-to-back scans are kept separate`() {
        assertEquals("2000000000015", type("2000000000015", gapMillis = 4, startAt = 1_000))
        assertEquals("5901234123457", type("5901234123457", gapMillis = 4, startAt = 5_000))
    }

    @Test
    fun `thresholds are configurable for slower scanners`() {
        // Given a scanner that emits at 60 ms — slower than the default 30 ms gate
        val tolerant = ScanAccumulator(maxGapMillis = 80)
        var now = 1_000L
        "2000000000015".forEach { tolerant.onCharacter(it, now); now += 60 }

        assertEquals("2000000000015", tolerant.onEnter(now))
    }
}
