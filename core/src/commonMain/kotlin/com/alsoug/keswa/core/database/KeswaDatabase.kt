package com.alsoug.keswa.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.alsoug.keswa.core.database.dao.CategoryDao
import com.alsoug.keswa.core.database.dao.ColourDao
import com.alsoug.keswa.core.database.dao.LocationDao
import com.alsoug.keswa.core.database.dao.PriceDao
import com.alsoug.keswa.core.database.dao.ProductDao
import com.alsoug.keswa.core.database.dao.StockLedgerDao
import com.alsoug.keswa.core.database.dao.VariantBarcodeDao
import com.alsoug.keswa.core.database.dao.VariantDao
import com.alsoug.keswa.core.database.entities.CategoryEntity
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.LocationEntity
import com.alsoug.keswa.core.database.entities.PriceEntity
import com.alsoug.keswa.core.database.entities.PriceListEntity
import com.alsoug.keswa.core.database.entities.ProductEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.entities.StockOnHandEntity
import com.alsoug.keswa.core.database.entities.VariantBarcodeEntity
import com.alsoug.keswa.core.database.entities.VariantEntity

/**
 * The shop's database — and, until sync ships in Phase 9, the only copy of its sales history.
 *
 * That is why KD-002 forbids `fallbackToDestructiveMigration`: there is no server to re-fetch from,
 * so a dropped table is permanent data loss. Every version bump ships a tested [androidx.room.migration.Migration].
 */
@Database(
    entities = [
        CategoryEntity::class,
        ColourEntity::class,
        LocationEntity::class,
        ProductEntity::class,
        VariantEntity::class,
        VariantBarcodeEntity::class,
        StockMovementEntity::class,
        StockOnHandEntity::class,
        PriceListEntity::class,
        PriceEntity::class,
    ],
    version = 1, // 1: initial schema — category tree, catalogue, stock ledger, pricing
    exportSchema = true,
)
@ConstructedBy(KeswaDatabaseConstructor::class)
abstract class KeswaDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun colourDao(): ColourDao
    abstract fun locationDao(): LocationDao
    abstract fun productDao(): ProductDao
    abstract fun variantDao(): VariantDao
    abstract fun variantBarcodeDao(): VariantBarcodeDao
    abstract fun stockLedgerDao(): StockLedgerDao
    abstract fun priceDao(): PriceDao
}

/**
 * Actual implementations are generated per target by the Room compiler.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object KeswaDatabaseConstructor : RoomDatabaseConstructor<KeswaDatabase> {
    override fun initialize(): KeswaDatabase
}
