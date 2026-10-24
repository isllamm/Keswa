package com.alsoug.keswa.features.analytics.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.alsoug.keswa.core.designsystem.KeswaTheme

/**
 * The chart palette, and the rules that keep every panel reading as one system.
 *
 * Series colours mean the same thing on every chart: **series 1 is what sold, series 2 is what is
 * still on hand.** Once that holds, the legend stops being something the reader has to re-learn
 * per panel.
 *
 * Two rules that survive from the prototype and are not review-negotiable:
 *
 * - **Text never wears the series colour.** Values and labels use ink; a coloured mark beside them
 *   carries the identity. Coloured text fails contrast at small sizes and reads as a state.
 * - **A legend appears whenever there are two or more series.** One-series charts get none — the
 *   title already names what is plotted.
 *
 * And one that is specific to this shop: **a garment's own colour is a swatch beside the axis
 * label, never a bar's fill.** A beige bar on a beige-and-navy chart cannot be read, and reusing
 * the fill for identity breaks the first rule above.
 */
data class ChartColours(
    val sold: Color,
    val onHand: Color,
    val grid: Color,
    val axisText: Color,
    val surface: Color,
    val good: Color,
    val warning: Color,
    val critical: Color,
    val heatFrom: Color,
    val heatTo: Color,
)

/**
 * Read from the theme, never from a literal here.
 *
 * These used to be two hand-written tables that called `isSystemInDarkTheme()` themselves. They
 * carried the prototype's *series* colours and somebody else's cool-grey neutrals — `#E4E4E7`
 * grid, `#6B7280` axis text, a pure-white surface — so the charts sat on a slightly different
 * system from the page around them. Nobody would have called it a bug; it just never looked like
 * one product.
 */
@Composable
@ReadOnlyComposable
fun chartColours(): ChartColours {
    val semantics = KeswaTheme.semantics
    return ChartColours(
        sold = semantics.sold,
        onHand = semantics.onHand,
        grid = semantics.grid,
        axisText = semantics.muted,
        surface = MaterialTheme.colorScheme.surface,
        good = semantics.good,
        warning = semantics.warning,
        critical = semantics.critical,
        // The sequential ramp's ends. In dark mode the ramp is reversed, so "more" stays the
        // higher-contrast end against whatever the page is.
        heatFrom = semantics.ramp.first(),
        heatTo = semantics.ramp.last(),
    )
}

/**
 * Axis ticks on round numbers — 0, 20K, 40K, 60K — never on the data's own maximum.
 *
 * A grid line at 63,482 is an axis that makes the reader do arithmetic to compare two panels.
 */
fun niceCeiling(value: Long): Long {
    if (value <= 0) return 1

    // Snap to 1, 2, 5 or 10 times the magnitude — the conventional set, and the one that leaves a
    // halfway tick that is also round.
    var magnitude = 1L
    while (magnitude * 10 <= value) magnitude *= 10

    NICE_MULTIPLES.forEach { multiple ->
        val candidate = magnitude * multiple
        if (candidate >= value) return candidate
    }
    return magnitude * 10
}

private val NICE_MULTIPLES = longArrayOf(1, 2, 5, 10)

/** `63482` → `63K`. Axis labels have no room for thousands separators and do not need them. */
fun abbreviate(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}
