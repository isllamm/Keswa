package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.data.repository.StockReceiptRepositoryImpl
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.VariantEntity
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ReceiptLineInput
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Receiving: the only place cost is decided, and the only place stock arrives.
 *
 * A draft that moves stock, or a post that moves it twice, would each be invisible until an owner
 * noticed a figure they could not explain months later.
 */
class StockReceiptTest {

    private val database = createTestDatabase()

    private class SequentialIds : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    private val repository = StockReceiptRepositoryImpl(
        database = database,
        dao = database.stockReceiptDao(),
        variants = database.variantDao(),
        ledger = database.stockLedgerDao(),
        prices = database.priceDao(),
        ids = SequentialIds(),
    ) { NOW }

    @AfterTest
    fun tearDown() = database.close()

    private companion object {
        const val NOW = 1_757_100_000_000L
    }

    private suspend fun draftWithLine(
        quantity: Int = 10,
        unitCost: Money = Money.ofPounds(140),
        sellPrice: Money? = null,
    ): String {
        database.seedBaseData()
        val receipt = repository
            .createDraft("rec-1", "INV-1", "Nile Textiles", SHOP_ID, "usr-1", NOW)
            .getOrThrow()
        repository.putLine("line-1", receipt.id, VARIANT_TEE_NAVY, quantity, unitCost, sellPrice).getOrThrow()
        return receipt.id
    }

