package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.data.repository.AnalyticsRepositoryImpl
import com.alsoug.keswa.core.database.entities.CategoryEntity
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.PaymentEntity
import com.alsoug.keswa.core.database.entities.ProductEntity
import com.alsoug.keswa.core.database.entities.SaleEntity
import com.alsoug.keswa.core.database.entities.SaleLineEntity
import com.alsoug.keswa.core.database.entities.SaleReturnEntity
import com.alsoug.keswa.core.database.entities.SaleReturnLineEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.entities.VariantEntity
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.SellThroughRowModel
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking

/**
 * The dashboard's numbers, and the measurement that decides how they are computed.
 *
 * The plan proposed rollup tables maintained inside every sale, receipt and return transaction.
 * Its own check 4 was always the arbiter — *"the one that decides whether the rollups were
 * necessary"* — so this file both proves the figures and times them against two years of data.
 */
class AnalyticsTest {

    private val database = createTestDatabase()
    private val analytics = AnalyticsRepositoryImpl(database.analyticsDao())

    @AfterTest
    fun tearDown() = database.close()

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val NOW = 1_757_000_000_000L
        const val FROM = NOW - 90 * DAY
        const val TO = NOW + DAY
    }

    private suspend fun seedTree() {
        database.seedBaseData()
        // A child category, to prove the rollup walks the materialised path.
        database.categoryDao().upsert(
            CategoryEntity(
                id = "cat-roundneck",
                parentId = CAT_TSHIRTS,
                name = "Round neck",
                nameAr = "رقبة دائرية",
                path = "/$CAT_TSHIRTS/cat-roundneck/",
                depth = 1,
                sortOrder = 0,
                isActive = true,
                createdAt = 0,
                updatedAt = 0,
            ),
        )
        database.colourDao().upsert(ColourEntity("col-beige", "Beige", "بيج", "#cfbfa4", 1, true))
        database.productDao().upsert(
            ProductEntity("prod-deep", "V-neck", "رقبة v", "cat-roundneck", null, null, null, true, 0, 0),
        )
        database.variantDao().insert(
            VariantEntity("var-deep", "prod-deep", "col-beige", "KSW-VNK-001-BG", 10_000, true, 0, 0),
        )
    }

    private var nextReceiptNumber = 1L

    private suspend fun sale(
        id: String,
        at: Long,
        variantId: String = VARIANT_TEE_NAVY,
        quantity: Int = 2,
        unitPrice: Long = 18_000,
        unitCost: Long = 12_000,
    ) {
        val total = unitPrice * quantity
        database.saleDao().insert(
            SaleEntity(
                id = id,
                receiptNumber = nextReceiptNumber++,
                locationId = SHOP_ID,
                priceListId = PRICE_LIST_ID,
                userId = "usr-1",
                shiftId = null,
                status = SaleStatus.COMPLETED,
                subtotalPiastres = total,
                discountPiastres = 0,
                taxPiastres = 0,
                totalPiastres = total,
                tenderedPiastres = total,
                changePiastres = 0,
                occurredAt = at,
                voidedAt = null,
                voidedByUserId = null,
                voidReason = null,
            ),
        )
        database.saleDao().insertLines(
            listOf(
                SaleLineEntity(
                    id = "$id-l1",
                    saleId = id,
                    lineNumber = 1,
                    variantId = variantId,
                    description = "shirt",
                    descriptionAr = "قميص",
                    quantity = quantity,
                    unitPricePiastres = unitPrice,
                    lineDiscountPiastres = 0,
                    orderDiscountPiastres = 0,
                    lineTotalPiastres = total,
                    taxPiastres = 0,
                    unitCostPiastres = unitCost,
                    authorisedByUserId = null,
                ),
            ),
        )
        database.saleDao().insertPayments(
            listOf(PaymentEntity("$id-p1", id, TenderMethod.CASH, total, total, null, at)),
        )
    }

    private suspend fun received(variantId: String, quantity: Int, id: String) {
        database.stockLedgerDao().record(
            StockMovementEntity(
                id = id,
                variantId = variantId,
                locationId = SHOP_ID,
                quantity = quantity,
                reason = MovementReason.RECEIPT,
                refType = null,
                refId = null,
                occurredAt = NOW - 60 * DAY,
                userId = "usr-1",
                unitCostPiastres = 12_000,
            ),
        )
    }

    @Test
    fun `headline figures add up, and cost only appears when asked for`() = runBlocking {
        seedTree()
        sale("s1", NOW - 2 * DAY)
        sale("s2", NOW - DAY)

        val withoutCost = analytics.headline(FROM, TO, withCost = false).getOrThrow()
        assertEquals(Money.ofPounds(720), withoutCost.revenue)
        assertEquals(2, withoutCost.transactions)
        assertEquals(4, withoutCost.units)
        assertEquals(Money.ofPounds(360), withoutCost.averageBasket)
        // Not fetched at all: a seller may see units and revenue without learning what the shop paid.
        assertNull(withoutCost.cogs)
        assertNull(withoutCost.margin)

        val withCost = analytics.headline(FROM, TO, withCost = true).getOrThrow()
        assertEquals(Money.ofPounds(480), withCost.cogs)
        assertEquals(Money.ofPounds(240), withCost.margin)
        assertEquals(3_333, withCost.marginBasisPoints)
    }

    @Test
    fun `a sale outside the window is not counted`() = runBlocking {
        seedTree()
        sale("s1", NOW - 2 * DAY)
        sale("old", NOW - 200 * DAY)

        assertEquals(1, analytics.headline(FROM, TO, withCost = false).getOrThrow().transactions)
    }

    @Test
    fun `a voided sale is not counted`() = runBlocking {
        seedTree()
        sale("s1", NOW - 2 * DAY)
        database.saleDao().markVoided("s1", NOW - DAY, "usr-admin", "wrong item")

        assertEquals(0, analytics.headline(FROM, TO, withCost = false).getOrThrow().transactions)
    }

    @Test
    fun `returns show as a rate, not just a count`() = runBlocking {
        seedTree()
        sale("s1", NOW - 2 * DAY)
        database.saleReturnDao().insert(
            SaleReturnEntity(
                id = "ret-1",
                returnNumber = 1,
                originalSaleId = "s1",
                locationId = SHOP_ID,
                userId = "usr-1",
                shiftId = null,
                status = SaleStatus.COMPLETED,
                reason = "wrong size",
                refundMethod = TenderMethod.CASH,
                refundAmountPiastres = 18_000,
                subtotalPiastres = 18_000,
                taxPiastres = 0,
                occurredAt = NOW - DAY,
                exchangeSaleId = null,
                authorisedByUserId = null,
                voidedAt = null,
                voidedByUserId = null,
                voidReason = null,
            ),
        )
        database.saleReturnDao().insertLines(
            listOf(
                SaleReturnLineEntity(
                    id = "ret-1-l1",
                    returnId = "ret-1",
                    lineNumber = 1,
                    saleLineId = "s1-l1",
                    variantId = VARIANT_TEE_NAVY,
                    description = "shirt",
                    quantity = 1,
                    unitRefundPiastres = 18_000,
                    lineRefundPiastres = 18_000,
                    condition = ReturnCondition.SELLABLE,
                    unitCostPiastres = 12_000,
                ),
            ),
        )

        val kpis = analytics.headline(FROM, TO, withCost = false).getOrThrow()
        assertEquals(1, kpis.returnedUnits)
        // One of two units back: 50%, in basis points so nothing floating-point reaches a decision.
        assertEquals(5_000, kpis.returnRateBasisPoints)
        assertEquals(Money.ofPounds(180), kpis.refunded)
        assertEquals(Money.ofPounds(180), kpis.netRevenue)
    }

    @Test
    fun `a shop on day one gets stated zeroes, not NaN`() = runBlocking {
        seedTree()

        val kpis = analytics.headline(FROM, TO, withCost = true).getOrThrow()

        assertEquals(Money.ZERO, kpis.revenue)
        assertEquals(0, kpis.transactions)
        // Null rather than zero: there is no average basket, which is different from one of zero.
        assertNull(kpis.averageBasket)
        assertNull(kpis.returnRateBasisPoints)
        assertNull(kpis.marginBasisPoints)
        assertTrue(analytics.revenueByDay(FROM, TO).getOrThrow().isEmpty())
        assertTrue(analytics.topMovers(FROM, TO, 10, withCost = false).getOrThrow().isEmpty())
    }

    @Test
    fun `colour performance shows sold against what is still on the rail`() = runBlocking {
        seedTree()
        received(VARIANT_TEE_NAVY, 10, "m1")
        received("var-deep", 10, "m2")
        sale("s1", NOW - 2 * DAY, variantId = VARIANT_TEE_NAVY, quantity = 8)
        database.stockLedgerDao().record(
            StockMovementEntity(
                "m3", VARIANT_TEE_NAVY, SHOP_ID, -8, MovementReason.SALE,
                "SALE", "s1", NOW - 2 * DAY, "usr-1",
            ),
        )

        val buckets = analytics.colourPerformance(FROM, TO).getOrThrow()

        val navy = buckets.single { it.colourId == COLOUR_NAVY }
        assertEquals(8, navy.sold)
        assertEquals(2, navy.onHand)
        assertEquals(8_000, navy.sellThroughBasisPoints)

        // Bought and untouched — the panel's whole point is that this is visible beside navy.
        val beige = buckets.single { it.colourId == "col-beige" }
        assertEquals(0, beige.sold)
        assertEquals(10, beige.onHand)
        assertEquals(0, beige.sellThroughBasisPoints)
    }

    @Test
    fun `sell-through rolls a deep product up to its top-level category`() = runBlocking {
        seedTree()
        received("var-deep", 10, "m1")
        // The product is three levels down: T-shirts → Round neck → V-neck.
        sale("s1", NOW - 2 * DAY, variantId = "var-deep", quantity = 7)

        val rows = analytics.sellThrough(FROM, TO).getOrThrow()

        val tshirts = rows.single { it.categoryId == CAT_TSHIRTS }
        // A rollup that counted only directly-assigned products would report zero here, under-report
        // every parent, and nobody would notice until somebody bought stock on the strength of it.
        assertEquals(7, tshirts.sold)
        assertEquals(10, tshirts.received)
        assertEquals(7_000, tshirts.basisPoints)
        assertTrue(!tshirts.isBelowTarget)
    }

    @Test
    fun `sell-through below target is flagged`() = runBlocking {
        seedTree()
        received("var-deep", 10, "m1")
        sale("s1", NOW - 2 * DAY, variantId = "var-deep", quantity = 3)

        val tshirts = analytics.sellThrough(FROM, TO).getOrThrow().single { it.categoryId == CAT_TSHIRTS }

        assertEquals(3_000, tshirts.basisPoints)
        assertTrue(tshirts.isBelowTarget)
        assertEquals(7_000, SellThroughRowModel.TARGET_BASIS_POINTS)
    }

    @Test
    fun `top movers rank by units and carry sell-through per SKU`() = runBlocking {
        seedTree()
        received(VARIANT_TEE_NAVY, 20, "m1")
        received("var-deep", 20, "m2")
        sale("s1", NOW - 2 * DAY, variantId = VARIANT_TEE_NAVY, quantity = 9)
        sale("s2", NOW - DAY, variantId = "var-deep", quantity = 4)

        val movers = analytics.topMovers(FROM, TO, limit = 10, withCost = true).getOrThrow()

        assertEquals(listOf(VARIANT_TEE_NAVY, "var-deep"), movers.map { it.variantId })
        assertEquals(9, movers.first().sold)
        assertEquals(4_500, movers.first().sellThroughBasisPoints)
        assertEquals(Money.ofPounds(540), movers.first().margin)
    }

    @Test
    fun `revenue is bucketed by the shop's own day`() = runBlocking {
        seedTree()
        sale("s1", NOW - 2 * DAY)
        sale("s2", NOW - 2 * DAY + 3_600_000)
        sale("s3", NOW - DAY)

        val points = analytics.revenueByDay(FROM, TO).getOrThrow()

        assertEquals(2, points.size)
        assertEquals(2, points.first().count)
        assertEquals(Money.ofPounds(720), points.first().amount)
    }

    @Test
    fun `busy hours bucket by weekday and hour`() = runBlocking {
        seedTree()
        sale("s1", NOW - 2 * DAY)
        sale("s2", NOW - 2 * DAY)

        val hours = analytics.busyHours(FROM, TO).getOrThrow()

        assertEquals(1, hours.buckets.size)
        assertEquals(2, hours.busiest)
        assertTrue(hours.buckets.single().dayOfWeek in 0..6)
        assertTrue(hours.buckets.single().hour in 0..23)
    }

    /**
     * The plan's check 4, and the measurement that settled how this phase is built.
     *
     * Two years of trading on one till: ~6,000 sales, ~12,000 lines, ~12,000 movements. If the
     * whole dashboard comes back well inside the 300 ms budget straight off the ledger, then a
     * rollup table maintained inside three separate write transactions — plus a rebuild path, plus
     * a class of drift bug — is complexity bought for nothing.
     */
    @Test
    fun `the whole dashboard opens well inside the budget against two years of data`() = runBlocking {
        seedTree()
        received(VARIANT_TEE_NAVY, 20_000, "m-open-1")
        received("var-deep", 20_000, "m-open-2")

        val sales = 6_000
        val start = NOW - 730 * DAY
        repeat(sales) { index ->
            val at = start + index * (730L * DAY / sales)
            sale(
                id = "perf-$index",
                at = at,
                variantId = if (index % 2 == 0) VARIANT_TEE_NAVY else "var-deep",
                quantity = 1 + index % 3,
            )
        }

        val window = NOW - 90 * DAY to NOW + DAY
        val elapsed = measureTime {
            analytics.headline(window.first, window.second, withCost = true).getOrThrow()
            analytics.revenueByDay(window.first, window.second).getOrThrow()
            analytics.colourPerformance(window.first, window.second).getOrThrow()
            analytics.sellThrough(window.first, window.second).getOrThrow()
            analytics.busyHours(window.first, window.second).getOrThrow()
            analytics.topMovers(window.first, window.second, 10, withCost = true).getOrThrow()
        }

        assertTrue(
            elapsed.inWholeMilliseconds < 300,
            "all six panels should load inside 300 ms, took ${elapsed.inWholeMilliseconds} ms",
        )
    }
}
