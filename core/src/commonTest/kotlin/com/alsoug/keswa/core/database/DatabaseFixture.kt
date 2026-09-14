package com.alsoug.keswa.core.database

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.LocationEntity
import com.alsoug.keswa.core.database.entities.ProductEntity
import com.alsoug.keswa.core.database.entities.VariantEntity
import com.alsoug.keswa.core.domain.model.LocationType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Room needs a dispatcher that actually runs work, so these tests use a real one rather than
 * [com.alsoug.keswa.core.coroutines.TestDispatcherProvider] — virtual time would stall the driver.
 */
internal object RealDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Default
    override val default: CoroutineDispatcher = Dispatchers.Default
}

/**
 * An in-memory database built through the production factory, so these tests exercise the real
 * configuration — the foreign-key pragma included.
 */
fun createTestDatabase(): KeswaDatabase =
    getKeswaDatabase(Room.inMemoryDatabaseBuilder<KeswaDatabase>(), RealDispatchers)

const val SHOP_ID = "loc-shop"
const val CAT_TSHIRTS = "cat-tshirts"
const val COLOUR_NAVY = "col-navy"
const val PRODUCT_TEE = "prod-tee"
const val VARIANT_TEE_NAVY = "var-tee-navy"

/** Seeds the minimum a stock movement needs to exist: location, category, colour, product, variant. */
suspend fun KeswaDatabase.seedBaseData() {
    locationDao().upsert(
        LocationEntity(SHOP_ID, "Downtown", "وسط البلد", LocationType.SHOP, isDefault = true, isActive = true),
    )
    categoryDao().upsert(
        com.alsoug.keswa.core.database.entities.CategoryEntity(
            id = CAT_TSHIRTS,
            parentId = null,
            name = "T-shirts",
            nameAr = "تيشيرتات",
            path = "/$CAT_TSHIRTS/",
            depth = 0,
            sortOrder = 0,
            isActive = true,
            createdAt = 0,
            updatedAt = 0,
        ),
    )
    colourDao().upsert(ColourEntity(COLOUR_NAVY, "Navy", "كحلي", "#20304f", 0, isActive = true))
    productDao().upsert(
        ProductEntity(PRODUCT_TEE, "Round-neck t-shirt", "تيشيرت رقبة دائرية", CAT_TSHIRTS, null, null, null, true, 0, 0),
    )
    variantDao().insert(
        VariantEntity(VARIANT_TEE_NAVY, PRODUCT_TEE, COLOUR_NAVY, "KSW-TSH-022-NV", 12_000, true, 0, 0),
    )
}
