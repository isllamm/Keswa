package com.alsoug.keswa.features.inventory.fakes

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Barcode
import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.model.Location
import com.alsoug.keswa.core.domain.model.LocationType
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.PriceList
import com.alsoug.keswa.core.domain.model.PriceListType
import com.alsoug.keswa.core.domain.model.Product
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.model.StockCount
import com.alsoug.keswa.core.domain.model.StockCountLine
import com.alsoug.keswa.core.domain.model.StockMovement
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.model.StockReceiptLine
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.model.Variant
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.CostChange
import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IStockAdjustmentRepository
import com.alsoug.keswa.core.domain.repository.IStockCountRepository
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.core.domain.repository.PostedReceipt
import com.alsoug.keswa.core.platform.IPrinterTransport
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.session.InMemorySessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var counter = 1
    override fun newId(): String = "$prefix-${counter++}"
}

fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider = object : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

fun fullPermissionSession(): InMemorySessionManager {
    val session = InMemorySessionManager()
    session.signIn(
        User(
            id = "usr-admin",
            username = "admin",
            displayName = "Admin",
            displayNameAr = "المدير",
            role = UserRole.ADMIN,
        ),
        atMillis = 0,
    )
    return session
}

class FakeLocationRepository(
    private val defaultLocation: Location = Location("loc-1", "Main Store", "المحل الرئيسي", LocationType.SHOP, isDefault = true, isActive = true),
) : ILocationRepository {
    override suspend fun default(): Result<Location?> = Result.success(defaultLocation)
    override suspend fun ensureDefault(id: String, name: String, nameAr: String): Result<Location> =
        Result.success(defaultLocation)
    override suspend fun getAll(): Result<List<Location>> = Result.success(listOf(defaultLocation))
}

class FakePriceRepository(
    private val defaultList: PriceList = PriceList("plist-1", "Standard", "الأساسي", PriceListType.RETAIL, isDefault = true, isActive = true),
) : IPriceRepository {
    override suspend fun defaultList(): Result<PriceList?> = Result.success(defaultList)
    override suspend fun ensureDefaultList(id: String, name: String, nameAr: String): Result<PriceList> =
        Result.success(defaultList)
    override suspend fun listsOfType(type: PriceListType): Result<List<PriceList>> = Result.success(listOf(defaultList))
    override suspend fun createList(id: String, name: String, nameAr: String, type: PriceListType): Result<PriceList> =
        Result.success(defaultList.copy(id = id, name = name, nameAr = nameAr, type = type))
    override suspend fun setPrice(id: String, variantId: String, priceListId: String, price: Money, from: Long): Result<Unit> =
        Result.success(Unit)
    override suspend fun effectivePrice(variantId: String, priceListId: String, at: Long): Result<Money?> =
        Result.success(Money.ofPounds(200))
}

class FakeProductRepository : IProductRepository {
    private val products = mutableMapOf<String, Product>()

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

class FakeColourRepository : IColourRepository {
    private val colours = mutableListOf<Colour>()

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

class FakeCategoryRepository : ICategoryRepository {
    private val cat = Category("cat-1", null, "Apparel", "ملابس", "cat-1", 0, 0, true)

    override suspend fun create(
        id: String,
        parentId: String?,
        name: String,
        nameAr: String,
        sortOrder: Int,
    ): Result<Category> = Result.success(cat)

    override suspend fun move(categoryId: String, newParentId: String?): Result<Unit> = Result.success(Unit)

    override suspend fun getTree(): Result<List<Category>> = Result.success(listOf(cat))

    override suspend fun getById(id: String): Result<Category?> = Result.success(cat)

    override suspend fun pathOf(id: String): Result<String?> = Result.success(cat.path)
}

class FakeSellableRepository : ISellableRepository {
    val items = mutableMapOf<String, SellableItem>()
    private val barcodeMap = mutableMapOf<String, String>()

    fun withItem(item: SellableItem, barcode: String? = null): FakeSellableRepository {
        items[item.variantId] = item
        if (barcode != null) barcodeMap[barcode] = item.variantId
        return this
    }

    override suspend fun byBarcode(barcode: String, priceListId: String, locationId: String, at: Long): Result<SellableItem?> =
        Result.success(barcodeMap[barcode]?.let { items[it] })

    override suspend fun byVariantId(variantId: String, priceListId: String, locationId: String, at: Long): Result<SellableItem?> =
        Result.success(items[variantId])

    override suspend fun search(term: String, priceListId: String, locationId: String, at: Long, limit: Int): Result<List<SellableItem>> =
        Result.success(items.values.filter { it.sku.contains(term, ignoreCase = true) || it.description.contains(term, ignoreCase = true) })
}

class FakeStockReceiptRepository : IStockReceiptRepository {
    private val receipts = mutableMapOf<String, StockReceipt>()
    private val draftsFlow = MutableStateFlow<List<StockReceipt>>(emptyList())

