package com.alsoug.keswa.core.database

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.RealDispatchers
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.domain.model.MovementReason
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Sync arrives against a database full of trading, and must be invisible to it.
 *
 * This migration adds no column to any existing table — the first since Phase 1 that does not —
 * because 9d keeps the outbox out of the ledger and 9i keeps receipt numbering out of `sale`. What
 * it does add is **triggers**, which Room does not model, so the thing this test is really for is
 * proving the upgrade path installs them. A shop that upgrades and silently syncs nothing looks
 * exactly like a shop that is working.
 */
class Migration7To8Test {

    private val directory: File = Files.createTempDirectory("keswa-migration-7-8").toFile()
    private val databaseFile = File(directory, "keswa.db")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun seedVersion7() {
        SchemaFixture.createDatabaseAtVersion(databaseFile, SchemaFixture.exportedSchema(7))
        SchemaFixture.execute(
            databaseFile,
            listOf(
                "INSERT INTO location (id,name,nameAr,type,isDefault,isActive) " +
                    "VALUES ('loc-1','Downtown','وسط البلد','SHOP',1,1)",
                "INSERT INTO category (id,parentId,name,nameAr,path,depth,sortOrder,isActive,createdAt,updatedAt) " +
                    "VALUES ('cat-1',NULL,'T-shirts','تيشيرتات','/cat-1/',0,0,1,0,0)",
                "INSERT INTO colour (id,name,nameAr,hex,sortOrder,isActive) " +
                    "VALUES ('col-1','Navy','كحلي','#20304f',0,1)",
                "INSERT INTO product (id,name,nameAr,categoryId,brandId,supplierId,season,isActive,createdAt,updatedAt) " +
                    "VALUES ('prod-1','Round-neck t-shirt','تيشيرت','cat-1',NULL,NULL,NULL,1,0,0)",
                "INSERT INTO variant (id,productId,colourId,sku,costPiastres,isActive,createdAt,updatedAt) " +
                    "VALUES ('var-1','prod-1','col-1','ROU-NAV',12000,1,0,0)",
                "INSERT INTO stock_movement (id,variantId,locationId,quantity,reason,refType,refId,occurredAt,userId) " +
                    "VALUES ('mov-1','var-1','loc-1',24,'RECEIPT',NULL,NULL,1757000000000,'usr-1')",
                "INSERT INTO stock_on_hand (variantId,locationId,quantity,lastMovementAt) " +
                    "VALUES ('var-1','loc-1',24,1757000000000)",
                "INSERT INTO price_list (id,name,nameAr,type,isDefault,isActive) " +
                    "VALUES ('pl-1','Retail','التجزئة','RETAIL',1,1)",
                "INSERT INTO price (id,priceListId,variantId,pricePiastres,validFrom,validTo) " +
                    "VALUES ('pr-1','pl-1','var-1',18000,0,NULL)",
                "INSERT INTO app_user (id,username,displayName,displayNameAr,role,secretHash,secretSalt," +
                    "secretKind,isActive,mustChangeSecret,failedAttempts,lockedUntil,createdAt,updatedAt) " +
                    "VALUES ('usr-1','owner','Owner','المالك','ADMIN','hash','0011','PASSWORD',1,0,0,NULL,0,0)",
                "INSERT INTO sale (id,receiptNumber,locationId,priceListId,userId,shiftId,customerId,status," +
                    "subtotalPiastres,discountPiastres,taxPiastres,totalPiastres,tenderedPiastres," +
                    "changePiastres,occurredAt,voidedAt,voidedByUserId,voidReason) " +
                    "VALUES ('sale-1',412,'loc-1','pl-1','usr-1',NULL,NULL,'COMPLETED',36000,0,0,36000,40000,4000," +
                    "1757000500000,NULL,NULL,NULL)",
                "INSERT INTO customer (id,name,nameAr,phone,taxId,priceListId,creditLimitPiastres," +
                    "paymentTermsDays,isActive,createdAt,updatedAt) " +
                    "VALUES ('cus-1','Nasr Textiles','نصر','0100','TX1','pl-1',500000,30,1,0,0)",
            ),
        )
    }

    private fun open(): KeswaDatabase = getKeswaDatabase(
        Room.databaseBuilder<KeswaDatabase>(name = databaseFile.absolutePath),
        RealDispatchers,
    )

    @Test
    fun `a shop's trading survives the arrival of sync`() = runBlocking {
        seedVersion7()

        val database = open()

        assertEquals(24, database.stockLedgerDao().sumQuantity("var-1", "loc-1"))
        assertEquals(24, database.stockLedgerDao().getOnHand("var-1", "loc-1")?.quantity)
        assertEquals(412L, assertNotNull(database.saleDao().getById("sale-1")).receiptNumber)
        assertEquals("Nasr Textiles", database.customerDao().getById("cus-1")?.name)
        assertNotNull(database.userDao().findByUsername("owner"))
        database.close()
    }

    @Test
    fun `the upgrade installs every trigger, not just the tables`() = runBlocking {
        seedVersion7()

        val database = open()

        val installed = database.triggerNames()
        SYNC_TRIGGER_NAMES.forEach { name ->
            assertTrue(name in installed, "$name is missing after the upgrade — this shop would sync nothing")
        }
        database.close()
    }

    @Test
    fun `nothing that was already here is queued to be sent`() = runBlocking {
        seedVersion7()

        val database = open()

        // The triggers fire on writes from now on. A migration that enqueued the shop's entire
        // history would make the first sync of an established shop a very long one, and this test
        // is what says that was a decision.
        assertEquals(emptyList(), database.syncDao().pending(limit = 10))
        database.close()
    }

    @Test
    fun `a sale made after the upgrade is queued`() = runBlocking {
        seedVersion7()

        val database = open()
        database.stockLedgerDao().insertMovement(
            StockMovementEntity(
                id = "mov-2",
                variantId = "var-1",
                locationId = "loc-1",
                quantity = -2,
                reason = MovementReason.SALE,
                refType = null,
                refId = null,
                occurredAt = 1_757_100_000_000,
                userId = "usr-1",
            ),
        )

        assertEquals(
            listOf("stock_movement" to "mov-2"),
            database.syncDao().pending(limit = 10).map { it.tableName to it.rowId },
        )
        database.close()
    }

    @Test
    fun `the first device carries on numbering where it left off`() = runBlocking {
        seedVersion7()

        val database = open()

        // Ordinal 0 until enrolment, and the first device to enrol keeps 0 — so a shop trading at
        // receipt 412 issues 413 next, not 1000001 (9i).
        val settings = com.alsoug.keswa.core.sync.SyncSettings(
            database.settingDao(),
            com.alsoug.keswa.core.domain.UuidIdGenerator(),
        )
        val block = settings.receiptBlock()
        assertEquals(413L, database.saleDao().nextReceiptNumber(block.first, block.last))
        database.close()
    }
}
