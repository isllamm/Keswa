package com.alsoug.keswa.features.analytics.domain

import com.alsoug.keswa.features.analytics.presentation.components.abbreviate
import com.alsoug.keswa.features.analytics.presentation.components.niceCeiling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Axis ticks on round numbers, at every magnitude.
 *
 * The plan's check 7. A grid line at 63,482 makes the reader do arithmetic to compare two panels,
 * and two panels that cannot be compared at a glance are two panels nobody reads.
 */
class ChartTokensTest {

    @Test
    fun `a ceiling is always a round number at or above the data`() {
        // 1, 2, 5, 10 × the magnitude — the conventional set, and the one whose halfway tick is
        // also round, which matters because the axis shows ceiling, ceiling/2 and zero.
        val cases = listOf(
            1L to 1L,
            7L to 10L,
            10L to 10L,
            42L to 50L,
            63L to 100L,
            120L to 200L,
            999L to 1_000L,
            1_001L to 2_000L,
            63_482L to 100_000L,
            200_000L to 200_000L,
        )

        cases.forEach { (value, expected) ->
            assertEquals(expected, niceCeiling(value), "ceiling for $value")
        }
    }

    @Test
    fun `a ceiling never falls below its data`() {
        // The failure that matters: a bar drawn taller than the axis it is measured against.
        (1L..2_000L).forEach { value ->
            assertTrue(niceCeiling(value) >= value, "ceiling for $value clipped the data")
        }
    }

    @Test
    fun `an empty chart still has an axis`() {
        // Zero would divide by zero in every bar's height. A day-one install hits this first.
        assertEquals(1L, niceCeiling(0))
        assertEquals(1L, niceCeiling(-5))
    }

    @Test
    fun `a ceiling is one of the nice multiples`() {
        // Not merely ≥ the data: an axis topping out at 63,482 makes the reader do arithmetic.
        (1L..5_000L).forEach { value ->
            val ceiling = niceCeiling(value)
            var magnitude = 1L
            while (magnitude * 10 <= ceiling) magnitude *= 10
            assertTrue(
                ceiling / magnitude in listOf(1L, 2L, 5L) && ceiling % magnitude == 0L,
                "ceiling $ceiling for $value is not a round number",
            )
        }
    }

    @Test
    fun `axis labels abbreviate rather than wrap`() {
        assertEquals("0", abbreviate(0))
        assertEquals("940", abbreviate(940))
        assertEquals("1K", abbreviate(1_000))
        assertEquals("63K", abbreviate(63_482))
        assertEquals("2M", abbreviate(2_400_000))
    }
}
