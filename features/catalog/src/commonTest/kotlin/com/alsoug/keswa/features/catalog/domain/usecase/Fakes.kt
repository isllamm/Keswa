package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
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
import kotlinx.coroutines.flow.flowOf

/** ADR-019: hand-written fakes, no mocking framework. */

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}

class FakeProductRepository(
    private val products: MutableMap<String, Product> = mutableMapOf(),
) : IProductRepository {

    fun given(product: Product) = apply { products[product.id] = product }

    override suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        categoryId: String,
        supplierId: String?,
        season: String?,
    ): Result<Product> {
        val product = Product(id, name, nameAr, categoryId, null, supplierId, season, true)
        products[id] = product
        return Result.success(product)
    }

    override suspend fun update(product: Product): Result<Unit> {
        products[product.id] = product
        return Result.success(Unit)
    }

    override suspend fun getById(id: String): Result<Product?> = Result.success(products[id])

    override suspend fun inCategoryTree(pathPrefix: String): Result<List<Product>> =
        Result.success(products.values.toList())

    override fun observeAll(): Flow<List<Product>> = flowOf(products.values.toList())
}

class FakeColourRepository(
    private val colours: MutableList<Colour> = mutableListOf(),
) : IColourRepository {

    fun given(colour: Colour) = apply { colours += colour }

    override suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        hex: String,
        sortOrder: Int,
    ): Result<Colour> {
        val colour = Colour(id, name, nameAr, hex, sortOrder, true)
        colours += colour
        return Result.success(colour)
    }

    override suspend fun getAll(): Result<List<Colour>> = Result.success(colours.toList())

    override fun observeAll(): Flow<List<Colour>> = flowOf(colours.toList())
}

class FakeVariantRepository : IVariantRepository {

    val variants = mutableMapOf<String, Variant>()
    val barcodes = mutableListOf<Barcode>()
    var stock = mutableMapOf<String, Int>()

    fun givenStock(variantId: String, quantity: Int) = apply { stock[variantId] = quantity }

    override suspend fun addColour(
        id: String,
        productId: String,
        colourId: String,
        sku: String,
        cost: Money,
    ): Result<Variant> {
        val variant = Variant(id, productId, colourId, sku, cost, true)
        variants[id] = variant
        return Result.success(variant)
    }

    override suspend fun forProduct(productId: String): Result<List<Variant>> =
        Result.success(variants.values.filter { it.productId == productId })

    override suspend fun skuExists(sku: String): Result<Boolean> =
        Result.success(variants.values.any { it.sku == sku })

    override suspend fun getById(id: String): Result<Variant?> = Result.success(variants[id])

    override suspend fun deactivate(id: String): Result<Unit> {
        variants[id]?.let { variants[id] = it.copy(isActive = false) }
        return Result.success(Unit)
    }

    override suspend fun onHand(variantId: String): Result<Int> =
        Result.success(stock[variantId] ?: 0)

    override suspend fun attachBarcode(
        barcode: String,
        variantId: String,
        source: BarcodeSource,
        isPrimary: Boolean,
    ): Result<Unit> {
        barcodes += Barcode(barcode, variantId, isPrimary, source)
        return Result.success(Unit)
    }

    override suspend fun barcodesFor(variantId: String): Result<List<Barcode>> =
        Result.success(barcodes.filter { it.variantId == variantId })

    override suspend fun findByBarcode(barcode: String): Result<Variant?> =
        Result.success(barcodes.firstOrNull { it.barcode == barcode }?.let { variants[it.variantId] })

    override suspend fun ownBarcodeCount(): Result<Int> =
        Result.success(barcodes.count { it.source == BarcodeSource.OWN })
}

fun product(id: String = "p1", name: String = "Oxford shirt") =
    Product(id, name, "قميص أكسفورد", "cat-1", null, null, null, true)

fun colour(id: String = "c1", name: String = "Navy") =
    Colour(id, name, "كحلي", "#20304f", 0, true)
