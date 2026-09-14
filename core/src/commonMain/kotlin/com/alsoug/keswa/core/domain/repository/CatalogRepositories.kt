package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.Barcode
import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.core.domain.model.Product
import com.alsoug.keswa.core.domain.model.Variant
import com.alsoug.keswa.core.domain.money.Money
import kotlinx.coroutines.flow.Flow

/**
 * Ids are supplied by the caller throughout: they are client-generated UUIDs so a later sync can
 * upsert idempotently, and it keeps tests deterministic.
 */
interface IProductRepository {

    suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        categoryId: String,
        supplierId: String? = null,
        season: String? = null,
    ): Result<Product>

    suspend fun update(product: Product): Result<Unit>

    suspend fun getById(id: String): Result<Product?>

    /** Products filed under [pathPrefix] or any category beneath it. */
    suspend fun inCategoryTree(pathPrefix: String): Result<List<Product>>

    fun observeAll(): Flow<List<Product>>
}

interface IColourRepository {

    suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        hex: String,
        sortOrder: Int = 0,
    ): Result<Colour>

    suspend fun getAll(): Result<List<Colour>>

    fun observeAll(): Flow<List<Colour>>
}

interface IVariantRepository {

    /** Creates the SKU for one colour of a product. Fails if that colour is already stocked. */
    suspend fun addColour(
        id: String,
        productId: String,
        colourId: String,
        sku: String,
        cost: Money,
    ): Result<Variant>

    suspend fun forProduct(productId: String): Result<List<Variant>>

    suspend fun skuExists(sku: String): Result<Boolean>

    suspend fun getById(id: String): Result<Variant?>

    /**
     * Retires a variant. Never deletes it — stock history references it, and the ledger is
     * append-only.
     */
    suspend fun deactivate(id: String): Result<Unit>

    /** Total on hand across every location. */
    suspend fun onHand(variantId: String): Result<Int>

    suspend fun attachBarcode(
        barcode: String,
        variantId: String,
        source: BarcodeSource,
        isPrimary: Boolean = false,
    ): Result<Unit>

    suspend fun barcodesFor(variantId: String): Result<List<Barcode>>

    suspend fun findByBarcode(barcode: String): Result<Variant?>

    /** How many codes the shop has printed itself — the sequence for the next one. */
    suspend fun ownBarcodeCount(): Result<Int>
}
