package com.alsoug.keswa.server

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The log is the durable thing; the shop is derived from it.
 *
 * That is the whole of 9c's exit path — the day this outgrows SQLite, the migration is to replay
 * the log somewhere else — so it is worth a test rather than a promise.
 */
class MaterialiserTest {

    private val fixture = temporaryLog()
    private val log = fixture.first
    private val directory: File = fixture.second
    private val shop = inMemoryShop()
    private val materialiser = materialiserFor(log, shop)

    @AfterTest
    fun tearDown() {
        shop.close()
        log.close()
        directory.deleteRecursively()
    }

    @Test
    fun `a push becomes a shop`() = runBlocking {
        log.append("device-till", catalogueRows() + movement("mov-1", 24), CLOCK)

        materialiser.runToCompletion()

        assertEquals("ROU-NAV", shop.variantDao().getById("var-1")?.sku)
        assertEquals(24, shop.stockLedgerDao().sumQuantity("var-1", "loc-1"))
    }

    @Test
    fun `a row whose parent is still in flight waits, and the marker waits with it`() = runBlocking {
        log.append("device-till", catalogueRows(), CLOCK)
        materialiser.runToCompletion()
        val settled = log.materialisedThrough()

        log.append("device-till", listOf(orphanMovement()), CLOCK)
        val result = materialiser.run()

        assertEquals(0, result.applied)
        assertEquals(1, result.deferred)
        assertEquals(settled, log.materialisedThrough(), "the marker must not pass what it could not build")
    }

    @Test
    fun `and it lands as soon as the rest arrives`() = runBlocking {
        log.append("device-till", catalogueRows(), CLOCK)
        log.append("device-till", listOf(orphanMovement()), CLOCK)
        materialiser.runToCompletion()
        assertEquals(0, shop.stockLedgerDao().sumQuantity("var-2", "loc-1"))

        log.append("device-till", lateCatalogueRows(), CLOCK)
        materialiser.runToCompletion()

        // Nothing had to know that a movement belongs to a variant. The row was retried because it
        // failed, and it stopped failing.
        assertEquals(3, shop.stockLedgerDao().sumQuantity("var-2", "loc-1"))
    }

    @Test
    fun `the shop can be thrown away and rebuilt from the log`() = runBlocking {
        log.append("device-till", catalogueRows() + movement("mov-1", 24) + movement("mov-2", -4), CLOCK)
        materialiser.runToCompletion()
        val before = shop.stockLedgerDao().sumQuantity("var-1", "loc-1")

        // A fresh, empty shop and the same log.
        val rebuilt = inMemoryShop()
        try {
            log.setMaterialisedThrough(0)
            materialiserFor(log, rebuilt).runToCompletion()

            assertEquals(before, rebuilt.stockLedgerDao().sumQuantity("var-1", "loc-1"))
            assertEquals("ROU-NAV", rebuilt.variantDao().getById("var-1")?.sku)
            assertTrue(before == 20)
        } finally {
            rebuilt.close()
        }
    }
}
