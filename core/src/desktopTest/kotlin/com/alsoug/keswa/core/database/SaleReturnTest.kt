package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.data.repository.SaleRepositoryImpl
import com.alsoug.keswa.core.data.repository.SaleReturnRepositoryImpl
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.Payment
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.SaleLine
import com.alsoug.keswa.core.domain.model.SaleReturnLine
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ReturnDraft
import com.alsoug.keswa.core.domain.repository.SaleDraft
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Goods coming back, and the money going out with them.
 *
 * The two things that cost real money when they are wrong: returning more than was sold, and a
 * damaged garment going back on the rail.
 */
class SaleReturnTest {

    private val database = createTestDatabase()

    private class SequentialIds : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    // One generator across both repositories, as Koin binds one `IdGenerator` singleton. Two
    // would hand out the same ids and collide on `stock_movement.id` — which is the unique index
    // doing exactly its job.
    private val ids = SequentialIds()

    private val sales = SaleRepositoryImpl(
        database = database,
        dao = database.saleDao(),
        ledger = database.stockLedgerDao(),
        ids = ids,
    )

    private val returns = SaleReturnRepositoryImpl(
        database = database,
        dao = database.saleReturnDao(),
        sales = database.saleDao(),
        ledger = database.stockLedgerDao(),
        ids = ids,
    )

    @AfterTest
    fun tearDown() = database.close()

    private companion object {
        const val SOLD_AT = 1_757_000_000_000L
        const val RETURNED_AT = 1_757_100_000_000L
    }

    /** Ten on the shelf, three sold at 180 each. */
    private suspend fun sellThree(): String {
        database.seedBaseData()
        database.seedPriceList()
        database.stockLedgerDao().record(receipt(quantity = 10))

        sales.record(
            SaleDraft(
                id = "sale-1",
                locationId = SHOP_ID,
                priceListId = PRICE_LIST_ID,
                userId = "usr-seller",
                shiftId = "shift-1",
                subtotal = Money.ofPounds(540),
                discount = Money.ZERO,
                tax = Money.ZERO,
                total = Money.ofPounds(540),
                tendered = Money.ofPounds(540),
                change = Money.ZERO,
                occurredAt = SOLD_AT,
                lines = listOf(
                    SaleLine(
                        id = "sale-1-line-1",
                        saleId = "sale-1",
                        lineNumber = 1,
                        variantId = VARIANT_TEE_NAVY,
                        description = "Round-neck t-shirt — Navy",
                        descriptionAr = "تيشيرت — كحلي",
                        quantity = 3,
                        unitPrice = Money.ofPounds(180),
                        lineDiscount = Money.ZERO,
                        orderDiscount = Money.ZERO,
                        lineTotal = Money.ofPounds(540),
                        tax = Money.ZERO,
                        unitCost = Money.ofPounds(120),
                    ),
                ),
                payments = listOf(
                    Payment(
                        "sale-1-pay-1", "sale-1", TenderMethod.CASH,
                        Money.ofPounds(540), Money.ofPounds(540), null, SOLD_AT,
                    ),
                ),
            ),
        ).getOrThrow()
        return "sale-1"
    }

    private fun returnDraft(
        id: String,
        quantity: Int,
        condition: ReturnCondition = ReturnCondition.SELLABLE,
        saleId: String? = "sale-1",
        saleLineId: String? = "sale-1-line-1",
    ) = ReturnDraft(
        id = id,
        originalSaleId = saleId,
        locationId = SHOP_ID,
        userId = "usr-seller",
        shiftId = "shift-1",
        reason = "wrong size",
        refundMethod = TenderMethod.CASH,
        refundAmount = Money.ofPounds(180) * quantity,
        subtotal = Money.ofPounds(180) * quantity,
        tax = Money.ZERO,
        occurredAt = RETURNED_AT,
        authorisedByUserId = null,
        lines = listOf(
            SaleReturnLine(
                id = "$id-line-1",
                returnId = id,
                lineNumber = 1,
                saleLineId = saleLineId,
                variantId = VARIANT_TEE_NAVY,
                description = "Round-neck t-shirt — Navy",
                quantity = quantity,
                unitRefund = Money.ofPounds(180),
                lineRefund = Money.ofPounds(180) * quantity,
                condition = condition,
                unitCost = Money.ofPounds(120),
            ),
        ),
    )

