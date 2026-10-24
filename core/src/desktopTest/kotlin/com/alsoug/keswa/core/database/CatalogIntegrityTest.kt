package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.database.entities.VariantBarcodeEntity
import com.alsoug.keswa.core.database.entities.VariantEntity
import com.alsoug.keswa.core.domain.model.BarcodeSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CatalogIntegrityTest {

    private val db = createTestDatabase()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `both the supplier barcode and our own resolve to the same variant`() = runTest {
        // Given a garment carrying the supplier's EAN-13 and a code we printed
        db.seedBaseData()
        val barcodes = db.variantBarcodeDao()
        barcodes.insert(VariantBarcodeEntity("6221031492016", VARIANT_TEE_NAVY, false, BarcodeSource.SUPPLIER, 0))
        barcodes.insert(VariantBarcodeEntity("2000000000017", VARIANT_TEE_NAVY, true, BarcodeSource.OWN, 0))

        // When either is scanned
        val viaSupplier = barcodes.findVariantByBarcode("6221031492016")
        val viaOwn = barcodes.findVariantByBarcode("2000000000017")

        // Then both land on the same SKU
        assertEquals(VARIANT_TEE_NAVY, viaSupplier?.id)
        assertEquals(VARIANT_TEE_NAVY, viaOwn?.id)
        assertEquals(2, barcodes.getForVariant(VARIANT_TEE_NAVY).size)
        assertNull(barcodes.findVariantByBarcode("0000000000000"))
    }

    @Test
    fun `a barcode cannot be attached to two variants`() = runTest {
        db.seedBaseData()
        db.colourDao().upsert(
            com.alsoug.keswa.core.database.entities.ColourEntity("col-red", "Red", "أحمر", "#b3322f", 1, true),
        )
        db.variantDao().insert(
            VariantEntity("var-2", PRODUCT_TEE, "col-red", "KSW-TSH-022-RD", 12_000, true, 0, 0),
        )
        db.variantBarcodeDao().insert(
            VariantBarcodeEntity("2000000000017", VARIANT_TEE_NAVY, true, BarcodeSource.OWN, 0),
        )

        // When the same barcode is claimed by another variant
        val reused = runCatching {
            db.variantBarcodeDao().insert(
                VariantBarcodeEntity("2000000000017", "var-2", true, BarcodeSource.OWN, 0),
            )
        }

        // Then it is refused — a scan must never be ambiguous
        assertTrue(reused.isFailure)
    }

    @Test
    fun `a category with products cannot be deleted`() = runTest {
        // Given a category that is in use
        db.seedBaseData()

        // When deletion is attempted
        val deleted = runCatching { db.categoryDao().deleteById(CAT_TSHIRTS) }

        // Then RESTRICT refuses it — deactivating is the supported path, so history survives
        assertTrue(deleted.isFailure, "onDelete = RESTRICT did not fire; is PRAGMA foreign_keys on?")
        assertEquals(1, db.productDao().countInCategory(CAT_TSHIRTS))
    }

    @Test
    fun `one colour per product is enforced`() = runTest {
        db.seedBaseData()

        // When a second variant of the same product claims the same colour
        val duplicate = runCatching {
            db.variantDao().insert(
                VariantEntity("var-dupe", PRODUCT_TEE, COLOUR_NAVY, "KSW-OTHER", 1, true, 0, 0),
            )
        }

        // Then the unique index refuses it — two "navy" rows would split the same SKU's stock
        assertTrue(duplicate.isFailure)
    }
}
