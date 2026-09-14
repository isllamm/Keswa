package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.database.dao.CategoryDao
import com.alsoug.keswa.core.database.dao.ColourDao
import com.alsoug.keswa.core.database.dao.ProductDao
import com.alsoug.keswa.core.database.dao.StockLedgerDao
import com.alsoug.keswa.core.database.dao.VariantBarcodeDao
import com.alsoug.keswa.core.database.dao.VariantDao
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.ProductEntity
import com.alsoug.keswa.core.database.entities.VariantBarcodeEntity
import com.alsoug.keswa.core.database.entities.VariantEntity
import com.alsoug.keswa.core.domain.model.Barcode
import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.core.domain.model.Product
import com.alsoug.keswa.core.domain.model.Variant
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProductRepositoryImpl(
    private val dao: ProductDao,
    private val categoryDao: CategoryDao,
    private val now: () -> Long,
) : IProductRepository {

    override suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        categoryId: String,
        supplierId: String?,
        season: String?,
    ): Result<Product> = runCatchingCancellable {
        requireNotNull(categoryDao.getById(categoryId)) { "category not found: $categoryId" }
        val timestamp = now()
        val entity = ProductEntity(
            id = id,
            name = name,
            nameAr = nameAr,
            categoryId = categoryId,
            brandId = null,
            supplierId = supplierId,
            season = season,
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        dao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun update(product: Product): Result<Unit> = runCatchingCancellable {
        val existing = requireNotNull(dao.getById(product.id)) { "product not found: ${product.id}" }
        dao.upsert(
            existing.copy(
                name = product.name,
                nameAr = product.nameAr,
                categoryId = product.categoryId,
                supplierId = product.supplierId,
                season = product.season,
                isActive = product.isActive,
                updatedAt = now(),
            ),
        )
    }

    override suspend fun getById(id: String): Result<Product?> = runCatchingCancellable {
        dao.getById(id)?.toDomain()
    }

    override suspend fun inCategoryTree(pathPrefix: String): Result<List<Product>> =
        runCatchingCancellable { dao.getInCategoryTree(pathPrefix).map { it.toDomain() } }

    override fun observeAll(): Flow<List<Product>> =
        dao.observeAll().map { products -> products.map { it.toDomain() } }
}

class ColourRepositoryImpl(
    private val dao: ColourDao,
) : IColourRepository {

    override suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        hex: String,
        sortOrder: Int,
    ): Result<Colour> = runCatchingCancellable {
        val entity = ColourEntity(id, name, nameAr, hex, sortOrder, isActive = true)
        dao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun getAll(): Result<List<Colour>> =
        runCatchingCancellable { dao.getAll().map { it.toDomain() } }

    override fun observeAll(): Flow<List<Colour>> =
        dao.observeAll().map { colours -> colours.map { it.toDomain() } }
}

class VariantRepositoryImpl(
    private val dao: VariantDao,
    private val barcodeDao: VariantBarcodeDao,
    private val ledger: StockLedgerDao,
    private val now: () -> Long,
) : IVariantRepository {

    override suspend fun addColour(
        id: String,
        productId: String,
        colourId: String,
        sku: String,
        cost: Money,
    ): Result<Variant> = runCatchingCancellable {
        val timestamp = now()
        val entity = VariantEntity(
            id = id,
            productId = productId,
            colourId = colourId,
            sku = sku,
            costPiastres = cost.piastres,
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        // insert, not upsert: the unique (productId, colourId) index must refuse a second
        // "navy" for the same product rather than merge it into the existing SKU.
        dao.insert(entity)
        entity.toDomain()
    }

    override suspend fun forProduct(productId: String): Result<List<Variant>> =
        runCatchingCancellable { dao.getForProduct(productId).map { it.toDomain() } }

    override suspend fun skuExists(sku: String): Result<Boolean> =
        runCatchingCancellable { dao.getBySku(sku) != null }

    override suspend fun getById(id: String): Result<Variant?> =
        runCatchingCancellable { dao.getById(id)?.toDomain() }

    override suspend fun deactivate(id: String): Result<Unit> = runCatchingCancellable {
        val existing = requireNotNull(dao.getById(id)) { "variant not found: $id" }
        dao.update(existing.copy(isActive = false, updatedAt = now()))
    }

    override suspend fun onHand(variantId: String): Result<Int> =
        runCatchingCancellable { ledger.sumQuantityEverywhere(variantId) }

    override suspend fun attachBarcode(
        barcode: String,
        variantId: String,
        source: BarcodeSource,
        isPrimary: Boolean,
    ): Result<Unit> = runCatchingCancellable {
        barcodeDao.insert(
            VariantBarcodeEntity(
                barcode = barcode,
                variantId = variantId,
                isPrimary = isPrimary,
                source = source,
                createdAt = now(),
            ),
        )
    }

    override suspend fun barcodesFor(variantId: String): Result<List<Barcode>> =
        runCatchingCancellable {
            barcodeDao.getForVariant(variantId).map {
                Barcode(it.barcode, it.variantId, it.isPrimary, it.source)
            }
        }

    override suspend fun findByBarcode(barcode: String): Result<Variant?> =
        runCatchingCancellable { barcodeDao.findVariantByBarcode(barcode)?.toDomain() }

    override suspend fun ownBarcodeCount(): Result<Int> =
        runCatchingCancellable { barcodeDao.countBySource(BarcodeSource.OWN) }
}
