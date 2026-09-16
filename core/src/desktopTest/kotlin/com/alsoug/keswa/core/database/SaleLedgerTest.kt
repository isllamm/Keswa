package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.data.repository.SaleRepositoryImpl
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.Payment
import com.alsoug.keswa.core.domain.model.SaleLine
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.SaleDraft
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The invariants a till cannot be wrong about: a sale is all-or-nothing, it moves stock exactly
 * once per line, and a void reverses rather than erases.
 */
class SaleLedgerTest {

    private val database = createTestDatabase()

    private class SequentialIds : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    private val repository = SaleRepositoryImpl(
        database = database,
        dao = database.saleDao(),
        ledger = database.stockLedgerDao(),
        ids = SequentialIds(),
    )

    @AfterTest
    fun tearDown() = database.close()

    private fun draft(
        id: String,
        quantity: Int = 2,
        total: Money = Money.ofPounds(360),
        shiftId: String? = null,
    ) = SaleDraft(
        id = id,
        locationId = SHOP_ID,
        priceListId = PRICE_LIST_ID,
        userId = "user-1",
        shiftId = shiftId,
        subtotal = total,
        discount = Money.ZERO,
        tax = Money.ZERO,
        total = total,
        tendered = total,
        change = Money.ZERO,
        occurredAt = 1_757_000_000_000,
        lines = listOf(
            SaleLine(
                id = "$id-line-1",
                saleId = id,
                lineNumber = 1,
                variantId = VARIANT_TEE_NAVY,
                description = "Round-neck t-shirt — Navy",
                descriptionAr = "تيشيرت — كحلي",
                quantity = quantity,
                unitPrice = Money.ofPounds(180),
                lineDiscount = Money.ZERO,
                orderDiscount = Money.ZERO,
                lineTotal = total,
                tax = Money.ZERO,
                unitCost = Money.ofPounds(120),
            ),
        ),
        payments = listOf(
            Payment(
                id = "$id-pay-1",
                saleId = id,
                method = TenderMethod.CASH,
                amount = total,
                tendered = total,
                reference = null,
                occurredAt = 1_757_000_000_000,
            ),
        ),
    )

    private suspend fun seed() {
        database.seedBaseData()
        database.seedPriceList()
        database.stockLedgerDao().record(receipt(quantity = 10))
    }

    @Test
    fun `a sale writes its header, lines, tenders and exactly one movement per line`() = runBlocking {
        seed()

        val sale = repository.record(draft("sale-1")).getOrThrow()

        assertEquals(1, sale.receiptNumber)
        assertEquals(SaleStatus.COMPLETED, sale.status)
        assertEquals(1, database.saleDao().getLines("sale-1").size)
        assertEquals(1, database.saleDao().getPayments("sale-1").size)

        val movements = database.stockLedgerDao()
            .getMovementsForReference(SaleRepositoryImpl.REF_SALE, "sale-1")
        assertEquals(1, movements.size)
        assertEquals(-2, movements.single().quantity)
        assertEquals(MovementReason.SALE, movements.single().reason)
        assertEquals("user-1", movements.single().userId)

        assertEquals(8, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `nothing is written when a sale fails part way through`() = runBlocking {
        seed()

        // A line pointing at a variant that does not exist trips the foreign key *after* the
        // header has been inserted — which is exactly the half-written state the transaction exists
        // to prevent.
        val broken = draft("sale-bad").let { original ->
            original.copy(lines = original.lines.map { it.copy(variantId = "no-such-variant") })
        }

        assertTrue(repository.record(broken).isFailure)

        assertNull(database.saleDao().getById("sale-bad"))
        assertEquals(0, database.saleDao().getLines("sale-bad").size)
        assertEquals(0, database.saleDao().getPayments("sale-bad").size)
        // The receipt on the opening stock is the only movement there should be.
        assertEquals(1, database.stockLedgerDao().movementCount())
        assertEquals(10, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `receipt numbers run consecutively from one`() = runBlocking {
        seed()

        val first = repository.record(draft("sale-1")).getOrThrow()
        val second = repository.record(draft("sale-2")).getOrThrow()
        val third = repository.record(draft("sale-3")).getOrThrow()

        assertEquals(listOf(1L, 2L, 3L), listOf(first, second, third).map { it.receiptNumber })
    }

    @Test
    fun `voiding reverses the stock and keeps the sale`() = runBlocking {
        seed()
        repository.record(draft("sale-1")).getOrThrow()

        val voided = repository.void("sale-1", "admin-1", "wrong item", 1_757_000_100_000).getOrThrow()

        assertEquals(SaleStatus.VOIDED, voided.status)
        assertEquals("admin-1", voided.voidedByUserId)
        assertEquals("wrong item", voided.voidReason)

        // Back to what it was before the sale, and both movements are still on the record.
        assertEquals(10, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
        val reversal = database.stockLedgerDao()
            .getMovementsForReference(SaleRepositoryImpl.REF_VOID, "sale-1")
        assertEquals(1, reversal.size)
        assertEquals(2, reversal.single().quantity)
        // Same reason as the sale, so "units sold" nets correctly with no special case.
        assertEquals(MovementReason.SALE, reversal.single().reason)

        assertNotNull(database.saleDao().getById("sale-1"))
        assertEquals(1, database.saleDao().getLines("sale-1").size)
    }

    @Test
    fun `a sale cannot be voided twice`() = runBlocking {
        seed()
        repository.record(draft("sale-1")).getOrThrow()
        repository.void("sale-1", "admin-1", "wrong item", 1_757_000_100_000).getOrThrow()

        assertTrue(repository.void("sale-1", "admin-1", "again", 1_757_000_200_000).isFailure)

        // One reversal, not two — otherwise a double submit invents stock.
        assertEquals(10, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `selling more than the shop has is recorded, and goes negative`() = runBlocking {
        seed()

        repository.record(draft("sale-1", quantity = 12)).getOrThrow()

        // Allowed on purpose: the stock figure is more often wrong than the customer's hands, and
        // refusing the sale teaches the shop to work around the till.
        assertEquals(-2, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }
}