    override suspend fun createDraft(
        id: String,
        reference: String,
        supplierName: String,
        locationId: String,
        userId: String,
        atMillis: Long,
    ): Result<StockReceipt> {
        val receipt = StockReceipt(
            id = id,
            reference = reference,
            supplierName = supplierName,
            locationId = locationId,
            status = DocumentStatus.DRAFT,
            note = null,
            createdAt = atMillis,
            createdByUserId = userId,
        )
        receipts[id] = receipt
        updateFlow()
        return Result.success(receipt)
    }

    override suspend fun putLine(
        id: String,
        receiptId: String,
        variantId: String,
        quantity: Int,
        unitCost: Money,
    ): Result<StockReceipt> {
        val receipt = receipts[receiptId] ?: return Result.failure(IllegalArgumentException("not found"))
        val existingIndex = receipt.lines.indexOfFirst { it.variantId == variantId }
        val updatedLines = receipt.lines.toMutableList()
        val newLine = StockReceiptLine(
            id = if (existingIndex >= 0) updatedLines[existingIndex].id else id,
            receiptId = receiptId,
            lineNumber = if (existingIndex >= 0) updatedLines[existingIndex].lineNumber else receipt.lines.size + 1,
            variantId = variantId,
            quantity = quantity,
            unitCost = unitCost,
            lineTotal = unitCost * quantity,
        )
        if (existingIndex >= 0) updatedLines[existingIndex] = newLine else updatedLines.add(newLine)
        val updated = receipt.copy(lines = updatedLines)
        receipts[receiptId] = updated
        updateFlow()
        return Result.success(updated)
    }

    override suspend fun removeLine(receiptId: String, lineId: String): Result<StockReceipt> {
        val receipt = receipts[receiptId] ?: return Result.failure(IllegalArgumentException("not found"))
        val updated = receipt.copy(lines = receipt.lines.filterNot { it.id == lineId })
        receipts[receiptId] = updated
        updateFlow()
        return Result.success(updated)
    }

    override suspend fun post(receiptId: String, userId: String, atMillis: Long): Result<PostedReceipt> {
        val receipt = receipts[receiptId] ?: return Result.failure(IllegalArgumentException("not found"))
        val posted = receipt.copy(
            status = DocumentStatus.POSTED,
            postedAt = atMillis,
            postedByUserId = userId,
        )
        receipts[receiptId] = posted
        updateFlow()
        val changes = receipt.lines.map { line ->
            CostChange(line.variantId, Money.ofPounds(100), line.unitCost)
        }
        return Result.success(PostedReceipt(posted, changes))
    }

    override suspend fun getById(id: String): Result<StockReceipt?> = Result.success(receipts[id])

    override suspend fun discardDraft(id: String): Result<Unit> {
        receipts.remove(id)
        updateFlow()
        return Result.success(Unit)
    }

    override suspend fun recent(locationId: String, limit: Int): Result<List<StockReceipt>> =
        Result.success(receipts.values.filter { it.locationId == locationId }.take(limit))

    override fun observeDrafts(locationId: String): Flow<List<StockReceipt>> =
        draftsFlow.asStateFlow().map { list -> list.filter { it.locationId == locationId && it.isDraft } }

    private fun updateFlow() {
        draftsFlow.value = receipts.values.toList()
    }
}

class FakeStockCountRepository : IStockCountRepository {
    private val counts = mutableMapOf<String, StockCount>()

    override suspend fun start(id: String, locationId: String, userId: String, atMillis: Long): Result<StockCount> {
        val count = StockCount(
            id = id,
            locationId = locationId,
            status = DocumentStatus.DRAFT,
            note = null,
            startedAt = atMillis,
            startedByUserId = userId,
        )
        counts[id] = count
        return Result.success(count)
    }

    override suspend fun putLine(id: String, countId: String, variantId: String, counted: Int): Result<StockCount> {
        val count = counts[countId] ?: return Result.failure(IllegalArgumentException("not found"))
        val existingIndex = count.lines.indexOfFirst { it.variantId == variantId }
        val updatedLines = count.lines.toMutableList()
        val newLine = StockCountLine(
            id = if (existingIndex >= 0) updatedLines[existingIndex].id else id,
            countId = countId,
            lineNumber = if (existingIndex >= 0) updatedLines[existingIndex].lineNumber else count.lines.size + 1,
            variantId = variantId,
            counted = counted,
        )
        if (existingIndex >= 0) updatedLines[existingIndex] = newLine else updatedLines.add(newLine)
        val updated = count.copy(lines = updatedLines)
        counts[countId] = updated
        return Result.success(updated)
    }

