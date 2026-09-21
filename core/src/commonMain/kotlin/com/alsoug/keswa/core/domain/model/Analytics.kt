package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * The numbers on the dashboard.
 *
 * [cogs] and [margin] are nullable, and separately so: `VIEW_COST_AND_MARGIN` is its own
 * permission. A seller may be shown units and revenue without learning what the shop paid — in a
 * trade where staff move between shops on the same street, that is the leak an owner cares about.
 */
data class HeadlineKpis(
    val revenue: Money,
    val refunded: Money,
    val transactions: Int,
    val units: Int,
    val returnedUnits: Int,
    val cogs: Money? = null,
) {
    val netRevenue: Money get() = revenue - refunded

    /** Null when the basket would be a division by zero — a shop on day one, which is common. */
    val averageBasket: Money?
        get() = if (transactions > 0) Money.ofPiastres(revenue.piastres / transactions) else null

    /**
     * Returned units over units sold, in basis points.
     *
     * A first-class KPI in clothing rather than a footnote: fit is guesswork, so a shop with no
     * returns is a shop nobody is trying things on in.
     */
    val returnRateBasisPoints: Int?
        get() = if (units > 0) ((returnedUnits.toLong() * 10_000) / units).toInt() else null

    val margin: Money? get() = cogs?.let { netRevenue - it }

    val marginBasisPoints: Int?
        get() {
            val value = margin ?: return null
            if (netRevenue.isZero) return null
            return ((value.piastres * 10_000) / netRevenue.piastres).toInt()
        }
}

/** One day on the trend line. [day] is a local `yyyy-MM-dd`, so it reads as the shop's own day. */
data class DailyPoint(val day: String, val amount: Money, val count: Int)

/**
 * A colour, and what happened to it.
 *
 * The garment's own colour is a swatch beside the label, never the bar's fill — a bar coloured
 * beige on a beige-to-navy chart is unreadable, and the series colour has to mean *sold* or
 * *on hand* consistently across every panel.
 */
data class ColourBucket(
    val colourId: String,
    val name: String,
    val nameAr: String,
    val hex: String,
    val sold: Int,
    val onHand: Int,
) {
    val total: Int get() = sold + onHand

    /** Basis points, so no floating point reaches a figure anybody makes a decision on. */
    val sellThroughBasisPoints: Int?
        get() = if (total > 0) ((sold.toLong() * 10_000) / total).toInt() else null
}

/**
 * Sell-through for one branch of the category tree.
 *
 * `sold / (received)` across the period. Below target late in a season means discount now, not in
 * January — which is the decision this panel exists to trigger.
 */
data class SellThroughRowModel(
    val categoryId: String,
    val name: String,
    val nameAr: String,
    val sold: Int,
    val received: Int,
) {
    val basisPoints: Int?
        get() = if (received > 0) ((sold.toLong() * 10_000) / received).toInt() else null

    val isBelowTarget: Boolean get() = (basisPoints ?: 0) < TARGET_BASIS_POINTS

    companion object {
        /** 70%, the conventional season target this panel is read against. */
        const val TARGET_BASIS_POINTS = 7_000
    }
}

/** Transactions by weekday and hour. [dayOfWeek] is 0 = Sunday, matching SQLite's `%w`. */
data class HourBucket(val dayOfWeek: Int, val hour: Int, val transactions: Int, val amount: Money)

data class BusyHours(val buckets: List<HourBucket>) {
    val busiest: Int get() = buckets.maxOfOrNull { it.transactions } ?: 0

    fun at(dayOfWeek: Int, hour: Int): HourBucket? =
        buckets.firstOrNull { it.dayOfWeek == dayOfWeek && it.hour == hour }
}

/** One SKU's performance. Past about seven items a table beats a chart, so this is a table. */
data class Mover(
    val variantId: String,
    val sku: String,
    val productName: String,
    val colourName: String,
    val sold: Int,
    val revenue: Money,
    val onHand: Int,
    val received: Int,
    val cogs: Money? = null,
) {
    val sellThroughBasisPoints: Int?
        get() = if (received > 0) ((sold.toLong() * 10_000) / received).toInt() else null

    val margin: Money? get() = cogs?.let { revenue - it }
}

/** How far back the dashboard is looking. One filter row scopes the whole page. */
enum class AnalyticsPeriod(val days: Int) {
    TODAY(1),
    WEEK(7),
    MONTH(30),
    QUARTER(90),
}
