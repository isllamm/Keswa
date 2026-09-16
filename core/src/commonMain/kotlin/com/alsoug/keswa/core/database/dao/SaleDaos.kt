package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alsoug.keswa.core.database.entities.HeldSaleEntity
import com.alsoug.keswa.core.database.entities.HeldSaleLineEntity
import com.alsoug.keswa.core.database.entities.PaymentEntity
import com.alsoug.keswa.core.database.entities.SaleEntity
import com.alsoug.keswa.core.database.entities.SaleLineEntity
import com.alsoug.keswa.core.database.entities.ShiftEntity
import com.alsoug.keswa.core.domain.model.TenderMethod
import kotlinx.coroutines.flow.Flow

/**
 * Sales, their lines and their tenders.
 *
 * **No delete, and the only update is the void.** A sale is a financial record: it is corrected by
 * a void that leaves the original in place, exactly as a stock mistake is corrected by a
 * compensating movement rather than an edit. `SaleLedgerTest` asserts the absence.
 *
 * The transaction that ties a sale to its stock movements is not here — it spans DAOs, so it lives
 * in `Transactions.kt` and is used by the repository.
 */
@Dao
interface SaleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<SaleLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    /**
     * The next human-facing number.
     *
     * Read inside the sale's own transaction, so two tills cannot both read the same one — and the
     * unique index means that if they somehow did, the second insert fails loudly.
     */
    @Query("SELECT COALESCE(MAX(receiptNumber), 0) + 1 FROM sale")
    suspend fun nextReceiptNumber(): Long

    @Query("SELECT * FROM sale WHERE id = :id")
    suspend fun getById(id: String): SaleEntity?

    @Query("SELECT * FROM sale WHERE receiptNumber = :receiptNumber")
    suspend fun getByReceiptNumber(receiptNumber: Long): SaleEntity?

    @Query("SELECT * FROM sale_line WHERE saleId = :saleId ORDER BY lineNumber")
    suspend fun getLines(saleId: String): List<SaleLineEntity>

    @Query("SELECT * FROM payment WHERE saleId = :saleId ORDER BY occurredAt")
    suspend fun getPayments(saleId: String): List<PaymentEntity>

    @Query("SELECT * FROM sale ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SaleEntity>>

    /**
     * Marks a sale void. Guarded on the current status so a double submit cannot void twice and
     * write a second set of compensating movements.
     */
    @Query(
        """
        UPDATE sale SET status = 'VOIDED', voidedAt = :atMillis,
                        voidedByUserId = :userId, voidReason = :reason
        WHERE id = :saleId AND status = 'COMPLETED'
        """,
    )
    suspend fun markVoided(saleId: String, atMillis: Long, userId: String, reason: String): Int

    @Query("SELECT COUNT(*) FROM sale WHERE shiftId = :shiftId AND status = 'COMPLETED'")
    suspend fun countInShift(shiftId: String): Int

    @Query("SELECT COUNT(*) FROM sale WHERE shiftId = :shiftId AND status = 'VOIDED'")
    suspend fun countVoidedInShift(shiftId: String): Int

    @Query(
        """
        SELECT COALESCE(SUM(subtotalPiastres), 0) FROM sale
        WHERE shiftId = :shiftId AND status = 'COMPLETED'
        """,
    )
    suspend fun sumSubtotalInShift(shiftId: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(discountPiastres), 0) FROM sale
        WHERE shiftId = :shiftId AND status = 'COMPLETED'
        """,
    )
    suspend fun sumDiscountInShift(shiftId: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(taxPiastres), 0) FROM sale
        WHERE shiftId = :shiftId AND status = 'COMPLETED'
        """,
    )
    suspend fun sumTaxInShift(shiftId: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(totalPiastres), 0) FROM sale
        WHERE shiftId = :shiftId AND status = 'COMPLETED'
        """,
    )
    suspend fun sumTotalInShift(shiftId: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(changePiastres), 0) FROM sale
        WHERE shiftId = :shiftId AND status = 'COMPLETED'
        """,
    )
    suspend fun sumChangeInShift(shiftId: String): Long

    /**
     * What a tender type actually put in the drawer.
     *
     * `amountPiastres` is the settled portion, not what the customer handed over, so change is
     * already netted off and must not be subtracted again.
     */
    @Query(
        """
        SELECT COALESCE(SUM(p.amountPiastres), 0) FROM payment p
        JOIN sale s ON s.id = p.saleId
        WHERE s.shiftId = :shiftId AND s.status = 'COMPLETED' AND p.method = :method
        """,
    )
    suspend fun sumTakenInShift(shiftId: String, method: TenderMethod): Long

    /** Should normally be zero — a sale rung up outside any shift is worth someone noticing. */
    @Query(
        "SELECT COUNT(*) FROM sale WHERE shiftId IS NULL AND status = 'COMPLETED' " +
            "AND occurredAt >= :fromMillis AND occurredAt <= :toMillis",
    )
    suspend fun countOutsideShift(fromMillis: Long, toMillis: Long): Int
}

@Dao
interface ShiftDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(shift: ShiftEntity)

    @Query(
        """
        UPDATE shift SET closedAt = :atMillis, closedByUserId = :userId,
                         countedCashPiastres = :countedCash, expectedCashPiastres = :expectedCash,
                         note = :note
        WHERE id = :shiftId AND closedAt IS NULL
        """,
    )
    suspend fun close(
        shiftId: String,
        atMillis: Long,
        userId: String,
        countedCash: Long,
        expectedCash: Long,
        note: String?,
    ): Int

    @Query("SELECT * FROM shift WHERE id = :id")
    suspend fun getById(id: String): ShiftEntity?

    /**
     * The open shift at a location, if there is one.
     *
     * One at a time, by design: two open shifts on one till make every cash figure ambiguous.
     */
    @Query("SELECT * FROM shift WHERE locationId = :locationId AND closedAt IS NULL ORDER BY openedAt DESC LIMIT 1")
    suspend fun getOpen(locationId: String): ShiftEntity?

    @Query("SELECT * FROM shift WHERE locationId = :locationId AND closedAt IS NULL ORDER BY openedAt DESC LIMIT 1")
    fun observeOpen(locationId: String): Flow<ShiftEntity?>

    @Query("SELECT * FROM shift ORDER BY openedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ShiftEntity>
}

/**
 * Parked carts.
 *
 * The one table in the schema that deletes freely: a held cart is meant to be thrown away, and its
 * lines go with it through `onDelete = CASCADE`.
 */
@Dao
interface HeldSaleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(held: HeldSaleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<HeldSaleLineEntity>)

    @Query("SELECT * FROM held_sale WHERE locationId = :locationId ORDER BY heldAt")
    suspend fun list(locationId: String): List<HeldSaleEntity>

    @Query("SELECT * FROM held_sale WHERE locationId = :locationId ORDER BY heldAt")
    fun observe(locationId: String): Flow<List<HeldSaleEntity>>

    @Query("SELECT * FROM held_sale WHERE id = :id")
    suspend fun getById(id: String): HeldSaleEntity?

    @Query("SELECT * FROM held_sale_line WHERE heldSaleId = :heldSaleId ORDER BY lineNumber")
    suspend fun getLines(heldSaleId: String): List<HeldSaleLineEntity>

    @Query("DELETE FROM held_sale WHERE id = :id")
    suspend fun delete(id: String)
}
