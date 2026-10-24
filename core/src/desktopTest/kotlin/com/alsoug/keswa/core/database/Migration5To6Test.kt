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
 * Returns arrive against a database that already holds real sales, and references them.
 *
 * Additive, and it has to be — by this point there is nothing in the schema that could be
 * rebuilt from anywhere else.
 */
class Migration5To6Test {

    private val directory: File = Files.createTempDirectory("keswa-migration-5-6").toFile()
    private val databaseFile = File(directory, "keswa.db")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun seedVersion5() {
        SchemaFixture.createDatabaseAtVersion(databaseFile, SchemaFixture.exportedSchema(5))
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
                // A real sale, which is the thing this migration must not endanger.
                "INSERT INTO sale (id,receiptNumber,locationId,priceListId,userId,shiftId,status," +
                    "subtotalPiastres,discountPiastres,taxPiastres,totalPiastres,tenderedPiastres," +
                    "changePiastres,occurredAt,voidedAt,voidedByUserId,voidReason) " +
                    "VALUES ('sale-1',1,'loc-1','pl-1','usr-1',NULL,'COMPLETED',36000,0,0,36000,40000,4000," +
                    "1757000500000,NULL,NULL,NULL)",
                "INSERT INTO sale_line (id,saleId,lineNumber,variantId,description,descriptionAr," +
                    "quantity,unitPricePiastres,lineDiscountPiastres,orderDiscountPiastres," +
                    "lineTotalPiastres,taxPiastres,unitCostPiastres,authorisedByUserId) " +
                    "VALUES ('sl-1','sale-1',1,'var-1','Round-neck — Navy','تيشيرت',2,18000,0,0,36000,0,12000,NULL)",
                "INSERT INTO payment (id,saleId,method,amountPiastres,tenderedPiastres,reference,occurredAt) " +
                    "VALUES ('pay-1','sale-1','CASH',36000,40000,NULL,1757000500000)",
            ),
        )
    }

    private fun open(): KeswaDatabase = getKeswaDatabase(
        Room.databaseBuilder<KeswaDatabase>(name = databaseFile.absolutePath),
        RealDispatchers,
    )

    @Test
    fun `a year of trading survives the arrival of returns`() = runBlocking {
        seedVersion5()

        val database = open()

        assertEquals(24, database.stockLedgerDao().sumQuantity("var-1", "loc-1"))
        assertEquals(24, database.stockLedgerDao().getOnHand("var-1", "loc-1")?.quantity)
        assertEquals("ROU-NAV", database.variantDao().getById("var-1")?.sku)
        assertNotNull(database.userDao().findByUsername("owner"))

        val sale = assertNotNull(database.saleDao().getById("sale-1"))
        assertEquals(1L, sale.receiptNumber)
        assertEquals(36_000, sale.totalPiastres)
        assertEquals(1, database.saleDao().getLines("sale-1").size)
        assertEquals(1, database.saleDao().getPayments("sale-1").size)
        database.close()
    }

    @Test
    fun `the new tables open empty and usable`() = runBlocking {
        seedVersion5()

        val database = open()

        // Numbering starts at one on an upgraded database, exactly as on a fresh one.
        assertEquals(1L, database.saleReturnDao().nextReturnNumber())
        assertEquals(emptyList(), database.saleReturnDao().forSale("sale-1"))
        database.close()
    }

    @Test
    fun `an existing sale is immediately returnable`() = runBlocking {
        seedVersion5()

        val database = open()

        // The whole point of the migration being additive: a receipt printed last week still
        // works as a receipt today.
        assertEquals(0, database.saleReturnDao().alreadyReturned("sl-1"))
        assertEquals(18_000, database.saleReturnDao().lowestSoldPrice("var-1"))
        database.close()
    }
}
