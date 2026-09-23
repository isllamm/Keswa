package com.alsoug.keswa.features.analytics.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import androidx.compose.foundation.background

/**
 * Charts drawn by hand on a Compose `Canvas`.
 *
 * There is no KMP charting library worth the dependency, and this is the same skill the receipt
 * renderer already needed — measure, then draw.
 *
 * The mark specs come from the prototype and are deliberate:
 *
 * | Mark | Spec |
 * |---|---|
 * | Line | 2px, round cap and join; area fill at 10% |
 * | Column | ≤ 24px thick, rounded at the data end, square at the baseline |
 * | Stacked segments | a 2px gap **in the surface colour** — never a stroke |
 * | Grid | 1px solid hairline. Never dashed |
 *
 * A dashed grid competes with the data for attention, and a stroked segment boundary reads as a
 * third series.
 */

private const val STACK_GAP_DP = 2
private const val MAX_COLUMN_DP = 24

/** The revenue trend. One series, so no legend — the title says what is plotted. */
@Composable
fun TrendLine(
    points: List<DailyPoint>,
    modifier: Modifier = Modifier,
) {
    val colours = chartColours()
    if (points.isEmpty()) {
        EmptyPanel("Nothing sold in this period", modifier)
        return
    }

    val ceiling = niceCeiling(points.maxOf { it.amount.piastres })

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AxisLabels(ceiling)
            Canvas(modifier = Modifier.weight(1f).height(160.dp)) {
                drawGrid(colours.grid, size)

                if (points.size == 1) {
                    // A single day is a dot, not a line. A one-point "trend" is a lie.
                    val y = size.height - size.height * (points[0].amount.piastres.toFloat() / ceiling)
                    drawCircle(colours.sold, radius = 5f, center = Offset(size.width / 2f, y))
                    return@Canvas
                }

                val step = size.width / (points.size - 1)
                val coordinates = points.mapIndexed { index, point ->
                    Offset(
                        x = index * step,
                        y = size.height - size.height * (point.amount.piastres.toFloat() / ceiling),
                    )
                }

                // Area fill first, at 10% — it sits behind the stroke, never over it.
                val area = Path().apply {
                    moveTo(coordinates.first().x, size.height)
                    coordinates.forEach { lineTo(it.x, it.y) }
                    lineTo(coordinates.last().x, size.height)
                    close()
                }
                drawPath(area, colours.sold.copy(alpha = 0.1f))

                val line = Path().apply {
                    moveTo(coordinates.first().x, coordinates.first().y)
                    coordinates.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    line,
                    colours.sold,
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // End marker: ≥ 8px with a 2px surface ring, so the latest value is findable.
                val last = coordinates.last()
                drawCircle(colours.surface, radius = 6f, center = last)
                drawCircle(colours.sold, radius = 4f, center = last)
            }
        }
        Text(
            "${points.first().day} — ${points.last().day}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Colour performance: sold stacked on still-on-hand.
 *
 * The garment's colour is the swatch beside the label; the bar carries sold-versus-on-hand. Two
 * series, so a legend.
 */
@Composable
fun ColourPerformanceChart(
    buckets: List<ColourBucket>,
    modifier: Modifier = Modifier,
) {
    val colours = chartColours()
    val visible = buckets.filter { it.total > 0 }
    if (visible.isEmpty()) {
        EmptyPanel("No stock to compare yet", modifier)
        return
    }

    val ceiling = niceCeiling(visible.maxOf { it.total }.toLong())

    Column(modifier = modifier) {
        Legend(soldColour = colours.sold, onHandColour = colours.onHand)

        Row(
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            visible.forEach { bucket ->
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .width(MAX_COLUMN_DP.dp)
                        .height(160.dp),
                ) {
                    val columnWidth = minOf(size.width, MAX_COLUMN_DP * density)
                    val left = (size.width - columnWidth) / 2
                    val gap = STACK_GAP_DP * density

                    val soldHeight = size.height * (bucket.sold.toFloat() / ceiling)
                    val onHandHeight = size.height * (bucket.onHand.toFloat() / ceiling)

                    // On hand at the baseline, square; sold above it, rounded at the data end.
                    if (onHandHeight > 0) {
                        drawRect(
                            color = colours.onHand,
                            topLeft = Offset(left, size.height - onHandHeight),
                            size = Size(columnWidth, onHandHeight),
                        )
                    }
                    if (soldHeight > 0) {
                        val top = size.height - onHandHeight - gap - soldHeight
                        drawRoundRect(
                            color = colours.sold,
                            topLeft = Offset(left, top),
                            size = Size(columnWidth, soldHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visible.forEach { bucket ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Swatch(bucket.hex)
                    Text(
                        bucket.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** When the shop is busy. A sequential ramp, because the value has an order and a zero. */
@Composable
fun BusyHoursHeatmap(hours: BusyHours, modifier: Modifier = Modifier) {
    val colours = chartColours()
    if (hours.buckets.isEmpty()) {
        EmptyPanel("No trading hours to show yet", modifier)
        return
    }

    val busiest = hours.busiest.coerceAtLeast(1)
    // Sunday first, matching SQLite's %w and the Egyptian working week.
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val openHours = OPENING_HOUR..CLOSING_HOUR

    Column(modifier = modifier) {
        days.forEachIndexed { dayIndex, label ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                )
                openHours.forEach { hour ->
                    val bucket = hours.at(dayIndex, hour)
                    val intensity = (bucket?.transactions ?: 0).toFloat() / busiest
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(lerpColour(colours.heatFrom, colours.heatTo, intensity)),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 2.dp)) {
            Text(
                "${OPENING_HOUR}:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${CLOSING_HOUR}:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A horizontal bar against the season target. Two marks, so the target line is labelled. */
@Composable
fun SellThroughBar(
    basisPoints: Int,
    targetBasisPoints: Int,
    modifier: Modifier = Modifier,
) {
    val colours = chartColours()
    Canvas(modifier = modifier.fillMaxWidth().height(14.dp)) {
        drawRoundRect(
            color = colours.grid,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        )
        val fraction = (basisPoints.toFloat() / 10_000f).coerceIn(0f, 1f)
        if (fraction > 0) {
            drawRoundRect(
                color = if (basisPoints >= targetBasisPoints) colours.good else colours.warning,
                size = Size(size.width * fraction, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            )
        }
        // The target, as a 1px solid hairline. Never dashed.
        val targetX = size.width * (targetBasisPoints / 10_000f)
        drawLine(
            color = colours.axisText,
            start = Offset(targetX, 0f),
            end = Offset(targetX, size.height),
            strokeWidth = 1f,
        )
    }
}

@Composable
private fun AxisLabels(ceiling: Long) {
    Column(
        modifier = Modifier.width(40.dp).height(160.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Round numbers only, so two panels can be compared without arithmetic.
        listOf(ceiling, ceiling / 2, 0L).forEach { value ->
            Text(
                abbreviate(value / 100),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Legend(soldColour: Color, onHandColour: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendEntry(soldColour, "Sold")
        LegendEntry(onHandColour, "On hand")
    }
}

@Composable
private fun LegendEntry(colour: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colour))
        // Ink, never the series colour: coloured text fails contrast and reads as a state.
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The garment's own colour, beside the label — never as a bar's fill. */
@Composable
private fun Swatch(hex: String) {
    val colour = runCatching {
        Color(hex.removePrefix("#").toLong(16) or 0xFF000000)
    }.getOrElse { Color.Gray }

    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colour))
}

@Composable
private fun EmptyPanel(message: String, modifier: Modifier) {
    // A stated empty state, never a broken axis or a NaN. Every new install sees this first —
    // which is also why it no longer reserves the chart's full height: a sentence floating in the
    // middle of 120dp of nothing made a shop's first morning look like a page that had failed to
    // load. It takes the room it needs and the panel closes up around it.
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(colour: Color, size: Size) {
    // Four solid hairlines. A dashed grid competes with the data for attention.
    repeat(4) { index ->
        val y = size.height * index / 3f
        drawLine(colour, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

private fun lerpColour(from: Color, to: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f,
    )
}

private const val OPENING_HOUR = 9
private const val CLOSING_HOUR = 23
