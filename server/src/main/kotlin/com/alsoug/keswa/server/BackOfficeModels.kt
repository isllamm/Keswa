package com.alsoug.keswa.server

import kotlinx.serialization.Serializable

@Serializable
data class DashboardSummaryDto(
    val periodDays: Int,
    val headline: HeadlineKpisDto,
    val dailyRevenue: List<DailyPointDto>,
    val colourPerformance: List<ColourBucketDto>,
    val topMovers: List<MoverDto>,
)

@Serializable
data class HeadlineKpisDto(
    val revenuePounds: Double,
    val refundedPounds: Double,
    val netRevenuePounds: Double,
    val transactions: Int,
    val unitsSold: Int,
    val unitsReturned: Int,
    val cogsPounds: Double?,
    val grossMarginPercent: Double?,
    val averageBasketPounds: Double,
)

@Serializable
data class DailyPointDto(
    val day: String,
    val amountPounds: Double,
    val count: Int,
)

@Serializable
data class ColourBucketDto(
    val colourId: String,
    val name: String,
    val nameAr: String,
    val hex: String,
    val sold: Int,
    val onHand: Int,
)

@Serializable
data class MoverDto(
    val variantId: String,
    val sku: String,
    val productName: String,
    val colourName: String,
    val sold: Int,
    val revenuePounds: Double,
    val onHand: Int,
    val received: Int,
    val cogsPounds: Double?,
    val marginPercent: Double?,
    val productNameAr: String? = null,
    val colourNameAr: String? = null,
)

@Serializable
data class InventoryValuationDto(
    val totalPiecesOnHand: Int,
    val totalCostValuePounds: Double,
    val trackedSkusCount: Int,
)

@Serializable
data class DeviceSummaryDto(
    val id: String,
    val name: String,
    val ordinal: Int,
    val receiptRange: String,
    val enrolledAt: Long,
    val revokedAt: Long?,
    val isRevoked: Boolean,
)
