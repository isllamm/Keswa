package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.database.entities.SaleEntity
import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.sync.SyncSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 9i — two tills, and the receipt number that would otherwise take the second one down on its
 * first morning.
 *
 * `nextReceiptNumber` was `MAX + 1` over the whole table, so two tills selling at the same moment
 * both reach 413 and the unique index refuses one of them on the first sync. Phase 5 left a note
 * on `SaleEntity` saying exactly this would happen.
 */
class ReceiptBlockTest {

    private val till = SyncPeer("device-till")
    private val handheld = SyncPeer("device-handheld")
    private val hub = SyncHub()

    @AfterTest
    fun tearDown() {
        till.close()
        handheld.close()
    }

    private fun settingsFor(peer: SyncPeer) =
        SyncSettings(peer.database.settingDao(), UuidIdGenerator())

    private suspend fun ring(peer: SyncPeer, id: String): Long {
        val block = settingsFor(peer).receiptBlock()
        val number = peer.database.saleDao().nextReceiptNumber(block.first, block.last)
        peer.database.saleDao().insert(
            SaleEntity(
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
                tenderedPiastres = 18_000,
                changePiastres = 0,
                occurredAt = 1_757_000_500_000,
                voidedAt = null,
                voidedByUserId = null,
                voidReason = null,
            ),
        )
        return number
    }

    private suspend fun seedBoth() {
        till.database.seedBaseData()
        till.database.seedPriceList()
        settle(hub, till, handheld)
    }

    @Test
    fun `a shop with one till numbers from one, exactly as it always did`() = runBlocking {
        seedBoth()

        assertEquals(1L, ring(till, "sale-1"))
        assertEquals(2L, ring(till, "sale-2"))
    }

    @Test
    fun `two tills selling at the same moment cannot collide`() = runBlocking {
        seedBoth()
        settingsFor(handheld).setOrdinal(1)

        val first = ring(till, "sale-till-1")
        val second = ring(handheld, "sale-handheld-1")

        assertEquals(1L, first)
        assertEquals(1_000_001L, second)

        // And the unique index, which Phase 5 put there to make this loud, is never reached.
        settle(hub, till, handheld)
        assertNotNull(handheld.database.saleDao().getByReceiptNumber(1L))
        assertNotNull(handheld.database.saleDao().getByReceiptNumber(1_000_001L))
        Unit
    }

    @Test
    fun `each till's own run stays contiguous`() = runBlocking {
        seedBoth()
        settingsFor(handheld).setOrdinal(1)

        ring(till, "sale-till-1")
        ring(handheld, "sale-handheld-1")
        settle(hub, till, handheld)

        // The till's next number comes from its own block, not from whatever the handheld reached.
        assertEquals(2L, ring(till, "sale-till-2"))
        assertEquals(1_000_002L, ring(handheld, "sale-handheld-2"))
    }

    @Test
    fun `a shop already trading keeps its numbering when it enrols`() = runBlocking {
        seedBoth()
        repeat(412) { index -> ring(till, "sale-$index") }

        // The first device to enrol takes ordinal 0, so nothing jumps.
        settingsFor(till).setOrdinal(0)

        assertTrue(settingsFor(till).isEnrolled())
        assertEquals(413L, ring(till, "sale-after-enrolment"))
    }
}
