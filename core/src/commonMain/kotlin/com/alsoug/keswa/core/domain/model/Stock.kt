package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * One immutable change in stock.
 *
 * [quantity] is signed — negative for a sale, positive for a receipt — so on-hand is a plain sum
 * and two tills that sold offline merge by appending, with no conflict resolution.
 */
data class StockMovement(
    val id: String,
    val variantId: String,
    val locationId: String,
    val quantity: Int,
    val reason: MovementReason,
    val refType: String?,
    val refId: String?,
    val occurredAt: Long,
    val userId: String,
    /** The cost basis when this happened (KD-008). Null for movements written before Phase 6. */
    val unitCost: Money? = null,
    /** The reason in words, where the enum only gives the category. */
    val note: String? = null,
)

/** The cached sum for one variant at one location. Always rebuildable from the ledger. */
data class StockOnHand(
    val variantId: String,
    val locationId: String,
    val quantity: Int,
    val lastMovementAt: Long,
)
