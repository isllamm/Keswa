package com.alsoug.keswa.features.analytics.domain.usecase

import com.alsoug.keswa.core.domain.model.AnalyticsPeriod
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import com.alsoug.keswa.core.domain.model.HeadlineKpis
import com.alsoug.keswa.core.domain.model.Mover
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SellThroughRowModel
import com.alsoug.keswa.core.domain.model.can
import com.alsoug.keswa.core.domain.repository.IAnalyticsRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * The window a panel is looking at, resolved once for the whole page.
 *
 * One filter row scoping everything, never per-chart filters — two panels that silently disagree
 * destroy trust in the numbers faster than any bug.
 */
data class AnalyticsWindow(val from: Long, val to: Long)

fun AnalyticsPeriod.windowEndingAt(nowMillis: Long): AnalyticsWindow =
    AnalyticsWindow(from = nowMillis - days * MILLIS_PER_DAY, to = nowMillis)

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * Gates every read on `VIEW_SHOP_ANALYTICS`, and cost separately on `VIEW_COST_AND_MARGIN`.
 *
 * The second is the one that matters commercially: a seller may see units and revenue without
 * learning what the shop paid. Where the permission is absent the cost is **not fetched**, rather
 * than fetched and hidden — hiding a number in the UI is not a control on an offline desktop app
 * whose user owns the machine.
 */
private fun ISessionManager.requireAnalytics(): Boolean {
    require(Permission.VIEW_SHOP_ANALYTICS)
    return current.value.can(Permission.VIEW_COST_AND_MARGIN)
}

class GetHeadlineKpisUseCase(
    private val analytics: IAnalyticsRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(period: AnalyticsPeriod): Result<HeadlineKpis> = runCatching {
        val withCost = sessions.requireAnalytics()
        val window = period.windowEndingAt(now())
        analytics.headline(window.from, window.to, withCost).getOrThrow()
    }
}

class GetRevenueTrendUseCase(
    private val analytics: IAnalyticsRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(period: AnalyticsPeriod): Result<List<DailyPoint>> = runCatching {
        sessions.requireAnalytics()
        val window = period.windowEndingAt(now())
        analytics.revenueByDay(window.from, window.to).getOrThrow()
    }
}

/**
 * Sold against still-on-hand, per colour.
 *
 * The most valuable panel in the product: beige and green over-bought while navy sold out is cash
 * tied up in the wrong colours, and that is next season's buying decision.
 */
class GetColourPerformanceUseCase(
    private val analytics: IAnalyticsRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(period: AnalyticsPeriod): Result<List<ColourBucket>> = runCatching {
        sessions.requireAnalytics()
        val window = period.windowEndingAt(now())
        analytics.colourPerformance(window.from, window.to).getOrThrow()
    }
}

/** The markdown trigger: below target late in a season means discount now, not in January. */
class GetSellThroughUseCase(
    private val analytics: IAnalyticsRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(period: AnalyticsPeriod): Result<List<SellThroughRowModel>> =
        runCatching {
            sessions.requireAnalytics()
            val window = period.windowEndingAt(now())
            analytics.sellThrough(window.from, window.to).getOrThrow()
        }
}

class GetBusyHoursUseCase(
    private val analytics: IAnalyticsRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(period: AnalyticsPeriod): Result<BusyHours> = runCatching {
        sessions.requireAnalytics()
        val window = period.windowEndingAt(now())
        analytics.busyHours(window.from, window.to).getOrThrow()
    }
}

class GetTopMoversUseCase(
    private val analytics: IAnalyticsRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        period: AnalyticsPeriod,
        limit: Int = DEFAULT_LIMIT,
    ): Result<List<Mover>> = runCatching {
        val withCost = sessions.requireAnalytics()
        val window = period.windowEndingAt(now())
        analytics.topMovers(window.from, window.to, limit, withCost).getOrThrow()
    }

    private companion object {
        /** Past about seven rows a table stops being scannable; ten is the practical ceiling. */
        const val DEFAULT_LIMIT = 10
    }
}
