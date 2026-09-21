package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.data.repository.StockCountRepositoryImpl
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.model.MovementReason
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A count is only worth doing if it is blind, so that is what most of this file asserts.
 */
class StockCountTest {

    private val database = createTestDatabase()

    private class SequentialIds : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    private val repository = StockCountRepositoryImpl(
        database = database,
        dao = database.stockCountDao(),
        variants = database.variantDao(),
        ledger = database.stockLedgerDao(),
        ids = SequentialIds(),
    )

    @AfterTest
    fun tearDown() = database.close()

    private companion object {
        const val NOW = 1_757_200_000_000L
    }

    private suspend fun openCountWith(onShelf: Int, counted: Int): String {
        database.seedBaseData()
        database.stockLedgerDao().record(receipt(quantity = onShelf))
        val count = repository.start("cnt-1", SHOP_ID, "usr-1", NOW).getOrThrow()
        repository.putLine("line-1", count.id, VARIANT_TEE_NAVY, counted).getOrThrow()
        return count.id
    }

    @Test
    fun `an open count never reveals what was expected`() = runBlocking {
        val countId = openCountWith(onShelf = 12, counted = 9)

        val count = repository.getById(countId).getOrThrow()!!
        val line = count.lines.single()

        assertEquals(9, line.counted)
        assertNull(line.expected, "a count that can be peeked at finds nothing")
        assertNull(line.variance)
    }

    @Test
    fun `posting settles against the ledger and writes the difference`() = runBlocking {
        val countId = openCountWith(onShelf = 12, counted = 9)

        val posted = repository.post(countId, "usr-2", "Friday count", NOW).getOrThrow()

        assertEquals(DocumentStatus.POSTED, posted.status)
        val line = posted.lines.single()
        assertEquals(12, line.expected)
        assertEquals(-3, line.variance)

        val movement = database.stockLedgerDao()
            .getMovementsForReference(StockCountRepositoryImpl.REF_COUNT, countId)
            .single()
        assertEquals(-3, movement.quantity)
        assertEquals(MovementReason.COUNT, movement.reason)
        assertEquals("Friday count", movement.note)
        assertEquals("usr-2", movement.userId)

        // The shelf is now what the counter said it was.
        assertEquals(9, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `a line that agrees writes nothing`() = runBlocking {
        val countId = openCountWith(onShelf = 12, counted = 12)

        val posted = repository.post(countId, "usr-2", null, NOW).getOrThrow()

        assertEquals(0, posted.lines.single().variance)
        // A ledger entry saying "nothing changed" is noise that makes the real ones harder to find.
        assertEquals(
            0,
            database.stockLedgerDao()
                .getMovementsForReference(StockCountRepositoryImpl.REF_COUNT, countId).size,
        )
        assertEquals(1, database.stockLedgerDao().movementCount())
    }

    @Test
    fun `a surplus is recorded as readily as a shortfall`() = runBlocking {
        val countId = openCountWith(onShelf = 10, counted = 13)

        val posted = repository.post(countId, "usr-2", null, NOW).getOrThrow()

        assertEquals(3, posted.lines.single().variance)
        assertEquals(13, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `counting the same variant twice corrects the line`() = runBlocking {
        val countId = openCountWith(onShelf = 12, counted = 9)

        repository.putLine("line-2", countId, VARIANT_TEE_NAVY, 11).getOrThrow()

        val lines = repository.getById(countId).getOrThrow()!!.lines
        assertEquals(1, lines.size)
        assertEquals(11, lines.single().counted)
    }

    @Test
    fun `a count cannot be posted twice`() = runBlocking {
        val countId = openCountWith(onShelf = 12, counted = 9)
        repository.post(countId, "usr-2", null, NOW).getOrThrow()

        assertTrue(repository.post(countId, "usr-2", null, NOW).isFailure)

        assertEquals(9, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `only one count is open at a location`() = runBlocking {
        openCountWith(onShelf = 12, counted = 9)

        assertTrue(repository.start("cnt-2", SHOP_ID, "usr-1", NOW).isFailure)
    }

    @Test
    fun `a posted count is closed to further lines`() = runBlocking {
        val countId = openCountWith(onShelf = 12, counted = 9)
        repository.post(countId, "usr-2", null, NOW).getOrThrow()

        assertTrue(repository.putLine("line-9", countId, VARIANT_TEE_NAVY, 4).isFailure)
    }

    @Test
    fun `the movement carries the cost basis so a write-off can be valued`() = runBlocking {
        val countId = openCountWith(onShelf = 12, counted = 9)

        repository.post(countId, "usr-2", null, NOW).getOrThrow()

        val movement = database.stockLedgerDao()
            .getMovementsForReference(StockCountRepositoryImpl.REF_COUNT, countId)
            .single()
        // Three shirts lost at 120 each is a number the owner can act on; "three shirts" is not.
        assertEquals(12_000, movement.unitCostPiastres)
    }
}
