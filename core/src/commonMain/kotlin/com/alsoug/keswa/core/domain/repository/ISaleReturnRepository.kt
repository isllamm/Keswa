package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.ReturnableLine
import com.alsoug.keswa.core.domain.model.SaleReturn
import com.alsoug.keswa.core.domain.model.SaleReturnLine
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import kotlinx.coroutines.flow.Flow

/** A return as the till has assembled it, before it has a number. */
data class ReturnDraft(
    val id: String,
    val originalSaleId: String?,
    val locationId: String,
    val userId: String,
    val shiftId: String?,
    val reason: String,
    val refundMethod: TenderMethod,
    val refundAmount: Money,
    val subtotal: Money,
    val tax: Money,
    val occurredAt: Long,
    val authorisedByUserId: String?,
    val lines: List<SaleReturnLine>,
)

interface ISaleReturnRepository {

    /**
     * Commits the return, its lines and its stock movements in one transaction, and returns it
     * with the number it was allocated.
     *
     * A sellable line writes one `RETURN` movement. A damaged one writes **two** — a `RETURN`
     * bringing it in and a `DAMAGE` taking it out — because the shop did take possession of the
     * garment and then lose it, and a ledger that records only half of that cannot explain where
     * the refund went.
     */
    suspend fun record(draft: ReturnDraft): Result<SaleReturn>

    /** Reverses a return: compensating movements, and the row marked rather than removed. */
    suspend fun void(
        returnId: String,
        byUserId: String,
        reason: String,
        atMillis: Long,
    ): Result<SaleReturn>

    /** Ties a return to the replacement purchase, making the pair an exchange. */
    suspend fun linkExchange(returnId: String, saleId: String): Result<Unit>

    suspend fun getById(id: String): Result<SaleReturn?>

    suspend fun getByNumber(returnNumber: Long): Result<SaleReturn?>

    /** What is still returnable against a sale — sold minus already returned, per line. */
    suspend fun returnableLines(saleId: String): Result<List<ReturnableLine>>

    /**
     * The lowest price this variant has ever actually sold for, for a no-receipt return.
     *
     * Null when it has never sold, which is a return that needs a manual amount and an admin.
     */
    suspend fun lowestSoldPrice(variantId: String): Result<Money?>

    fun observeRecent(limit: Int = 25): Flow<List<SaleReturn>>
}
