package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.database.dao.AnalyticsDao
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import com.alsoug.keswa.core.domain.model.HeadlineKpis
import com.alsoug.keswa.core.domain.model.HourBucket
import com.alsoug.keswa.core.domain.model.Mover
import com.alsoug.keswa.core.domain.model.SellThroughRowModel
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IAnalyticsRepository

/**
 * The dashboard, straight off the ledger.
 *
 * No rollup tables: see the measurement in `AnalyticsPerformanceTest`, and the deviation note in
 * `phase-8-analytics-plan.md`. Adding a projection that has to be kept in step with three
 * repositories, plus a rebuild path, is a real cost — and the plan's own check 4 was always the
 * thing that decided whether it was worth paying.
 */
class AnalyticsRepositoryImpl(
    private val dao: AnalyticsDao,
) : IAnalyticsRepository {

    override suspend fun headline(
        from: Long,
        to: Long,
        withCost: Boolean,
    ): Result<HeadlineKpis> = runCatchingCancellable {
        val row = dao.headline(from, to)
        HeadlineKpis(
            revenue = Money.ofPiastres(row?.revenuePiastres ?: 0),
            refunded = Money.ofPiastres(dao.refunded(from, to)),
            transactions = row?.transactions ?: 0,
            units = row?.units ?: 0,
            returnedUnits = dao.returnedUnits(from, to),
            // Not fetched at all without the permission. A figure that reaches a ViewModel is a
            // figure that can reach a screen.
            cogs = if (withCost) Money.ofPiastres(row?.cogsPiastres ?: 0) else null,
        )
    }

    override suspend fun revenueByDay(from: Long, to: Long): Result<List<DailyPoint>> =
        runCatchingCancellable {
            dao.revenueByDay(from, to).map {
                DailyPoint(it.day, Money.ofPiastres(it.amountPiastres), it.count)
            }
        }

    override suspend fun colourPerformance(from: Long, to: Long): Result<List<ColourBucket>> =
        runCatchingCancellable {
            dao.colourPerformance(from, to).map {
                ColourBucket(
                    colourId = it.colourId,
                    name = it.colourName,
                    nameAr = it.colourNameAr,
                    hex = it.hex,
                    sold = it.soldQuantity,
                    onHand = it.onHandQuantity,
                )
            }
        }

    override suspend fun sellThrough(from: Long, to: Long): Result<List<SellThroughRowModel>> =
        runCatchingCancellable {
            dao.sellThroughByCategory(from, to).map {
                SellThroughRowModel(
                    categoryId = it.categoryId,
                    name = it.categoryName,
                    nameAr = it.categoryNameAr,
                    sold = it.soldQuantity,
                    received = it.receivedQuantity,
                )
            }
        }

    override suspend fun busyHours(from: Long, to: Long): Result<BusyHours> =
        runCatchingCancellable {
            BusyHours(
                dao.busyHours(from, to).map {
                    HourBucket(it.dayOfWeek, it.hour, it.transactions, Money.ofPiastres(it.amountPiastres))
                },
            )
        }

    override suspend fun topMovers(
        from: Long,
        to: Long,
        limit: Int,
        withCost: Boolean,
    ): Result<List<Mover>> = runCatchingCancellable {
        dao.topMovers(from, to, limit).map {
            Mover(
                variantId = it.variantId,
                sku = it.sku,
                productName = it.productName,
                colourName = it.colourName,
                sold = it.soldQuantity,
                revenue = Money.ofPiastres(it.revenuePiastres),
                onHand = it.onHandQuantity,
                received = it.receivedQuantity,
                cogs = if (withCost) Money.ofPiastres(it.cogsPiastres) else null,
                productNameAr = it.productNameAr,
                colourNameAr = it.colourNameAr,
            )
        }
    }
}
