package com.alsoug.keswa.core.database

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.RealDispatchers
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

/**
 * The first migration that could meet a database with real trading in it.
 *
 * Everything before this shipped to nobody. From here on, a mistake in a migration destroys a
 * shop's only copy of its history — there is no server to re-fetch from until Phase 9.
 */
class Migration3To4Test {

    private val directory: File = Files.createTempDirectory("keswa-migration-3-4").toFile()
    private val databaseFile = File(directory, "keswa.db")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun seedVersion3() {
        SchemaFixture.createDatabaseAtVersion(databaseFile, SchemaFixture.exportedSchema(3))
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
                    "VALUES ('mov-1','var-1','loc-1',24,'RECEIPT',NULL,NULL,1757000000000,'user-1')",
                "INSERT INTO stock_on_hand (variantId,locationId,quantity,lastMovementAt) " +
                    "VALUES ('var-1','loc-1',24,1757000000000)",
                "INSERT INTO app_setting (key,value) VALUES ('printer.receipt.host','192.168.1.50')",
                "INSERT INTO app_user (id,username,displayName,displayNameAr,role,secretHash,secretSalt," +
                    "secretKind,isActive,mustChangeSecret,failedAttempts,lockedUntil,createdAt,updatedAt) " +
                    "VALUES ('usr-1','owner','Owner','المالك','ADMIN','hash','0011','PASSWORD',1,0,0,NULL,0,0)",
            ),
        )
    }

    private fun open(): KeswaDatabase = getKeswaDatabase(
        Room.databaseBuilder<KeswaDatabase>(name = databaseFile.absolutePath),
        RealDispatchers,
    )

    @Test
    fun `stock, catalogue, settings and accounts all survive the arrival of selling`() = runBlocking {
        seedVersion3()

        val database = open()

        assertEquals(24, database.stockLedgerDao().sumQuantity("var-1", "loc-1"))
        assertEquals(24, database.stockLedgerDao().getOnHand("var-1", "loc-1")?.quantity)
        assertEquals("ROU-NAV", database.variantDao().getById("var-1")?.sku)
        assertEquals("192.168.1.50", database.settingDao().get("printer.receipt.host"))
        assertNotNull(database.userDao().findByUsername("owner"))
        database.close()
    }

    @Test
    fun `the new tables open empty and usable`() = runBlocking {
        seedVersion3()

        val database = open()

        // Numbering starts at one on an upgraded database, exactly as on a fresh one.
        assertEquals(1L, database.saleDao().nextReceiptNumber())
        assertEquals(emptyList(), database.heldSaleDao().list("loc-1"))
        assertEquals(null, database.shiftDao().getOpen("loc-1"))
        database.close()
    }
}
