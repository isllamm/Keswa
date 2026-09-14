package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * A cached `SUM(quantity)` per variant and location.
 *
 * This is a projection, never a source of truth: it is written in the same transaction as the
 * movement that changes it, and can be rebuilt from `stock_movement` at any time. If it and the
 * ledger ever disagree, the ledger wins and this is regenerated — which is what makes a drift
 * recoverable rather than corrupt.
 */
@Entity(
    tableName = "stock_on_hand",
    primaryKeys = ["variantId", "locationId"],
    indices = [Index("locationId")],
)
data class StockOnHandEntity(
    val variantId: String,
    val locationId: String,
    val quantity: Int,
    val lastMovementAt: Long,
)
