package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.database.entities.AssortmentPackEntity
import com.alsoug.keswa.core.database.entities.AssortmentPackLineEntity
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.SaleEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.entities.VariantEntity
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.SaleStatus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Two devices, and whether they end up agreeing.
 *
 * This is the phase. Everything else is plumbing around these answers.
 */
class SyncMergeTest {

    private val till = SyncPeer("device-till")
    private val handheld = SyncPeer("device-handheld")
    private val hub = SyncHub()

    @AfterTest
    fun tearDown() {
        till.close()
        handheld.close()
    }

    private suspend fun bothSeeded() {
        till.database.seedBaseData()
        settle(hub, till, handheld)
    }

    private fun movement(id: String, quantity: Int, at: Long) = StockMovementEntity(
        id = id,
        variantId = VARIANT_TEE_NAVY,
        locationId = SHOP_ID,
        quantity = quantity,
        reason = if (quantity > 0) MovementReason.RECEIPT else MovementReason.SALE,
        refType = null,
        refId = null,
        occurredAt = at,
        userId = "user-1",
    )

    @Test
    fun `the catalogue reaches the second device`() = runBlocking {
        bothSeeded()

        assertEquals("KSW-TSH-022-NV", handheld.database.variantDao().getById(VARIANT_TEE_NAVY)?.sku)
    }

    @Test
    fun `pushing the same batch twice writes one row, not two`() = runBlocking {
        bothSeeded()
        till.database.stockLedgerDao().insertMovement(movement("mov-1", 24, 1))
        hub.collectFrom(till)

        // Delivered, then delivered again from the start: exactly what a retry after a dropped
        // connection looks like.
        hub.deliverTo(handheld)
        handheld.advanceTo(0)
        hub.deliverTo(handheld)

        assertEquals(24, handheld.database.stockLedgerDao().sumQuantity(VARIANT_TEE_NAVY, SHOP_ID))
    }

    @Test
    fun `movements from two devices merge to the same figure whichever order they arrive`() = runBlocking {
        bothSeeded()
        till.database.stockLedgerDao().insertMovement(movement("mov-till", 24, 10))
        handheld.database.stockLedgerDao().insertMovement(movement("mov-handheld", -4, 20))

        settle(hub, till, handheld)

        // Appends commute. This is the property Phase 1 bought four phases before it was needed.
        assertEquals(20, till.database.stockLedgerDao().sumQuantity(VARIANT_TEE_NAVY, SHOP_ID))
        assertEquals(20, handheld.database.stockLedgerDao().sumQuantity(VARIANT_TEE_NAVY, SHOP_ID))
    }

    @Test
    fun `a row whose parent has not arrived waits instead of being dropped`() = runBlocking {
        bothSeeded()
        till.database.stockLedgerDao().insertMovement(movement("mov-orphan", 5, 30))
        hub.collectFrom(till)

        // Deliver the movement to a device that has never heard of the variant it names.
        val stranger = SyncPeer("device-stranger")
        try {
            val result = stranger.apply(hub.rows().filter { it.row.table == "stock_movement" })
            assertEquals(0, result.applied)
            assertEquals(1, result.deferred.size)

            // The catalogue turns up, and so does the movement.
            stranger.apply(hub.rows())
            assertEquals(5, stranger.database.stockLedgerDao().sumQuantity(VARIANT_TEE_NAVY, SHOP_ID))
        } finally {
            stranger.close()
        }
    }

    @Test
    fun `the cursor stops at a deferred row rather than stepping over it`() = runBlocking {
        bothSeeded()
        val stranger = SyncPeer("device-stranger")
        try {
            till.database.stockLedgerDao().insertMovement(movement("mov-1", 5, 30))
            hub.collectFrom(till)

            // Only the movement is offered; its variant is not in this window.
            val orphan = hub.rows().filter { it.row.table == "stock_movement" }
            val result = stranger.apply(orphan)
            stranger.advanceTo(result.deferred.minOf { it.seq - 1 })

            assertTrue(stranger.cursor < orphan.single().seq, "the cursor must not pass what it could not apply")
        } finally {
            stranger.close()
        }
    }

    @Test
    fun `an edit converges, and what it replaced is kept`() = runBlocking {
        bothSeeded()
        handheld.database.colourDao().upsert(ColourEntity(COLOUR_NAVY, "Ink", "حبر", "#0b1220", 0, isActive = true))

        settle(hub, handheld, till)

        assertEquals("Ink", till.database.colourDao().getById(COLOUR_NAVY)?.name)
        val record = till.database.syncDao().superseded().single { it.rowId == COLOUR_NAVY }
        assertTrue("Navy" in record.previousJson, "the replaced value is what a shop would ask about")
    }

