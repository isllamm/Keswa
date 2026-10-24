package com.alsoug.keswa.server

import com.alsoug.keswa.core.database.KeswaDatabase

class BackOfficeReportService(
    private val database: KeswaDatabase,
    private val log: LogStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun getSummary(days: Int): DashboardSummaryDto {
        val safeDays = days.coerceIn(1, 365)
        val currentTime = now()
        val from = currentTime - (safeDays.toLong() * MILLIS_PER_DAY)
        val to = currentTime + 1000L

        val dao = database.analyticsDao()
        val headline = dao.headline(from, to)
        val refundedPiastres = dao.refunded(from, to)
        val returnedUnits = dao.returnedUnits(from, to)

        val revenuePiastres = headline?.revenuePiastres ?: 0L
        val cogsPiastres = headline?.cogsPiastres ?: 0L
        val transactions = headline?.transactions ?: 0
        val units = headline?.units ?: 0

        val netRevenuePiastres = revenuePiastres - refundedPiastres
        val grossMarginPercent = if (revenuePiastres > 0) {
            ((revenuePiastres - cogsPiastres).toDouble() / revenuePiastres) * 100.0
        } else null

        val averageBasketPounds = if (transactions > 0) {
            (revenuePiastres / 100.0) / transactions
        } else 0.0

        val headlineDto = HeadlineKpisDto(
            revenuePounds = revenuePiastres / 100.0,
            refundedPounds = refundedPiastres / 100.0,
            netRevenuePounds = netRevenuePiastres / 100.0,
            transactions = transactions,
            unitsSold = units,
            unitsReturned = returnedUnits,
            cogsPounds = cogsPiastres / 100.0,
            grossMarginPercent = grossMarginPercent,
            averageBasketPounds = averageBasketPounds,
        )

        val dailyRevenue = dao.revenueByDay(from, to).map {
            DailyPointDto(it.day, it.amountPiastres / 100.0, it.count)
        }

        val colourPerformance = dao.colourPerformance(from, to).map {
            ColourBucketDto(
                colourId = it.colourId,
                name = it.colourName,
                nameAr = it.colourNameAr,
                hex = it.hex,
                sold = it.soldQuantity,
                onHand = it.onHandQuantity,
            )
        }

        val topMovers = dao.topMovers(from, to, 10).map {
            val margin = if (it.revenuePiastres > 0) {
                ((it.revenuePiastres - it.cogsPiastres).toDouble() / it.revenuePiastres) * 100.0
            } else null
            MoverDto(
                variantId = it.variantId,
                sku = it.sku,
                productName = it.productName,
                colourName = it.colourName,
                sold = it.soldQuantity,
                revenuePounds = it.revenuePiastres / 100.0,
                onHand = it.onHandQuantity,
                received = it.receivedQuantity,
                cogsPounds = it.cogsPiastres / 100.0,
                marginPercent = margin,
                productNameAr = it.productNameAr,
                colourNameAr = it.colourNameAr,
            )
        }

        return DashboardSummaryDto(
            periodDays = safeDays,
            headline = headlineDto,
            dailyRevenue = dailyRevenue,
            colourPerformance = colourPerformance,
            topMovers = topMovers,
        )
    }

    suspend fun getValuation(): InventoryValuationDto {
        database.stockLedgerDao().rebuildProjection()
        val onHands = database.stockLedgerDao().getAllOnHand()
        var totalPieces = 0
        var totalCostPiastres = 0L
        val positiveVariantIds = mutableSetOf<String>()

        for (onHand in onHands) {
            if (onHand.quantity > 0) {
                totalPieces += onHand.quantity
                positiveVariantIds += onHand.variantId
                val variant = database.variantDao().getById(onHand.variantId)
                if (variant != null) {
                    totalCostPiastres += variant.costPiastres * onHand.quantity
                }
            }
        }

        return InventoryValuationDto(
            totalPiecesOnHand = totalPieces,
            totalCostValuePounds = totalCostPiastres / 100.0,
            trackedSkusCount = positiveVariantIds.size,
        )
    }

    suspend fun getDevices(): List<DeviceSummaryDto> {
        return log.listDevices().map { device ->
            val rangeStart = device.ordinal.toLong() * 1_000_000L
            val rangeEnd = rangeStart + 999_999L
            DeviceSummaryDto(
                id = device.id,
                name = device.name,
                ordinal = device.ordinal,
                receiptRange = "#$rangeStart – #$rangeEnd",
                enrolledAt = device.enrolledAt,
                revokedAt = device.revokedAt,
                isRevoked = device.isRevoked,
            )
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
