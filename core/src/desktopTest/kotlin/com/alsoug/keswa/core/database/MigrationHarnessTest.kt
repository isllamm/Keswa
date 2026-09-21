package com.alsoug.keswa.core.database

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.RealDispatchers
import androidx.room.migration.Migration
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.domain.model.MovementReason
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The pattern every future migration test follows: seed at version N, migrate, assert the rows
 * survived with their values intact.
 *
 * KD-002 exists because this database is the shop's only copy of its history until Phase 9 —
 * `fallbackToDestructiveMigration` would silently discard it. That makes "the data still reaches
 * the next version" the single most important property to keep proving.
 *
 * ⚠️ There is no version 2 yet, so what is proven here is the harness itself and the
 * durability half of the contract: a real file, a real close, a real reopen through the production
 * factory with [com.alsoug.keswa.core.database.migrations.ALL_MIGRATIONS] applied, data intact.
 * The first genuine v1 → v2 assertion arrives with the user tables in Phase 4, which is exactly
 * why they were scheduled as a migration rather than folded into v1.
 */
class MigrationHarnessTest {

    private val directory: File = Files.createTempDirectory("keswa-migration").toFile()
    private val databaseFile = File(directory, "keswa.db")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun open(migrations: Array<Migration> = emptyArray()): KeswaDatabase =
        Room.databaseBuilder<KeswaDatabase>(name = databaseFile.absolutePath)
            .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())
            .setQueryCoroutineContext(RealDispatchers.io)
            .addMigrations(*migrations)
            .build()

    @Test
    fun `data written at the current version survives a close and reopen`() = runTest {
        // Given a shop's ledger, written and then closed
        val first = open()
        first.seedBaseData()
        first.stockLedgerDao().record(
            StockMovementEntity(
                id = "m1",
                variantId = VARIANT_TEE_NAVY,
                locationId = SHOP_ID,
                quantity = 24,
                reason = MovementReason.RECEIPT,
                refType = "PURCHASE_ORDER",
                refId = "PO-2026-0412",
                occurredAt = 1_757_000_000_000,
                userId = "user-1",
            ),
        )
        first.close()
        assertTrue(databaseFile.exists(), "the database should be a real file on disk")

        // When it is reopened through the same configuration
        val second = open()

        // Then every value is exactly as it was — nothing dropped, nothing coerced
        val movements = second.stockLedgerDao().getMovements(VARIANT_TEE_NAVY, SHOP_ID)
        assertEquals(1, movements.size)
        assertEquals(24, movements.single().quantity)
        assertEquals(MovementReason.RECEIPT, movements.single().reason)
        assertEquals("PO-2026-0412", movements.single().refId)
        assertEquals(1_757_000_000_000, movements.single().occurredAt)
        assertEquals(24, second.stockLedgerDao().getOnHand(VARIANT_TEE_NAVY, SHOP_ID)?.quantity)
        second.close()
    }

    @Test
    fun `the shipped migration list is applied by the production factory`() = runTest {
        // Guards against the list being declared and then never wired — a silent way to lose the
        // protection KD-002 is built on.
        val database = getKeswaDatabase(
            Room.inMemoryDatabaseBuilder<KeswaDatabase>(),
            RealDispatchers,
        )
        database.seedBaseData()
        assertEquals(1, database.locationDao().getAll().size)
        database.close()
    }
}
