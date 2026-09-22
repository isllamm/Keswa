package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.alsoug.keswa.core.database.entities.AssortmentPackEntity
import com.alsoug.keswa.core.database.entities.AssortmentPackLineEntity
import com.alsoug.keswa.core.database.entities.CustomerEntity
import com.alsoug.keswa.core.database.entities.CustomerLedgerEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Upsert
    suspend fun upsert(customer: CustomerEntity)

    @Query("SELECT * FROM customer WHERE id = :id")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT * FROM customer WHERE isActive = 1 ORDER BY name")
    suspend fun getAll(): List<CustomerEntity>

    @Query("SELECT * FROM customer WHERE isActive = 1 ORDER BY name")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT * FROM customer
        WHERE isActive = 1 AND (name LIKE '%' || :term || '%' OR phone LIKE '%' || :term || '%')
        ORDER BY name LIMIT :limit
        """,
    )
    suspend fun search(term: String, limit: Int): List<CustomerEntity>
}

/**
 * What each customer owes, as an append-only ledger.
 *
 * **No update and no delete, deliberately** — the same discipline as `stock_movement`, and the same
 * three payoffs: the trail is auditable, appends merge without conflict resolution, and a balance
 * can never be quietly edited. A mistake is a compensating entry.
 * `ReceivablesAreAppendOnlyTest` asserts the absence.
 */
@Dao
interface CustomerLedgerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: CustomerLedgerEntryEntity)

    /** The balance, and the only definition of it there is. */
    @Query("SELECT COALESCE(SUM(amountPiastres), 0) FROM customer_ledger_entry WHERE customerId = :customerId")
    suspend fun balance(customerId: String): Long

    @Query(
        """
        SELECT * FROM customer_ledger_entry
        WHERE customerId = :customerId AND occurredAt >= :from AND occurredAt < :to
        ORDER BY occurredAt, id
        """,
    )
    suspend fun statement(customerId: String, from: Long, to: Long): List<CustomerLedgerEntryEntity>

    @Query(
        "SELECT * FROM customer_ledger_entry WHERE customerId = :customerId ORDER BY occurredAt, id",
    )
    suspend fun allFor(customerId: String): List<CustomerLedgerEntryEntity>

    @Query("SELECT * FROM customer_ledger_entry WHERE refType = :refType AND refId = :refId")
    suspend fun forReference(refType: String, refId: String): List<CustomerLedgerEntryEntity>

    /**
     * Every customer who owes anything, worst first.
     *
     * `HAVING` rather than a stored balance column: there is no balance to store, only a sum.
     */
    @Query(
        """
        SELECT customerId FROM customer_ledger_entry
        GROUP BY customerId
        HAVING SUM(amountPiastres) > 0
        ORDER BY SUM(amountPiastres) DESC
        """,
    )
    suspend fun customersOwing(): List<String>

    /**
     * Debits still outstanding, oldest first, for ageing and for allocating a payment in reports.
     *
     * Allocation is a view, never a stored fact: a customer pays 5,000 against four invoices, and
     * forcing the shop to say which one at the moment the money arrives gets it wrong as often
     * as not.
     */
    @Query(
        """
        SELECT * FROM customer_ledger_entry
        WHERE customerId = :customerId AND amountPiastres > 0
        ORDER BY occurredAt, id
        """,
    )
    suspend fun debits(customerId: String): List<CustomerLedgerEntryEntity>

    @Query("SELECT COALESCE(SUM(amountPiastres), 0) FROM customer_ledger_entry WHERE customerId = :customerId AND amountPiastres < 0")
    suspend fun credited(customerId: String): Long
}

@Dao
interface AssortmentPackDao {

    @Upsert
    suspend fun upsert(pack: AssortmentPackEntity)

    @Upsert
    suspend fun upsertLine(line: AssortmentPackLineEntity)

    @Query("SELECT * FROM assortment_pack WHERE isActive = 1 ORDER BY name")
    suspend fun getAll(): List<AssortmentPackEntity>

    @Query("SELECT * FROM assortment_pack WHERE id = :id")
    suspend fun getById(id: String): AssortmentPackEntity?

    @Query("SELECT * FROM assortment_pack_line WHERE packId = :packId")
    suspend fun getLines(packId: String): List<AssortmentPackLineEntity>

    @Query("DELETE FROM assortment_pack_line WHERE id = :id")
    suspend fun deleteLine(id: String)
}
