package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.alsoug.keswa.core.database.entities.StockCountEntity
import com.alsoug.keswa.core.database.entities.StockCountLineEntity
import com.alsoug.keswa.core.database.entities.StockReceiptEntity
import com.alsoug.keswa.core.database.entities.StockReceiptLineEntity
import kotlinx.coroutines.flow.Flow

/**
 * Deliveries, as drafts and then as history.
 *
 * Drafts are freely editable and deletable — nothing has happened yet. Once posted, the document is
 * referenced by stock movements and becomes as immutable as they are: the only `UPDATE` here is the
 * post itself, guarded on `DRAFT` so a double click cannot receive the same carton twice.
 */
@Dao
interface StockReceiptDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(receipt: StockReceiptEntity)

    @Upsert
    suspend fun upsertLine(line: StockReceiptLineEntity)

    @Query("SELECT * FROM stock_receipt WHERE id = :id")
    suspend fun getById(id: String): StockReceiptEntity?

    @Query("SELECT * FROM stock_receipt_line WHERE receiptId = :receiptId ORDER BY lineNumber")
    suspend fun getLines(receiptId: String): List<StockReceiptLineEntity>

    @Query("SELECT COALESCE(MAX(lineNumber), 0) + 1 FROM stock_receipt_line WHERE receiptId = :receiptId")
    suspend fun nextLineNumber(receiptId: String): Int

    @Query("SELECT * FROM stock_receipt WHERE locationId = :locationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(locationId: String, limit: Int): List<StockReceiptEntity>

    @Query("SELECT * FROM stock_receipt WHERE locationId = :locationId AND status = 'DRAFT' ORDER BY createdAt")
    fun observeDrafts(locationId: String): Flow<List<StockReceiptEntity>>

    /** Only ever a draft: a posted receipt is referenced by movements and stays for good. */
    @Query("DELETE FROM stock_receipt WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraft(id: String): Int

    @Query("DELETE FROM stock_receipt_line WHERE id = :id")
    suspend fun deleteLine(id: String)

    @Query(
        """
        UPDATE stock_receipt
        SET status = 'POSTED', postedAt = :atMillis, postedByUserId = :userId,
            totalCostPiastres = :totalCost
        WHERE id = :receiptId AND status = 'DRAFT'
        """,
    )
    suspend fun markPosted(
        receiptId: String,
        atMillis: Long,
        userId: String,
        totalCost: Long,
    ): Int
}

/**
 * Counts, and the reason the schema is shaped the way it is.
 *
 * There is deliberately **no query that returns an expected quantity for an open count**. The
 * column is null until posting fills it in, so a blind count cannot stop being blind by someone
 * adding a convenient getter.
 */
@Dao
interface StockCountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(count: StockCountEntity)

    @Upsert
    suspend fun upsertLine(line: StockCountLineEntity)

    @Query("SELECT * FROM stock_count WHERE id = :id")
    suspend fun getById(id: String): StockCountEntity?

    @Query("SELECT * FROM stock_count_line WHERE countId = :countId ORDER BY lineNumber")
    suspend fun getLines(countId: String): List<StockCountLineEntity>

    @Query("SELECT * FROM stock_count_line WHERE countId = :countId AND variantId = :variantId")
    suspend fun getLine(countId: String, variantId: String): StockCountLineEntity?

    @Query("SELECT COALESCE(MAX(lineNumber), 0) + 1 FROM stock_count_line WHERE countId = :countId")
    suspend fun nextLineNumber(countId: String): Int

    @Query("SELECT * FROM stock_count WHERE locationId = :locationId AND status = 'DRAFT' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getOpen(locationId: String): StockCountEntity?

    @Query("SELECT * FROM stock_count WHERE locationId = :locationId ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(locationId: String, limit: Int): List<StockCountEntity>

    @Query("DELETE FROM stock_count WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraft(id: String): Int

    /** Written at post, which is the first moment the expected figure may exist. */
    @Query(
        """
        UPDATE stock_count_line
        SET expectedQuantity = :expected, varianceQuantity = :variance
        WHERE id = :lineId
        """,
    )
    suspend fun settleLine(lineId: String, expected: Int, variance: Int)

    @Query(
        """
        UPDATE stock_count SET status = 'POSTED', postedAt = :atMillis, postedByUserId = :userId
        WHERE id = :countId AND status = 'DRAFT'
        """,
    )
    suspend fun markPosted(countId: String, atMillis: Long, userId: String): Int
}
