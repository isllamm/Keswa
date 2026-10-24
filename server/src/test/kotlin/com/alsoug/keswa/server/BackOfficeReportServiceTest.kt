package com.alsoug.keswa.server

import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.sync.SyncRow
import com.alsoug.keswa.core.sync.SyncValue
import com.alsoug.keswa.core.sync.SyncValueType
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BackOfficeReportServiceTest {

    private val fixture = temporaryLog()
    private val log = fixture.first
    private val directory: File = fixture.second
    private val shop: KeswaDatabase = inMemoryShop()

    @AfterTest
    fun tearDown() {
        shop.close()
        log.close()
        directory.deleteRecursively()
    }

    private fun text(value: String) = SyncValue(SyncValueType.TEXT, value)
    private fun integer(value: Long) = SyncValue(SyncValueType.INTEGER, value.toString())
    private val absent = SyncValue(SyncValueType.NULL)

    @Test
    fun `getSummary on fresh empty database returns zeroed metrics and empty collections`() = runTest {
        val service = BackOfficeReportService(shop, log) { CLOCK }

        val summary = service.getSummary(days = 7)

        assertEquals(7, summary.periodDays)
        assertEquals(0.0, summary.headline.revenuePounds)
        assertEquals(0.0, summary.headline.refundedPounds)
        assertEquals(0.0, summary.headline.netRevenuePounds)
        assertEquals(0, summary.headline.transactions)
        assertEquals(0, summary.headline.unitsSold)
        assertEquals(0, summary.headline.unitsReturned)
        assertEquals(0.0, summary.headline.cogsPounds)
        assertNull(summary.headline.grossMarginPercent)
        assertEquals(0.0, summary.headline.averageBasketPounds)
        assertTrue(summary.dailyRevenue.isEmpty())
        assertTrue(summary.colourPerformance.isEmpty())
        assertTrue(summary.topMovers.isEmpty())
    }

    @Test
    fun `getSummary clamps period days between 1 and 365`() = runTest {
        val service = BackOfficeReportService(shop, log) { CLOCK }

        val clampedLow = service.getSummary(days = 0)
        assertEquals(1, clampedLow.periodDays)

        val clampedNegative = service.getSummary(days = -15)
        assertEquals(1, clampedNegative.periodDays)

        val clampedHigh = service.getSummary(days = 999)
        assertEquals(365, clampedHigh.periodDays)
    }

    @Test
    fun `getSummary computes revenue, cogs, margin and average basket accurately when sales exist`() = runTest {
        val materialiser = materialiserFor(log, shop)

        // Seed catalogue + stock
        log.append("dev-1", catalogueRows() + listOf(movement("mov-1", 50, "var-1")), CLOCK)

        // Add sale: 2 units @ 200 EGP (400 EGP total), cost 120 EGP each (240 EGP total)
        val saleRow = SyncRow(
            "sale", "sale-1",
            mapOf(
                "id" to text("sale-1"),
                "receiptNumber" to integer(1),
                "locationId" to text("loc-1"),
                "priceListId" to text("pl-1"),
                "userId" to text("usr-1"),
                "shiftId" to absent,
                "customerId" to absent,
                "status" to text("COMPLETED"),
                "subtotalPiastres" to integer(40_000),
                "discountPiastres" to integer(0),
                "taxPiastres" to integer(0),
                "totalPiastres" to integer(40_000),
                "tenderedPiastres" to integer(40_000),
                "changePiastres" to integer(0),
                "occurredAt" to integer(CLOCK - 1_000L),
                "voidedAt" to absent,
                "voidedByUserId" to absent,
                "voidReason" to absent,
            ),
        )
        val saleLineRow = SyncRow(
            "sale_line", "line-1",
            mapOf(
                "id" to text("line-1"),
                "saleId" to text("sale-1"),
                "lineNumber" to integer(1),
                "variantId" to text("var-1"),
                "description" to text("Round-neck t-shirt - Navy"),
                "descriptionAr" to text("تيشيرت - كحلي"),
                "quantity" to integer(2),
                "unitPricePiastres" to integer(20_000),
                "lineDiscountPiastres" to integer(0),
                "orderDiscountPiastres" to integer(0),
                "lineTotalPiastres" to integer(40_000),
                "taxPiastres" to integer(0),
                "unitCostPiastres" to integer(12_000),
                "authorisedByUserId" to absent,
                "packId" to absent,
            ),
        )
        log.append("dev-1", listOf(saleRow, saleLineRow), CLOCK)
        materialiser.runToCompletion()

        val service = BackOfficeReportService(shop, log) { CLOCK }
        val summary = service.getSummary(days = 7)

        assertEquals(400.0, summary.headline.revenuePounds, 0.01)
        assertEquals(0.0, summary.headline.refundedPounds, 0.01)
        assertEquals(400.0, summary.headline.netRevenuePounds, 0.01)
        assertEquals(1, summary.headline.transactions)
        assertEquals(2, summary.headline.unitsSold)
        assertEquals(240.0, summary.headline.cogsPounds!!, 0.01)
        assertEquals(40.0, summary.headline.grossMarginPercent!!, 0.01) // (400 - 240) / 400 = 40%
        assertEquals(400.0, summary.headline.averageBasketPounds, 0.01)
    }

    @Test
    fun `getValuation calculates total pieces and inventory value at cost for positive on hand stock`() = runTest {
        val materialiser = materialiserFor(log, shop)
        log.append(
            "dev-1",
            catalogueRows() + listOf(
                movement("mov-rec", 25, "var-1"), // 25 pieces @ 120 EGP cost = 3000 EGP
            ),
            CLOCK,
        )
        materialiser.runToCompletion()

        val service = BackOfficeReportService(shop, log) { CLOCK }
        val valuation = service.getValuation()

        assertEquals(25, valuation.totalPiecesOnHand)
        assertEquals(3_000.0, valuation.totalCostValuePounds, 0.01)
        assertEquals(1, valuation.trackedSkusCount)
    }

    @Test
    fun `getDevices formats device list with receipt range blocks and revocation status`() = runTest {
        log.mintEnrolmentCode("CODE-1", CLOCK)
        log.mintEnrolmentCode("CODE-2", CLOCK)

        log.enrol("CODE-1", "dev-1", "Till North", CLOCK)
        log.enrol("CODE-2", "dev-2", "Till South", CLOCK)

        val service = BackOfficeReportService(shop, log) { CLOCK }
        val devicesBeforeRevocation = service.getDevices()

        assertEquals(2, devicesBeforeRevocation.size)
        val firstDevice = devicesBeforeRevocation.first { it.id == "dev-1" }
        val secondDevice = devicesBeforeRevocation.first { it.id == "dev-2" }

        assertEquals(0, firstDevice.ordinal)
        assertEquals("#0 – #999999", firstDevice.receiptRange)
        assertFalse(firstDevice.isRevoked)

        assertEquals(1, secondDevice.ordinal)
        assertEquals("#1000000 – #1999999", secondDevice.receiptRange)
        assertFalse(secondDevice.isRevoked)

        // Revoke dev-1
        log.revoke("dev-1", CLOCK + 5_000L)
        val devicesAfterRevocation = service.getDevices()
        val revokedDev = devicesAfterRevocation.first { it.id == "dev-1" }
        assertTrue(revokedDev.isRevoked)
        assertEquals(CLOCK + 5_000L, revokedDev.revokedAt)
    }
}
