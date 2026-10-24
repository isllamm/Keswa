package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.database.entities.AssortmentPackEntity
import com.alsoug.keswa.core.database.entities.AssortmentPackLineEntity
import com.alsoug.keswa.core.database.entities.HeldSaleEntity
import com.alsoug.keswa.core.sync.SYNC_TABLES
import com.alsoug.keswa.core.sync.SyncKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The triggers, which are the only reason any of this works without a repository knowing sync
 * exists.
 */
class SyncOutboxTest {

    private val database = createTestDatabase()

    @AfterTest
    fun tearDown() = database.close()

    private suspend fun outbox() = database.syncDao().pending(limit = 500)

    @Test
    fun `writing a record enqueues it`() = runBlocking {
        database.seedBaseData()

        val queued = outbox().map { it.tableName }.toSet()

        assertTrue("product" in queued)
        assertTrue("variant" in queued)
        assertTrue("category" in queued)
        assertTrue("colour" in queued)
        assertTrue("location" in queued)
    }

    @Test
    fun `editing the same row twice enqueues twice, and the push is what coalesces`() = runBlocking {
        database.seedBaseData()
        database.colourDao().upsert(
            com.alsoug.keswa.core.database.entities.ColourEntity(
                COLOUR_NAVY, "Midnight", "منتصف الليل", "#101828", 0, isActive = true,
            ),
        )
        database.colourDao().upsert(
            com.alsoug.keswa.core.database.entities.ColourEntity(
                COLOUR_NAVY, "Ink", "حبر", "#0b1220", 0, isActive = true,
            ),
        )

        // A unique index here would have been tidier, and does not work: SQLite discards a
        // trigger body's conflict algorithm in favour of the firing statement's, so `OR REPLACE`
        // became `ABORT` and posting a stock receipt failed. The duplicate entries are also what
        // lets an edit made *during* a push survive the drain — see SyncEngineTest.
        // One entry per write — the insert from seeding, then both edits.
        assertEquals(3, outbox().count { it.tableName == "colour" && it.rowId == COLOUR_NAVY })
    }

    @Test
    fun `a stock movement enqueues`() = runBlocking {
        database.seedBaseData()
        database.stockLedgerDao().insertMovement(receipt(quantity = 24))

        assertEquals(1, outbox().count { it.tableName == "stock_movement" })
    }

    @Test
    fun `a held sale never leaves this till`() = runBlocking {
        database.seedBaseData()
        database.heldSaleDao().insert(
            HeldSaleEntity(
                id = "held-1",
                label = "Blue shirt customer",
                locationId = SHOP_ID,
                userId = "user-1",
                heldAt = 1_757_000_000_000,
            ),
        )

        // Two tills resuming the same parked basket would sell the same garments twice.
        assertEquals(0, outbox().count { it.tableName == "held_sale" })
    }

    @Test
    fun `deleting a record enqueues it, which is the only tombstone there is`() = runBlocking {
        database.seedBaseData()
        database.assortmentPackDao().upsert(
            AssortmentPackEntity("pack-1", "Carton", "كرتونة", 100_000, isActive = true),
        )
        database.assortmentPackDao().upsertLine(
            AssortmentPackLineEntity("packline-1", "pack-1", VARIANT_TEE_NAVY, 20),
        )
        database.syncDao().drainThrough(Long.MAX_VALUE)

        database.assortmentPackDao().deleteLine("packline-1")

        assertEquals(
            1,
            outbox().count { it.tableName == "assortment_pack_line" && it.rowId == "packline-1" },
        )
    }

    @Test
    fun `nothing is enqueued while a pull is being applied`() = runBlocking {
        database.seedBaseData()
        database.syncDao().drainThrough(Long.MAX_VALUE)

        database.syncDao().beginApplying()
        database.stockLedgerDao().insertMovement(receipt(quantity = 5))
        database.syncDao().endApplying()

        // Otherwise a row arriving from the server is queued straight back at it, for ever.
        assertEquals(emptyList(), outbox())
    }

    @Test
    fun `every syncable table that should have a trigger has one`() = runBlocking {
        val installed = database.triggerNames()

        SYNC_TABLES.forEach { table ->
            SYNC_TRIGGER_NAMES.filter { it.startsWith("sync_out_${table.name}_") }.forEach { name ->
                assertTrue(name in installed, "$name is missing — ${table.name} would never sync")
            }
        }
        assertTrue(SYNC_TRIGGER_NAMES.isNotEmpty())
    }

    @Test
    fun `only records may be deleted, so only records have a delete trigger`() {
        val deleteTriggers = SYNC_TRIGGER_NAMES.filter { it.endsWith("_del") }
        val records = SYNC_TABLES.filter { it.kind == SyncKind.RECORD }.map { it.name }.toSet()

        deleteTriggers.forEach { name ->
            val table = name.removePrefix("sync_out_").removeSuffix("_del")
            assertTrue(table in records, "$table is not a record; nothing in the shop's history is deletable")
        }
    }
}
