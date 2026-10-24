package com.alsoug.keswa.core.database

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.RealDispatchers
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.money.Money
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/**
 * 8 → 9: `stock_receipt_line` gains `sellPricePiastres`.
 *
 * A shop upgrading mid-delivery has a draft open on the back-room counter and posted deliveries in
 * its history; both must survive intact with `sellPrice = null`.
 */
class Migration8To9Test {

    private val directory: File = Files.createTempDirectory("keswa-migration-8-9").toFile()
    private val databaseFile = File(directory, "keswa.db")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun seedVersion8() {
        SchemaFixture.createDatabaseAtVersion(databaseFile, SchemaFixture.exportedSchema(8))
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
                "INSERT INTO stock_receipt (id,reference,supplierName,locationId,status,note,createdAt," +
                    "createdByUserId,postedAt,postedByUserId,totalCostPiastres) " +
                    "VALUES ('rec-draft','INV-10','Nile Textiles','loc-1','DRAFT',NULL,1757000000000,'usr-1',NULL,NULL,0)",
                "INSERT INTO stock_receipt_line (id,receiptId,lineNumber,variantId,quantity,unitCostPiastres,lineTotalPiastres) " +
                    "VALUES ('line-draft','rec-draft',1,'var-1',12,13500,162000)",
                "INSERT INTO stock_receipt (id,reference,supplierName,locationId,status,note,createdAt," +
                    "createdByUserId,postedAt,postedByUserId,totalCostPiastres) " +
                    "VALUES ('rec-posted','INV-09','Nile Textiles','loc-1','POSTED',NULL,1756900000000," +
                    "'usr-1',1756900500000,'usr-1',240000)",
                "INSERT INTO stock_receipt_line (id,receiptId,lineNumber,variantId,quantity,unitCostPiastres,lineTotalPiastres) " +
                    "VALUES ('line-posted','rec-posted',1,'var-1',20,12000,240000)",
            ),
        )
    }

    private fun open(): KeswaDatabase = getKeswaDatabase(
        Room.databaseBuilder<KeswaDatabase>(name = databaseFile.absolutePath),
        RealDispatchers,
    )

    @Test
    fun `a draft and a posted receipt survive intact with a null sell price`() = runBlocking {
        seedVersion8()

        val database = open()

        val draftHeader = assertNotNull(database.stockReceiptDao().getById("rec-draft"))
        val draftLine = database.stockReceiptDao().getLines("rec-draft").single().toDomain()
        assertEquals(DocumentStatus.DRAFT, draftHeader.status)
        assertEquals(12, draftLine.quantity)
        assertEquals(Money.ofPiastres(13_500), draftLine.unitCost)
        assertNull(draftLine.sellPrice)

        val postedHeader = assertNotNull(database.stockReceiptDao().getById("rec-posted"))
        val postedLine = database.stockReceiptDao().getLines("rec-posted").single().toDomain()
        assertEquals(DocumentStatus.POSTED, postedHeader.status)
        assertEquals(20, postedLine.quantity)
        assertEquals(Money.ofPiastres(12_000), postedLine.unitCost)
        assertNull(postedLine.sellPrice)

        database.close()
    }
}
