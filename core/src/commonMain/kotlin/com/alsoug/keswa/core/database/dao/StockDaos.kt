package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.entities.StockOnHandEntity
import kotlinx.coroutines.flow.Flow

/**
 * The stock ledger and the projection derived from it.
 *
 * Both live on one DAO so that appending a movement and refreshing the cached total happen inside a
 * single [Transaction] — if they could diverge, every stock figure in the app would be suspect.
 *
 * **The ledger half exposes no update or delete, deliberately.** History is append-only; a mistake
 * is corrected with a compensating `ADJUSTMENT`, never by editing what happened. `StockLedgerDaoTest`
 * asserts the absence, so adding one fails the build rather than quietly eroding the audit trail.
 *
 * The projection half *is* mutable — that is the point of a cache. It can be regenerated from the
 * ledger at any time by [rebuildProjection].
 */
@Dao
abstract class StockLedgerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMovement(movement: StockMovementEntity)

    @Query("SELECT * FROM stock_movement WHERE variantId = :variantId AND locationId = :locationId ORDER BY occurredAt")
    abstract suspend fun getMovements(variantId: String, locationId: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movement WHERE refType = :refType AND refId = :refId")
    abstract suspend fun getMovementsForReference(refType: String, refId: String): List<StockMovementEntity>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_movement WHERE variantId = :variantId AND locationId = :locationId")
    abstract suspend fun sumQuantity(variantId: String, locationId: String): Int

    @Query("SELECT COUNT(*) FROM stock_movement")
    abstract suspend fun movementCount(): Int

    @Query("SELECT * FROM stock_on_hand WHERE variantId = :variantId AND locationId = :locationId")
    abstract suspend fun getOnHand(variantId: String, locationId: String): StockOnHandEntity?

    @Query("SELECT * FROM stock_on_hand ORDER BY variantId, locationId")
    abstract suspend fun getAllOnHand(): List<StockOnHandEntity>

    @Query("SELECT * FROM stock_on_hand WHERE locationId = :locationId")
    abstract fun observeOnHand(locationId: String): Flow<List<StockOnHandEntity>>

    @Query("DELETE FROM stock_on_hand")
    protected abstract suspend fun clearProjection()

    @Query(
        """
        INSERT OR REPLACE INTO stock_on_hand (variantId, locationId, quantity, lastMovementAt)
        SELECT variantId, locationId, SUM(quantity), MAX(occurredAt)
        FROM stock_movement
        GROUP BY variantId, locationId
        """,
    )
    protected abstract suspend fun projectAll()

    @Query(
        """
        INSERT OR REPLACE INTO stock_on_hand (variantId, locationId, quantity, lastMovementAt)
        VALUES (:variantId, :locationId, :quantity, :lastMovementAt)
        """,
    )
    protected abstract suspend fun upsertOnHand(
        variantId: String,
        locationId: String,
        quantity: Int,
        lastMovementAt: Long,
    )

    /**
     * Appends [movement] and refreshes its cached total, atomically.
     */
    @Transaction
    open suspend fun record(movement: StockMovementEntity) {
        insertMovement(movement)
        refresh(movement.variantId, movement.locationId)
    }

    @Transaction
    open suspend fun recordAll(movements: List<StockMovementEntity>) {
        movements.forEach { insertMovement(it) }
        movements.map { it.variantId to it.locationId }
            .distinct()
            .forEach { (variantId, locationId) -> refresh(variantId, locationId) }
    }

    /**
     * Regenerates the entire projection from the ledger.
     *
     * The safety net for everything built on top of this phase: if the cached figures ever drift,
     * they are recoverable rather than corrupt.
     */
    @Transaction
    open suspend fun rebuildProjection() {
        clearProjection()
        projectAll()
    }

    private suspend fun refresh(variantId: String, locationId: String) {
        val total = sumQuantity(variantId, locationId)
        val latest = getMovements(variantId, locationId).maxOfOrNull { it.occurredAt } ?: 0L
        upsertOnHand(variantId, locationId, total, latest)
    }
}
