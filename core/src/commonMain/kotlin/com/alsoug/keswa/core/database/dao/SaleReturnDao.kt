package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alsoug.keswa.core.database.entities.SaleReturnEntity
import com.alsoug.keswa.core.database.entities.SaleReturnLineEntity
import com.alsoug.keswa.core.domain.model.TenderMethod
import kotlinx.coroutines.flow.Flow

/**
 * Goods coming back.
 *
 * Append-only on the same terms as `sale`: no delete, and the only `UPDATE`s are the void and the
 * exchange link. A return made in error is voided, never edited — `ReturnsAreAppendOnlyTest`
 * asserts the absence by reading this file.
 */
@Dao
interface SaleReturnDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(saleReturn: SaleReturnEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<SaleReturnLineEntity>)

    /** The next number in this device's block, for the same reason receipts have one (9i). */
    @Query(
        "SELECT COALESCE(MAX(returnNumber), :blockStart) + 1 FROM sale_return " +
            "WHERE returnNumber BETWEEN :blockStart AND :blockEnd",
    )
    suspend fun nextReturnNumber(blockStart: Long = 0, blockEnd: Long = Long.MAX_VALUE): Long

    @Query("SELECT * FROM sale_return WHERE id = :id")
    suspend fun getById(id: String): SaleReturnEntity?

    @Query("SELECT * FROM sale_return WHERE returnNumber = :returnNumber")
    suspend fun getByNumber(returnNumber: Long): SaleReturnEntity?

    @Query("SELECT * FROM sale_return_line WHERE returnId = :returnId ORDER BY lineNumber")
    suspend fun getLines(returnId: String): List<SaleReturnLineEntity>

    @Query("SELECT * FROM sale_return WHERE originalSaleId = :saleId AND status = 'COMPLETED'")
    suspend fun forSale(saleId: String): List<SaleReturnEntity>

    /**
     * How many of a sale line have already come back.
     *
     * The guard against a customer returning the same jacket three times against one receipt —
     * summed across every completed return, because they may well come back on separate days.
     */
    @Query(
        """
        SELECT COALESCE(SUM(l.quantity), 0) FROM sale_return_line l
        JOIN sale_return r ON r.id = l.returnId
        WHERE l.saleLineId = :saleLineId AND r.status = 'COMPLETED'
        """,
    )
    suspend fun alreadyReturned(saleLineId: String): Int

    /**
     * The lowest price this variant has ever actually sold for.
     *
     * What a no-receipt return refunds at. Buying at a markdown and returning at full price is the
     * most common retail refund fraud there is, and it costs exactly the markdown every time.
     */
    @Query(
        """
        SELECT MIN(lineTotalPiastres / quantity) FROM sale_line
        WHERE variantId = :variantId AND quantity > 0
        """,
    )
    suspend fun lowestSoldPrice(variantId: String): Long?

    @Query("SELECT * FROM sale_return ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SaleReturnEntity>>

    @Query(
        """
        UPDATE sale_return SET status = 'VOIDED', voidedAt = :atMillis,
                               voidedByUserId = :userId, voidReason = :reason
        WHERE id = :returnId AND status = 'COMPLETED'
        """,
    )
    suspend fun markVoided(returnId: String, atMillis: Long, userId: String, reason: String): Int

    /** Written once, when the replacement sale is rung up in the same breath as the return. */
    @Query("UPDATE sale_return SET exchangeSaleId = :saleId WHERE id = :returnId")
    suspend fun linkExchange(returnId: String, saleId: String)

    @Query("SELECT COUNT(*) FROM sale_return WHERE shiftId = :shiftId AND status = 'COMPLETED'")
    suspend fun countInShift(shiftId: String): Int

    @Query(
        """
        SELECT COALESCE(SUM(refundAmountPiastres), 0) FROM sale_return
        WHERE shiftId = :shiftId AND status = 'COMPLETED' AND refundMethod = :method
        """,
    )
    suspend fun sumRefundedInShift(shiftId: String, method: TenderMethod): Long
}