    @Test
    fun `a sellable return goes back on the rail`() = runBlocking {
        sellThree()
        assertEquals(7, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)

        val recorded = returns.record(returnDraft("ret-1", quantity = 1)).getOrThrow()

        assertEquals(1L, recorded.returnNumber)
        assertEquals(SaleStatus.COMPLETED, recorded.status)
        assertEquals(8, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)

        val movements = database.stockLedgerDao()
            .getMovementsForReference(SaleReturnRepositoryImpl.REF_RETURN, "ret-1")
        assertEquals(1, movements.size)
        assertEquals(1, movements.single().quantity)
        assertEquals(MovementReason.RETURN, movements.single().reason)
    }

    @Test
    fun `a damaged return writes both halves of what happened`() = runBlocking {
        sellThree()

        returns.record(
            returnDraft("ret-1", quantity = 1, condition = ReturnCondition.DAMAGED),
        ).getOrThrow()

        // The shop took it back and then wrote it off. Both are true, and both are rows — a ledger
        // that skips the first cannot explain where the refund went.
        val movements = database.stockLedgerDao()
            .getMovementsForReference(SaleReturnRepositoryImpl.REF_RETURN, "ret-1")
        assertEquals(2, movements.size)
        assertEquals(setOf(MovementReason.RETURN, MovementReason.DAMAGE), movements.map { it.reason }.toSet())
        assertEquals(0, movements.sumOf { it.quantity })

        // Net effect on the shelf: nothing.
        assertEquals(7, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `the return line carries the cost the sale line carried`() = runBlocking {
        sellThree()

        returns.record(returnDraft("ret-1", quantity = 1)).getOrThrow()

        // Not today's cost: a change of mind must not move the moving average.
        val movement = database.stockLedgerDao()
            .getMovementsForReference(SaleReturnRepositoryImpl.REF_RETURN, "ret-1")
            .single()
        assertEquals(12_000, movement.unitCostPiastres)
    }

    @Test
    fun `returnable quantity falls as goods come back`() = runBlocking {
        sellThree()

        assertEquals(3, returns.returnableLines("sale-1").getOrThrow().single().returnable)

        returns.record(returnDraft("ret-1", quantity = 2)).getOrThrow()

        val line = returns.returnableLines("sale-1").getOrThrow().single()
        assertEquals(2, line.alreadyReturned)
        assertEquals(1, line.returnable)
    }

    @Test
    fun `returnable counts every completed return, not just the last`() = runBlocking {
        sellThree()
        returns.record(returnDraft("ret-1", quantity = 1)).getOrThrow()
        returns.record(returnDraft("ret-2", quantity = 1)).getOrThrow()

        // Two visits, two returns — the guard against the same jacket coming back three times.
        assertEquals(1, returns.returnableLines("sale-1").getOrThrow().single().returnable)
    }

    @Test
    fun `the unit price returned is what was actually paid, after discounts`() = runBlocking {
        sellThree()

        // 540 across 3 pieces, whatever the list price said.
        assertEquals(
            Money.ofPounds(180),
            returns.returnableLines("sale-1").getOrThrow().single().unitPrice,
        )
    }

    @Test
    fun `voiding a return reverses it and keeps the record`() = runBlocking {
        sellThree()
        returns.record(returnDraft("ret-1", quantity = 2)).getOrThrow()
        assertEquals(9, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)

        val voided = returns.void("ret-1", "usr-admin", "refunded in error", RETURNED_AT + 1)
            .getOrThrow()

        assertEquals(SaleStatus.VOIDED, voided.status)
        assertEquals("usr-admin", voided.voidedByUserId)
        assertEquals(7, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
        assertNotNull(database.saleReturnDao().getById("ret-1"))

        // And it no longer counts against what is returnable.
        assertEquals(3, returns.returnableLines("sale-1").getOrThrow().single().returnable)
    }

    @Test
    fun `voiding a damaged return reverses both of its movements`() = runBlocking {
        sellThree()
        returns.record(
            returnDraft("ret-1", quantity = 1, condition = ReturnCondition.DAMAGED),
        ).getOrThrow()

        returns.void("ret-1", "usr-admin", "wrong item scanned", RETURNED_AT + 1).getOrThrow()

        val reversal = database.stockLedgerDao()
            .getMovementsForReference(SaleReturnRepositoryImpl.REF_RETURN_VOID, "ret-1")
        assertEquals(2, reversal.size)
        assertEquals(7, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `a return cannot be voided twice`() = runBlocking {
        sellThree()
        returns.record(returnDraft("ret-1", quantity = 1)).getOrThrow()
        returns.void("ret-1", "usr-admin", "error", RETURNED_AT + 1).getOrThrow()

        assertTrue(returns.void("ret-1", "usr-admin", "again", RETURNED_AT + 2).isFailure)

        assertEquals(7, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }

    @Test
    fun `return numbers run consecutively from one`() = runBlocking {
        sellThree()

        val first = returns.record(returnDraft("ret-1", quantity = 1)).getOrThrow()
        val second = returns.record(returnDraft("ret-2", quantity = 1)).getOrThrow()

        assertEquals(listOf(1L, 2L), listOf(first, second).map { it.returnNumber })
    }

    @Test
    fun `a no-receipt return refunds the lowest price the variant ever sold for`() = runBlocking {
        sellThree()
        // A later markdown: the same shirt goes out at 120.
        sales.record(
            SaleDraft(
                id = "sale-2",
                locationId = SHOP_ID,
                priceListId = PRICE_LIST_ID,
                userId = "usr-seller",
                shiftId = null,
                subtotal = Money.ofPounds(120),
                discount = Money.ZERO,
                tax = Money.ZERO,
                total = Money.ofPounds(120),
                tendered = Money.ofPounds(120),
                change = Money.ZERO,
                occurredAt = SOLD_AT + 1,
                lines = listOf(
                    SaleLine(
                        id = "sale-2-line-1",
                        saleId = "sale-2",
                        lineNumber = 1,
                        variantId = VARIANT_TEE_NAVY,
                        description = "Round-neck t-shirt — Navy",
                        descriptionAr = "تيشيرت — كحلي",
                        quantity = 1,
                        unitPrice = Money.ofPounds(120),
                        lineDiscount = Money.ZERO,
                        orderDiscount = Money.ZERO,
                        lineTotal = Money.ofPounds(120),
                        tax = Money.ZERO,
                        unitCost = Money.ofPounds(120),
                    ),
                ),
                payments = emptyList(),
            ),
        ).getOrThrow()

        // Buying at the markdown and returning at full price is the most common refund fraud
        // there is, and it costs exactly the markdown every time.
        assertEquals(Money.ofPounds(120), returns.lowestSoldPrice(VARIANT_TEE_NAVY).getOrThrow())
    }

    @Test
    fun `a variant that never sold has no price to refund at`() = runBlocking {
        database.seedBaseData()

        assertNull(returns.lowestSoldPrice(VARIANT_TEE_NAVY).getOrThrow())
    }

    @Test
    fun `an exchange links the return to its replacement`() = runBlocking {
        sellThree()
        returns.record(returnDraft("ret-1", quantity = 1)).getOrThrow()

        returns.linkExchange("ret-1", "sale-2").getOrThrow()

        val linked = returns.getById("ret-1").getOrThrow()!!
        assertEquals("sale-2", linked.exchangeSaleId)
        assertTrue(linked.isExchange)
    }

    @Test
    fun `a no-receipt return still records and still moves stock`() = runBlocking {
        database.seedBaseData()
        database.seedPriceList()
        database.stockLedgerDao().record(receipt(quantity = 10))

        val recorded = returns.record(
            returnDraft("ret-1", quantity = 1, saleId = null, saleLineId = null),
        ).getOrThrow()

        assertTrue(!recorded.hasReceipt)
        assertEquals(11, database.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
    }
}