    @Test
    fun `a local edit is not clobbered by an older one still in flight`() = runBlocking {
        bothSeeded()

        // The handheld renames it and the change reaches the log.
        handheld.database.colourDao().upsert(ColourEntity(COLOUR_NAVY, "Ink", "حبر", "#0b1220", 0, isActive = true))
        hub.collectFrom(handheld)

        // The till renames it too, and has not pushed yet when the handheld's version arrives.
        till.database.colourDao().upsert(ColourEntity(COLOUR_NAVY, "Slate", "أردوازي", "#2a3441", 0, isActive = true))
        hub.deliverTo(till)

        // The person at the till watched their change survive, and it is what gets pushed.
        assertEquals("Slate", till.database.colourDao().getById(COLOUR_NAVY)?.name)

        settle(hub, till, handheld)
        assertEquals("Slate", handheld.database.colourDao().getById(COLOUR_NAVY)?.name)
    }

    @Test
    fun `a line dropped from a pack is dropped on the other device too`() = runBlocking {
        bothSeeded()
        till.database.assortmentPackDao().upsert(
            AssortmentPackEntity("pack-1", "Carton", "كرتونة", 100_000, isActive = true),
        )
        till.database.assortmentPackDao().upsertLine(
            AssortmentPackLineEntity("packline-1", "pack-1", VARIANT_TEE_NAVY, 20),
        )
        settle(hub, till, handheld)
        assertEquals(1, handheld.database.assortmentPackDao().getLines("pack-1").size)

        till.database.assortmentPackDao().deleteLine("packline-1")
        settle(hub, till, handheld)

        // The only deletion in the schema that has to travel, and the reason records get a delete
        // trigger: a carton whose composition differs by device is an argument waiting to happen.
        assertEquals(emptyList(), handheld.database.assortmentPackDao().getLines("pack-1"))
    }

    private fun sale(id: String, number: Long) = SaleEntity(
        id = id,
        receiptNumber = number,
        locationId = SHOP_ID,
        priceListId = PRICE_LIST_ID,
        userId = "user-1",
        shiftId = null,
        customerId = null,
        status = SaleStatus.COMPLETED,
        subtotalPiastres = 18_000,
        discountPiastres = 0,
        taxPiastres = 0,
        totalPiastres = 18_000,
        tenderedPiastres = 20_000,
        changePiastres = 2_000,
        occurredAt = 1_757_000_500_000,
        voidedAt = null,
        voidedByUserId = null,
        voidReason = null,
    )

    @Test
    fun `a void reaches the other device, and reaching it twice changes nothing`() = runBlocking {
        bothSeeded()
        till.database.seedPriceList()
        till.database.saleDao().insert(sale("sale-1", 1))
        settle(hub, till, handheld)

        till.database.saleDao().markVoided("sale-1", 1_757_000_900_000, "user-1", "wrong size")
        settle(hub, till, handheld)
        settle(hub, till, handheld)

        val voided = assertNotNull(handheld.database.saleDao().getById("sale-1"))
        assertEquals(SaleStatus.VOIDED, voided.status)
        assertEquals("wrong size", voided.voidReason)
    }

    @Test
    fun `a void that arrives before the sale it voids still ends up voided`() = runBlocking {
        bothSeeded()
        till.database.seedPriceList()
        till.database.saleDao().insert(sale("sale-2", 2))
        till.database.saleDao().markVoided("sale-2", 1_757_000_900_000, "user-1", "changed mind")
        hub.collectFrom(till)

        // The log holds the sale twice: as completed, then as voided. Deliver them backwards,
        // which is what a batch boundary or a reordered retry looks like.
        val rows = hub.rows().filter { it.row.table == "sale" }
        handheld.apply(rows.reversed())
        handheld.apply(hub.rows())

        // Terminal beats non-terminal, so the order it arrived in does not decide the outcome.
        assertEquals(SaleStatus.VOIDED, handheld.database.saleDao().getById("sale-2")?.status)
    }

    @Test
    fun `two devices inventing the same sku settle on one of them, and say so`() = runBlocking {
        bothSeeded()
        val shared = "KSW-TSH-099-RD"
        till.database.colourDao().upsert(ColourEntity("col-red", "Red", "أحمر", "#b3261e", 1, isActive = true))
        settle(hub, till, handheld)

        till.database.variantDao().insert(
            VariantEntity("var-aaa", PRODUCT_TEE, "col-red", shared, 12_000, true, 0, 0),
        )
        handheld.database.variantDao().insert(
            VariantEntity("var-zzz", PRODUCT_TEE, "col-red", shared, 12_000, true, 0, 0),
        )

        settle(hub, till, handheld)

        // Deterministic and symmetric: the smaller id wins, so neither device has to ask the other.
        assertEquals("var-aaa", till.database.variantDao().getBySku(shared)?.id)
        assertEquals("var-aaa", handheld.database.variantDao().getBySku(shared)?.id)
        assertTrue(handheld.database.syncDao().superseded().any { it.rowId == "var-zzz" })
    }
}