    override suspend fun post(countId: String, userId: String, note: String?, atMillis: Long): Result<StockCount> {
        val count = counts[countId] ?: return Result.failure(IllegalArgumentException("not found"))
        val posted = count.copy(
            status = DocumentStatus.POSTED,
            note = note,
            postedAt = atMillis,
            postedByUserId = userId,
            lines = count.lines.map { it.copy(expected = 10, variance = it.counted - 10) },
        )
        counts[countId] = posted
        return Result.success(posted)
    }

    override suspend fun getById(id: String): Result<StockCount?> = Result.success(counts[id])
    override suspend fun current(locationId: String): Result<StockCount?> =
        Result.success(counts.values.firstOrNull { it.locationId == locationId && it.isOpen })
    override suspend fun discard(id: String): Result<Unit> {
        counts.remove(id)
        return Result.success(Unit)
    }
    override suspend fun recent(locationId: String, limit: Int): Result<List<StockCount>> =
        Result.success(counts.values.filter { it.locationId == locationId }.take(limit))
}

class FakeStockAdjustmentRepository : IStockAdjustmentRepository {
    val movements = mutableListOf<StockMovement>()

    override suspend fun adjust(
        id: String,
        variantId: String,
        locationId: String,
        quantity: Int,
        reason: MovementReason,
        note: String,
        userId: String,
        atMillis: Long,
    ): Result<StockMovement> {
        val movement = StockMovement(
            id = id,
            variantId = variantId,
            locationId = locationId,
            quantity = quantity,
            reason = reason,
            refType = null,
            refId = null,
            occurredAt = atMillis,
            userId = userId,
            note = note,
        )
        movements += movement
        return Result.success(movement)
    }

    override suspend fun historyFor(variantId: String, locationId: String): Result<List<StockMovement>> =
        Result.success(movements.filter { it.variantId == variantId && it.locationId == locationId })
}

class FakeVariantRepository : IVariantRepository {
    private val barcodes = mutableMapOf<String, MutableList<Barcode>>()

    fun withBarcode(variantId: String, code: String, isPrimary: Boolean = true): FakeVariantRepository {
        barcodes.getOrPut(variantId) { mutableListOf() }.add(Barcode(code, variantId, isPrimary, BarcodeSource.OWN))
        return this
    }

    override suspend fun barcodesFor(variantId: String): Result<List<Barcode>> =
        Result.success(barcodes[variantId] ?: emptyList())

    override suspend fun attachBarcode(barcode: String, variantId: String, source: BarcodeSource, isPrimary: Boolean): Result<Unit> {
        barcodes.getOrPut(variantId) { mutableListOf() }.add(Barcode(barcode, variantId, isPrimary, source))
        return Result.success(Unit)
    }

    override suspend fun addColour(id: String, productId: String, colourId: String, sku: String, cost: Money): Result<Variant> =
        Result.success(Variant(id, productId, colourId, sku, cost, true))
    override suspend fun forProduct(productId: String): Result<List<Variant>> = Result.success(emptyList())
    override suspend fun skuExists(sku: String): Result<Boolean> = Result.success(false)
    override suspend fun getById(id: String): Result<Variant?> =
        Result.success(Variant(id, "p-1", "c-1", "SKU-$id", Money.ofPounds(100), true))
    override suspend fun deactivate(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun onHand(variantId: String): Result<Int> = Result.success(10)
    override suspend fun findByBarcode(barcode: String): Result<Variant?> = Result.success(null)
    override suspend fun ownBarcodeCount(): Result<Int> = Result.success(1)
}

class FakeSettingsRepository : ISettingsRepository {
    private var settings = ShopSettings(
        shopName = "Keswa Shop",
        shopNameAr = "كسوة",
        labelHost = "127.0.0.1",
        labelPort = 9100,
    )
    override suspend fun get(): Result<ShopSettings> = Result.success(settings)
    override suspend fun save(settings: ShopSettings): Result<Unit> {
        this.settings = settings
        return Result.success(Unit)
    }
}

class FakePrinterTransport : IPrinterTransport {
    val sentPayloads = mutableListOf<ByteArray>()
    override suspend fun open(): Result<Unit> = Result.success(Unit)
    override suspend fun write(bytes: ByteArray): Result<Unit> {
        sentPayloads += bytes
        return Result.success(Unit)
    }
    override suspend fun close() {}
}

class FakeTransportFactory(private val transport: IPrinterTransport = FakePrinterTransport()) : TransportFactory {
    override fun create(host: String, port: Int): IPrinterTransport = transport
}
