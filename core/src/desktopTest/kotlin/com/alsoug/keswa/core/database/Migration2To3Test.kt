package com.alsoug.keswa.core.database

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.RealDispatchers
import com.alsoug.keswa.core.database.entities.AppSettingEntity
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/**
 * An upgrade must never put the shop's trading history at risk to introduce a login.
 */
class Migration2To3Test {

    private val directory: File = Files.createTempDirectory("keswa-migration-2-3").toFile()
    private val databaseFile = File(directory, "keswa.db")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun seedVersion2() {
        SchemaFixture.createDatabaseAtVersion(databaseFile, SchemaFixture.exportedSchema(2))
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
                    "VALUES ('mov-1','var-1','loc-1',24,'RECEIPT',NULL,NULL,1757000000000,'legacy')",
                "INSERT INTO stock_on_hand (variantId,locationId,quantity,lastMovementAt) " +
                    "VALUES ('var-1','loc-1',24,1757000000000)",
                "INSERT INTO app_setting (key,value) VALUES ('printer.receipt.host','192.168.1.50')",
            ),
        )
    }

    private fun open(): KeswaDatabase = getKeswaDatabase(
        Room.databaseBuilder<KeswaDatabase>(name = databaseFile.absolutePath),
        RealDispatchers,
    )

    @Test
    fun `stock and settings both survive the addition of accounts`() = runBlocking {
        seedVersion2()

        val database = open()

        // The ledger is the source of truth, so assert it directly as well as the projection —
        // a migration is not expected to rebuild derived data, and this pins that expectation.
        assertEquals(24, database.stockLedgerDao().sumQuantity("var-1", "loc-1"))
        assertEquals(1, database.stockLedgerDao().movementCount())
        assertEquals(24, database.stockLedgerDao().getOnHand("var-1", "loc-1")?.quantity)
        assertEquals("ROU-NAV", database.variantDao().getById("var-1")?.sku)
        assertEquals("192.168.1.50", database.settingDao().get("printer.receipt.host"))
        database.close()
    }

    @Test
    fun `the upgraded database has no accounts, so it opens on first-run setup`() = runBlocking {
        seedVersion2()

        val database = open()

        assertEquals(0, database.userDao().countActive())
        assertNull(database.userDao().findByUsername("anyone"))
        database.close()
    }

    @Test
    fun `an upgrade chains all the way from version 1`() = runBlocking {
        // A shop that skipped a release upgrades through every migration in order.
        SchemaFixture.createDatabaseAtVersion(databaseFile, SchemaFixture.exportedSchema(1))
        SchemaFixture.execute(
            databaseFile,
            listOf(
                "INSERT INTO location (id,name,nameAr,type,isDefault,isActive) " +
                    "VALUES ('loc-1','Downtown','وسط البلد','SHOP',1,1)",
            ),
        )

        val database = open()

        assertEquals(1, database.locationDao().getAll().size)
        database.settingDao().put(AppSettingEntity("k", "v"))
        assertEquals("v", database.settingDao().get("k"))
        assertEquals(0, database.userDao().countActive())
        database.close()
    }
}
