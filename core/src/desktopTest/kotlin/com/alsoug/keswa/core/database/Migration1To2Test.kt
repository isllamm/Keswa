package com.alsoug.keswa.core.database

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.RealDispatchers
import com.alsoug.keswa.core.database.entities.AppSettingEntity
import com.alsoug.keswa.core.domain.model.MovementReason
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The pattern every future migration test follows.
 *
 * Seed at version N with data a real shop would have, migrate, and assert every value survived —
 * not just that the migration ran. `app_setting` was deliberately scheduled as a migration rather
 * than folded into v1 so this harness would be proven on a small, additive change, while there is
 * no production data to lose.
 */
class Migration1To2Test {

    private val directory: File = Files.createTempDirectory("keswa-migration-1-2").toFile()
    private val databaseFile = File(directory, "keswa.db")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun seedVersion1() {
        SchemaFixture.createDatabaseAtVersion(databaseFile, SchemaFixture.exportedSchema(1))
        SchemaFixture.execute(
            databaseFile,
            // Named columns, never positional: the fixture must not quietly depend on the order
            // Room happened to declare, which is exactly what a migration is allowed to change.
            listOf(
                "INSERT INTO location (id,name,nameAr,type,isDefault,isActive) " +
                    "VALUES ('loc-1','Downtown','وسط البلد','SHOP',1,1)",
                "INSERT INTO category (id,parentId,name,nameAr,path,depth,sortOrder,isActive,createdAt,updatedAt) " +
                    "VALUES ('cat-1',NULL,'T-shirts','تيشيرتات','/cat-1/',0,0,1,0,0)",
                "INSERT INTO colour (id,name,nameAr,hex,sortOrder,isActive) " +
                    "VALUES ('col-1','Navy','كحلي','#20304f',0,1)",
                "INSERT INTO product (id,name,nameAr,categoryId,brandId,supplierId,season,isActive,createdAt,updatedAt) " +
                    "VALUES ('prod-1','Round-neck t-shirt','تيشيرت رقبة دائرية','cat-1',NULL,NULL,NULL,1,0,0)",
                "INSERT INTO variant (id,productId,colourId,sku,costPiastres,isActive,createdAt,updatedAt) " +
                    "VALUES ('var-1','prod-1','col-1','ROU-NAV',12000,1,0,0)",
                "INSERT INTO stock_movement (id,variantId,locationId,quantity,reason,refType,refId,occurredAt,userId) " +
                    "VALUES ('mov-1','var-1','loc-1',24,'RECEIPT','PURCHASE_ORDER','PO-2026-0412',1757000000000,'user-1')",
                "INSERT INTO stock_on_hand (variantId,locationId,quantity,lastMovementAt) " +
                    "VALUES ('var-1','loc-1',24,1757000000000)",
            ),
        )
    }

    private fun openAtVersion2(): KeswaDatabase =
        getKeswaDatabase(
            Room.databaseBuilder<KeswaDatabase>(name = databaseFile.absolutePath),
            RealDispatchers,
        )

    @Test
    fun `the shop's ledger survives the upgrade intact`() = runBlocking {
        // Given a shop already trading on version 1
        seedVersion1()

        // When the app opens after an update
        val database = openAtVersion2()

        // Then every value is exactly as it was — not merely present
        val movements = database.stockLedgerDao().getMovements("var-1", "loc-1")
        assertEquals(1, movements.size)
        val movement = movements.single()
        assertEquals(24, movement.quantity)
        assertEquals(MovementReason.RECEIPT, movement.reason)
        assertEquals("PO-2026-0412", movement.refId)
        assertEquals(1_757_000_000_000, movement.occurredAt)
        assertEquals("user-1", movement.userId)

        assertEquals(24, database.stockLedgerDao().getOnHand("var-1", "loc-1")?.quantity)
        assertEquals("ROU-NAV", database.variantDao().getById("var-1")?.sku)
        assertEquals("/cat-1/", database.categoryDao().getById("cat-1")?.path)
        database.close()
    }

    @Test
    fun `the new settings table is usable straight after the upgrade`() = runBlocking {
        seedVersion1()

        val database = openAtVersion2()
        database.settingDao().put(AppSettingEntity("printer.host", "192.168.1.50"))

        assertEquals("192.168.1.50", database.settingDao().get("printer.host"))
        database.close()
    }

    @Test
    fun `a database already at the current version opens untouched`() = runBlocking {
        // Guards the no-op path: an app restart must not re-run a migration.
        SchemaFixture.createDatabaseAtVersion(databaseFile, SchemaFixture.exportedSchema(2))

        val database = openAtVersion2()
        database.settingDao().put(AppSettingEntity("paper.width", "576"))

        assertEquals("576", database.settingDao().get("paper.width"))
        assertTrue(database.stockLedgerDao().getMovements("var-1", "loc-1").isEmpty())
        database.close()
    }
}
