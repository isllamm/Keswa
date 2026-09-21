package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.StockCount
import com.alsoug.keswa.core.domain.model.StockMovement
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import kotlinx.coroutines.flow.Flow

/** What a post did, so the screen can say something more useful than "done". */
data class PostedReceipt(
    val receipt: StockReceipt,
    val costChanges: List<CostChange>,
)

/**
 * A variant's cost before and after a receipt (KD-008).
 *
 * Surfaced rather than hidden because a supplier quietly raising a price is exactly what an owner
 * wants to be told, and the moment it becomes visible is the moment stock is received.
 */
data class CostChange(
    val variantId: String,
    val before: Money,
    val after: Money,
) {
    val hasChanged: Boolean get() = before != after
}

interface IStockReceiptRepository {

    suspend fun createDraft(
        id: String,
        reference: String,
        supplierName: String,
        locationId: String,
        userId: String,
        atMillis: Long,
    ): Result<StockReceipt>

    /** Adds a line, or replaces the quantity and cost on the one already there for that variant. */
    suspend fun putLine(
        id: String,
        receiptId: String,
        variantId: String,
        quantity: Int,
        unitCost: Money,
    ): Result<StockReceipt>

    suspend fun removeLine(receiptId: String, lineId: String): Result<StockReceipt>

    /**
     * Writes every movement and every cost change in one transaction, and closes the receipt.
     *
     * Guarded on `DRAFT` inside the statement, so a double submit receives the carton once.
     */
    suspend fun post(receiptId: String, userId: String, atMillis: Long): Result<PostedReceipt>

    suspend fun getById(id: String): Result<StockReceipt?>

    suspend fun discardDraft(id: String): Result<Unit>

    suspend fun recent(locationId: String, limit: Int = 25): Result<List<StockReceipt>>

    fun observeDrafts(locationId: String): Flow<List<StockReceipt>>
}

interface IStockCountRepository {

    suspend fun start(
        id: String,
        locationId: String,
        userId: String,
        atMillis: Long,
    ): Result<StockCount>

    /** Records what was counted. Never returns what was expected — the count stays blind. */
    suspend fun putLine(
        id: String,
        countId: String,
        variantId: String,
        counted: Int,
    ): Result<StockCount>

    /**
     * Settles the count against the ledger and writes one `COUNT` movement per line that differs.
     *
     * A line that agrees writes nothing: a ledger entry saying "nothing changed" is noise that
     * makes the real ones harder to find.
     */
    suspend fun post(
        countId: String,
        userId: String,
        note: String?,
        atMillis: Long,
    ): Result<StockCount>

    suspend fun getById(id: String): Result<StockCount?>

    suspend fun current(locationId: String): Result<StockCount?>

    suspend fun discard(id: String): Result<Unit>

    suspend fun recent(locationId: String, limit: Int = 25): Result<List<StockCount>>
}

/** One-off corrections that do not belong to a document. */
interface IStockAdjustmentRepository {

    suspend fun adjust(
        id: String,
        variantId: String,
        locationId: String,
        quantity: Int,
        reason: MovementReason,
        note: String,
        userId: String,
        atMillis: Long,
    ): Result<StockMovement>

    suspend fun historyFor(variantId: String, locationId: String): Result<List<StockMovement>>
}
