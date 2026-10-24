package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import com.alsoug.keswa.core.domain.model.HeadlineKpis
import com.alsoug.keswa.core.domain.model.Mover
import com.alsoug.keswa.core.domain.model.SellThroughRowModel

/**
 * Every figure the dashboard shows, read from the ledger.
 *
 * [withCost] on the reads that can carry cost: `VIEW_COST_AND_MARGIN` is checked in the use case,
 * and when it is absent the cost is **never fetched**, not fetched and hidden. A number that
 * reaches a ViewModel is a number that can reach a screen.
 */
interface IAnalyticsRepository {

    suspend fun headline(from: Long, to: Long, withCost: Boolean): Result<HeadlineKpis>

    suspend fun revenueByDay(from: Long, to: Long): Result<List<DailyPoint>>

    suspend fun colourPerformance(from: Long, to: Long): Result<List<ColourBucket>>

    suspend fun sellThrough(from: Long, to: Long): Result<List<SellThroughRowModel>>

    suspend fun busyHours(from: Long, to: Long): Result<BusyHours>

    suspend fun topMovers(from: Long, to: Long, limit: Int, withCost: Boolean): Result<List<Mover>>
}
