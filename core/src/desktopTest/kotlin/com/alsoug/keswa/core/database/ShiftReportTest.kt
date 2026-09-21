package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.data.repository.SaleRepositoryImpl
import com.alsoug.keswa.core.data.repository.SaleReturnRepositoryImpl
import com.alsoug.keswa.core.data.repository.ShiftRepositoryImpl
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Payment
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.SaleLine
import com.alsoug.keswa.core.domain.model.SaleReturnLine
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ReturnDraft
import com.alsoug.keswa.core.domain.repository.SaleDraft
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A Z-report exists so a discrepancy is found on the day rather than at the month end.
 *
 * The figure that matters is expected cash, and the way to get it wrong is to subtract change
 * twice — change is already netted off the settled amount, so every till would read short.
 */
class ShiftReportTest {

    private val database = createTestDatabase()

    private class SequentialIds : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    // One generator across both repositories, as Koin binds one `IdGenerator` singleton.
    private val ids = SequentialIds()

    private val sales = SaleRepositoryImpl(
        database = database,
        dao = database.saleDao(),
        ledger = database.stockLedgerDao(),
        ids = ids,
    )
    private val shifts = ShiftRepositoryImpl(
        database.shiftDao(),
        database.saleDao(),
        database.saleReturnDao(),
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

    private fun draft(
        id: String,
        shiftId: String?,
        total: Money,
        tenders: List<Payment>,
        occurredAt: Long = 1_757_000_000_000,
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
        tendered = tenders.fold(Money.ZERO) { sum, it -> sum + it.tendered },
        change = tenders.fold(Money.ZERO) { sum, it -> sum + it.tendered } - total,
        occurredAt = occurredAt,
        lines = listOf(
            SaleLine(
                id = "$id-line-1",
                saleId = id,
                lineNumber = 1,
                variantId = VARIANT_TEE_NAVY,
                description = "Round-neck t-shirt — Navy",
                descriptionAr = "تيشيرت — كحلي",
                quantity = 1,
                unitPrice = total,
                lineDiscount = Money.ZERO,
                orderDiscount = Money.ZERO,
                lineTotal = total,
                tax = Money.ZERO,
                unitCost = Money.ofPounds(120),
            ),
        ),
        payments = tenders,
    )

    private fun cash(saleId: String, amount: Money, handedOver: Money = amount) = Payment(
        id = "$saleId-cash",
        saleId = saleId,
        method = TenderMethod.CASH,
        amount = amount,
        tendered = handedOver,
        reference = null,
        occurredAt = 1_757_000_000_000,
    )

    private fun card(saleId: String, amount: Money) = Payment(
        id = "$saleId-card",
        saleId = saleId,
        method = TenderMethod.CARD,
        amount = amount,
        tendered = amount,
        reference = "APPROVED",
        occurredAt = 1_757_000_000_000,
    )

    private suspend fun seed() {
        database.seedBaseData()
        database.seedPriceList()
        database.stockLedgerDao().record(receipt(quantity = 50))
    }

    @Test
    fun `expected cash is the float plus what cash actually settled`() = runBlocking {
        seed()
        val shift = shifts.open("shift-1", SHOP_ID, "user-1", Money.ofPounds(500), 1_757_000_000_000)
            .getOrThrow()

        // Over-tendered: 200 handed over against 180, so 20 comes back out of the drawer.
        sales.record(
            draft("sale-1", shift.id, Money.ofPounds(180), listOf(cash("sale-1", Money.ofPounds(180), Money.ofPounds(200)))),
        ).getOrThrow()
        // Split tender: half cash, half card.
        sales.record(
            draft(
                "sale-2",
                shift.id,
                Money.ofPounds(300),
                listOf(cash("sale-2", Money.ofPounds(100)), card("sale-2", Money.ofPounds(200))),
            ),
        ).getOrThrow()

        val report = shifts.close("shift-1", "user-1", Money.ofPounds(780), null, 1_757_000_900_000)
            .getOrThrow()

        assertEquals(2, report.saleCount)
        assertEquals(Money.ofPounds(480), report.netSales)
        assertEquals(Money.ofPounds(280), report.cashTaken)
        assertEquals(Money.ofPounds(200), report.cardTaken)
        assertEquals(Money.ofPounds(20), report.changeGiven)
        // 500 float + 280 cash. Change is already off the settled amount and must not come off again.
        assertEquals(Money.ofPounds(780), report.expectedCash)
        assertEquals(Money.ZERO, report.difference)
    }

    @Test
    fun `a short drawer is reported, not hidden`() = runBlocking {
        seed()
        shifts.open("shift-1", SHOP_ID, "user-1", Money.ofPounds(500), 1_757_000_000_000).getOrThrow()
        sales.record(
            draft("sale-1", "shift-1", Money.ofPounds(180), listOf(cash("sale-1", Money.ofPounds(180)))),
        ).getOrThrow()

        val report = shifts.close("shift-1", "user-1", Money.ofPounds(650), "one note missing", 1_757_000_900_000)
            .getOrThrow()

        assertEquals(Money.ofPounds(680), report.expectedCash)
        assertEquals(Money.ofPounds(-30), report.difference)
    }

    @Test
    fun `a voided sale leaves the shift's takings`() = runBlocking {
        seed()
        shifts.open("shift-1", SHOP_ID, "user-1", Money.ofPounds(500), 1_757_000_000_000).getOrThrow()
        sales.record(
            draft("sale-1", "shift-1", Money.ofPounds(180), listOf(cash("sale-1", Money.ofPounds(180)))),
        ).getOrThrow()
        sales.record(
            draft("sale-2", "shift-1", Money.ofPounds(220), listOf(cash("sale-2", Money.ofPounds(220)))),
        ).getOrThrow()
        sales.void("sale-2", "admin-1", "wrong item", 1_757_000_500_000).getOrThrow()

        val report = shifts.close("shift-1", "user-1", Money.ofPounds(680), null, 1_757_000_900_000)
            .getOrThrow()

        assertEquals(1, report.saleCount)
        assertEquals(1, report.voidedCount)
        assertEquals(Money.ofPounds(180), report.cashTaken)
        assertEquals(Money.ofPounds(680), report.expectedCash)
        assertEquals(Money.ZERO, report.difference)
    }

    @Test
    fun `sales rung up outside a shift are counted and visible`() = runBlocking {
        seed()
        shifts.open("shift-1", SHOP_ID, "user-1", Money.ofPounds(500), 1_757_000_000_000).getOrThrow()
        sales.record(
            draft("sale-1", null, Money.ofPounds(180), listOf(cash("sale-1", Money.ofPounds(180)))),
        ).getOrThrow()

        val report = shifts.close("shift-1", "user-1", Money.ofPounds(500), null, 1_757_000_900_000)
            .getOrThrow()

        assertEquals(0, report.saleCount)
        assertEquals(1, report.salesOutsideShift)
        // The sale happened; it simply is not this shift's cash to account for.
        assertEquals(Money.ofPounds(500), report.expectedCash)
    }

    @Test
    fun `only one shift can be open at a location`() = runBlocking {
        seed()
        shifts.open("shift-1", SHOP_ID, "user-1", Money.ofPounds(500), 1_757_000_000_000).getOrThrow()

        assertTrue(
            shifts.open("shift-2", SHOP_ID, "user-2", Money.ofPounds(300), 1_757_000_100_000).isFailure,
        )
    }

    @Test
    fun `a cash refund comes back out of the drawer`() = runBlocking {
        seed()
        shifts.open("shift-1", SHOP_ID, "usr-1", Money.ofPounds(500), 1_757_000_000_000).getOrThrow()
        sales.record(
            draft("sale-1", "shift-1", Money.ofPounds(180), listOf(cash("sale-1", Money.ofPounds(180)))),
        ).getOrThrow()

        returns.record(
            ReturnDraft(
                id = "ret-1",
                originalSaleId = "sale-1",
                locationId = SHOP_ID,
                userId = "usr-1",
                shiftId = "shift-1",
                reason = "wrong size",
                refundMethod = TenderMethod.CASH,
                refundAmount = Money.ofPounds(180),
                subtotal = Money.ofPounds(180),
                tax = Money.ZERO,
                occurredAt = 1_757_000_500_000,
                authorisedByUserId = null,
                lines = listOf(
                    SaleReturnLine(
                        id = "ret-1-line-1",
                        returnId = "ret-1",
                        lineNumber = 1,
                        saleLineId = "sale-1-line-1",
                        variantId = VARIANT_TEE_NAVY,
                        description = "Round-neck t-shirt — Navy",
                        quantity = 1,
                        unitRefund = Money.ofPounds(180),
                        lineRefund = Money.ofPounds(180),
                        condition = ReturnCondition.SELLABLE,
                        unitCost = Money.ofPounds(120),
                    ),
                ),
            ),
        ).getOrThrow()

        val report = shifts.close("shift-1", "usr-1", Money.ofPounds(500), null, 1_757_000_900_000)
            .getOrThrow()

        assertEquals(1, report.returnCount)
        assertEquals(Money.ofPounds(180), report.cashRefunded)
        // Sold 180 and gave it straight back: the drawer holds the float and nothing else. A till
        // that reads over after a refund is a till nobody trusts.
        assertEquals(Money.ofPounds(500), report.expectedCash)
        assertEquals(Money.ZERO, report.difference)
    }

    @Test
    fun `a card refund leaves the drawer alone`() = runBlocking {
        seed()
        shifts.open("shift-1", SHOP_ID, "usr-1", Money.ofPounds(500), 1_757_000_000_000).getOrThrow()
        sales.record(
            draft("sale-1", "shift-1", Money.ofPounds(180), listOf(cash("sale-1", Money.ofPounds(180)))),
        ).getOrThrow()

        returns.record(
            ReturnDraft(
                id = "ret-1",
                originalSaleId = "sale-1",
                locationId = SHOP_ID,
                userId = "usr-1",
                shiftId = "shift-1",
                reason = "faulty",
                refundMethod = TenderMethod.CARD,
                refundAmount = Money.ofPounds(180),
                subtotal = Money.ofPounds(180),
                tax = Money.ZERO,
                occurredAt = 1_757_000_500_000,
                authorisedByUserId = null,
                lines = listOf(
                    SaleReturnLine(
                        id = "ret-1-line-1",
                        returnId = "ret-1",
                        lineNumber = 1,
                        saleLineId = "sale-1-line-1",
                        variantId = VARIANT_TEE_NAVY,
                        description = "Round-neck t-shirt — Navy",
                        quantity = 1,
                        unitRefund = Money.ofPounds(180),
                        lineRefund = Money.ofPounds(180),
                        condition = ReturnCondition.SELLABLE,
                        unitCost = Money.ofPounds(120),
                    ),
                ),
            ),
        ).getOrThrow()

        val report = shifts.close("shift-1", "usr-1", Money.ofPounds(680), null, 1_757_000_900_000)
            .getOrThrow()

        assertEquals(Money.ofPounds(180), report.cardRefunded)
        assertEquals(Money.ZERO, report.cashRefunded)
        assertEquals(Money.ofPounds(680), report.expectedCash)
    }

    @Test
    fun `the expected figure is frozen at close`() = runBlocking {
        seed()
        shifts.open("shift-1", SHOP_ID, "user-1", Money.ofPounds(500), 1_757_000_000_000).getOrThrow()
        sales.record(
            draft("sale-1", "shift-1", Money.ofPounds(180), listOf(cash("sale-1", Money.ofPounds(180)))),
        ).getOrThrow()
        shifts.close("shift-1", "user-1", Money.ofPounds(680), null, 1_757_000_900_000).getOrThrow()

        // A late correction lands against the closed shift.
        sales.record(
            draft("sale-2", "shift-1", Money.ofPounds(90), listOf(cash("sale-2", Money.ofPounds(90)))),
        ).getOrThrow()

        val reread = shifts.report("shift-1").getOrThrow()!!
        // Tonight's reconciliation still reads as it did tonight: a report that rewrites its own
        // history is one nobody can act on.
        assertEquals(Money.ofPounds(680), reread.expectedCash)
    }
}