    @Test
    fun `a draft moves no stock`() = runBlocking {
        val receiptId = draftWithLine()

        assertNull(database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID))
        assertEquals(0, database.stockLedgerDao().movementCount())
        assertTrue(repository.getById(receiptId).getOrThrow()!!.isDraft)
    }

    @Test
    fun `posting moves the stock and recalculates cost`() = runBlocking {
        // Seeded variant costs 120; ten already on the shelf.
        val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140))
        database.stockLedgerDao().record(receipt(quantity = 10))

        val posted = repository.post(receiptId, "usr-1", NOW).getOrThrow()

        assertEquals(DocumentStatus.POSTED, posted.receipt.status)
        assertEquals(20, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)

        // 10 at 120 plus 10 at 140 → 130.
        val change = posted.costChanges.single()
        assertEquals(Money.ofPounds(120), change.before)
        assertEquals(Money.ofPounds(130), change.after)
        assertEquals(13_000, database.variantDao().getById(VARIANT_TEE_NAVY)?.costPiastres)
    }

    @Test
    fun `the movement carries what this delivery cost, not what the variant costs now`() =
        runBlocking {
            val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140))
            database.stockLedgerDao().record(receipt(quantity = 10))

            repository.post(receiptId, "usr-1", NOW).getOrThrow()

            val movement = database.stockLedgerDao()
                .getMovementsForReference(StockReceiptRepositoryImpl.REF_RECEIPT, receiptId)
                .single()
            // 140, not the new average of 130 — the ledger describes its own cost basis (KD-008).
            assertEquals(14_000, movement.unitCostPiastres)
            assertEquals(MovementReason.RECEIPT, movement.reason)
            assertEquals("usr-1", movement.userId)
        }

    @Test
    fun `a receipt cannot be posted twice`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10)
        repository.post(receiptId, "usr-1", NOW).getOrThrow()

        assertTrue(repository.post(receiptId, "usr-1", NOW).isFailure)

        // Ten, not twenty: a double click must not receive the same carton again.
        assertEquals(10, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
        assertEquals(1, database.stockLedgerDao().movementCount())
    }

    @Test
    fun `a failed post leaves the draft untouched`() = runBlocking {
        database.seedBaseData()
        val receipt = repository
            .createDraft("rec-1", "INV-1", "Nile Textiles", SHOP_ID, "usr-1", NOW)
            .getOrThrow()

        // An empty receipt has nothing to post, and must not close itself trying.
        assertTrue(repository.post(receipt.id, "usr-1", NOW).isFailure)

        assertTrue(repository.getById(receipt.id).getOrThrow()!!.isDraft)
        assertEquals(0, database.stockLedgerDao().movementCount())
    }

    @Test
    fun `scanning the same carton twice corrects the line rather than adding another`() =
        runBlocking {
            val receiptId = draftWithLine(quantity = 10)

            repository.putLine("line-2", receiptId, VARIANT_TEE_NAVY, 25, Money.ofPounds(138))
                .getOrThrow()

            val lines = repository.getById(receiptId).getOrThrow()!!.lines
            assertEquals(1, lines.size)
            assertEquals(25, lines.single().quantity)
            assertEquals(Money.ofPounds(138), lines.single().unitCost)
        }

    @Test
    fun `a posted receipt is closed to further lines`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10)
        repository.post(receiptId, "usr-1", NOW).getOrThrow()

        assertTrue(
            repository.putLine("line-2", receiptId, VARIANT_TEE_NAVY, 5, Money.ofPounds(100))
                .isFailure,
        )
    }

    @Test
    fun `only a draft can be discarded`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10)
        repository.post(receiptId, "usr-1", NOW).getOrThrow()

        assertTrue(repository.discardDraft(receiptId).isFailure)
        assertEquals(DocumentStatus.POSTED, repository.getById(receiptId).getOrThrow()!!.status)
    }

    @Test
    fun `the total is the sum of the lines`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140))

        val posted = repository.post(receiptId, "usr-1", NOW).getOrThrow()

        assertEquals(Money.ofPounds(1_400), posted.receipt.totalCost)
        assertEquals(10, posted.receipt.pieceCount)
    }

    @Test
    fun `a price on a draft line does not reach the till`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140), sellPrice = Money.ofPounds(220))
        database.seedPriceList(pricePiastres = 18_000)

        assertEquals(Money.ofPounds(220), repository.getById(receiptId).getOrThrow()!!.lines.single().sellPrice)
        assertEquals(
            18_000L,
            database.priceDao().getEffectivePrice(VARIANT_TEE_NAVY, PRICE_LIST_ID, NOW)?.pricePiastres,
        )
        assertEquals(1, database.priceDao().historyFor(VARIANT_TEE_NAVY).size)
    }

    @Test
    fun `posting puts the new price live from the posting time and closes the old one`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140), sellPrice = Money.ofPounds(220))
        database.seedPriceList(pricePiastres = 18_000)

        repository.post(receiptId, "usr-1", NOW).getOrThrow()

        assertEquals(
            22_000L,
            database.priceDao().getEffectivePrice(VARIANT_TEE_NAVY, PRICE_LIST_ID, NOW)?.pricePiastres,
        )
        val history = database.priceDao().historyFor(VARIANT_TEE_NAVY)
        assertEquals(2, history.size)
        val newest = history.first()
        val previous = history.last()
        assertEquals(22_000L, newest.pricePiastres)
        assertEquals(NOW, newest.validFrom)
        assertNull(newest.validTo)
        assertEquals(18_000L, previous.pricePiastres)
        assertEquals(NOW, previous.validTo)
    }

    @Test
    fun `a line whose price equals the live price writes no price row`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140), sellPrice = Money.ofPounds(180))
        database.seedPriceList(pricePiastres = 18_000)

        repository.post(receiptId, "usr-1", NOW).getOrThrow()

        val history = database.priceDao().historyFor(VARIANT_TEE_NAVY)
        assertEquals(1, history.size)
        assertNull(history.single().validTo)
    }

    @Test
    fun `a post that fails part-way changes no stock, no cost and no price`() = runBlocking {
        // No default price list seeded, so posting fails after the stock and cost loop when it tries
        // to apply the line's sell price. The transaction must roll back all of it.
        val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140), sellPrice = Money.ofPounds(220))

        assertTrue(repository.post(receiptId, "usr-1", NOW).isFailure)

        assertTrue(repository.getById(receiptId).getOrThrow()!!.isDraft)
        assertNull(database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID))
        assertEquals(0, database.stockLedgerDao().movementCount())
        assertEquals(12_000L, database.variantDao().getById(VARIANT_TEE_NAVY)?.costPiastres)
        assertTrue(database.priceDao().historyFor(VARIANT_TEE_NAVY).isEmpty())
    }

    @Test
    fun `discarding a draft leaves the price alone`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140), sellPrice = Money.ofPounds(220))
        database.seedPriceList(pricePiastres = 18_000)

        repository.discardDraft(receiptId).getOrThrow()

        assertNull(repository.getById(receiptId).getOrThrow())
        assertEquals(
            18_000L,
            database.priceDao().getEffectivePrice(VARIANT_TEE_NAVY, PRICE_LIST_ID, NOW)?.pricePiastres,
        )
        assertEquals(1, database.priceDao().historyFor(VARIANT_TEE_NAVY).size)
    }

    @Test
    fun `putLines with one bad line saves none of them`() = runBlocking {
        database.seedBaseData()
        database.colourDao().upsert(ColourEntity("col-white", "White", "أبيض", "#ffffff", 1, isActive = true))
        database.variantDao().insert(
            VariantEntity("var-tee-white", PRODUCT_TEE, "col-white", "KSW-TSH-022-WH", 12_000, true, 0, 0),
        )
        val receipt = repository
            .createDraft("rec-1", "INV-1", "Nile Textiles", SHOP_ID, "usr-1", NOW)
            .getOrThrow()

        val result = repository.putLines(
            receipt.id,
            listOf(
                ReceiptLineInput("line-1", VARIANT_TEE_NAVY, 10, Money.ofPounds(140), Money.ofPounds(200)),
                ReceiptLineInput("line-2", "var-tee-white", 0, Money.ofPounds(140), Money.ofPounds(200)),
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(repository.getById(receipt.id).getOrThrow()!!.lines.isEmpty())
    }

    @Test
    fun `putLines for a colour already on the delivery replaces its line`() = runBlocking {
        val receiptId = draftWithLine(quantity = 10, unitCost = Money.ofPounds(140), sellPrice = Money.ofPounds(200))

        val updated = repository.putLines(
            receiptId,
            listOf(
                ReceiptLineInput("line-new", VARIANT_TEE_NAVY, 24, Money.ofPounds(135), Money.ofPounds(210)),
            ),
        ).getOrThrow()

        val line = updated.lines.single()
        assertEquals("line-1", line.id)
        assertEquals(1, line.lineNumber)
        assertEquals(24, line.quantity)
        assertEquals(Money.ofPounds(135), line.unitCost)
        assertEquals(Money.ofPounds(210), line.sellPrice)
    }
}
