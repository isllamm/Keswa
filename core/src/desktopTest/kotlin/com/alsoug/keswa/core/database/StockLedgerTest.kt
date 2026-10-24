package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.domain.model.MovementReason
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StockLedgerTest {

    private val db = createTestDatabase()
    private val ledger get() = db.stockLedgerDao()

    @AfterTest
    fun tearDown() = db.close()

    private fun movement(
        id: String,
        quantity: Int,
        reason: MovementReason,
        at: Long,
    ) = StockMovementEntity(
        id = id,
        variantId = VARIANT_TEE_NAVY,
        locationId = SHOP_ID,
        quantity = quantity,
        reason = reason,
        refType = null,
        refId = null,
        occurredAt = at,
        userId = "user-1",
    )

    @Test
    fun `recording a movement updates the cached total in the same breath`() = runTest {
        // Given a seeded shop
        db.seedBaseData()

        // When 10 pieces are received and 2 are sold
        ledger.record(movement("m1", 10, MovementReason.RECEIPT, 100))
        ledger.record(movement("m2", -2, MovementReason.SALE, 200))

        // Then the projection agrees with the ledger
        val onHand = ledger.getOnHand(VARIANT_TEE_NAVY, SHOP_ID)
        assertEquals(8, onHand?.quantity)
        assertEquals(200L, onHand?.lastMovementAt)
        assertEquals(8, ledger.sumQuantity(VARIANT_TEE_NAVY, SHOP_ID))
    }

    @Test
    fun `rebuilding from the ledger reproduces the projection exactly`() = runTest {
        // Given a long, random, adversarial run of movements
        db.seedBaseData()
        val random = Random(seed = 20260914)
        val reasons = MovementReason.entries
        val movements = (1..200).map { index ->
            movement(
                id = "m$index",
                quantity = random.nextInt(-9, 10),
                reason = reasons[random.nextInt(reasons.size)],
                at = index.toLong() * 10,
            )
        }
        ledger.recordAll(movements)
        val incremental = ledger.getAllOnHand()

        // When the projection is thrown away and regenerated from the ledger alone
        ledger.rebuildProjection()

        // Then it comes back identical — the invariant the whole design rests on
        assertEquals(incremental, ledger.getAllOnHand())
        assertEquals(movements.sumOf { it.quantity }, ledger.sumQuantity(VARIANT_TEE_NAVY, SHOP_ID))
    }

    @Test
    fun `a correction is an extra movement, never an edit`() = runTest {
        // Given a receipt entered with the wrong quantity
        db.seedBaseData()
        ledger.record(movement("m1", 100, MovementReason.RECEIPT, 100))

        // When it is corrected
        ledger.record(movement("m2", -90, MovementReason.ADJUSTMENT, 200))

        // Then both rows survive and the total is right — history is not rewritten
        assertEquals(2, ledger.movementCount())
        assertEquals(10, ledger.getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
        val reasons = ledger.getMovements(VARIANT_TEE_NAVY, SHOP_ID).map { it.reason }
        assertEquals(listOf(MovementReason.RECEIPT, MovementReason.ADJUSTMENT), reasons)
    }

    @Test
    fun `movement ids are rejected twice over, so a double submit cannot duplicate stock`() = runTest {
        db.seedBaseData()
        ledger.record(movement("m1", 5, MovementReason.RECEIPT, 100))

        // When the same client-generated id arrives again
        val duplicated = runCatching { ledger.record(movement("m1", 5, MovementReason.RECEIPT, 100)) }

        // Then it is refused and stock is unchanged
        assertTrue(duplicated.isFailure)
        assertEquals(5, ledger.getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
        assertEquals(1, ledger.movementCount())
    }
}
