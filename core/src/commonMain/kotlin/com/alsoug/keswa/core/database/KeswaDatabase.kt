package com.alsoug.keswa.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.alsoug.keswa.core.database.dao.CategoryDao
import com.alsoug.keswa.core.database.dao.ColourDao
import com.alsoug.keswa.core.database.dao.HeldSaleDao
import com.alsoug.keswa.core.database.dao.LocationDao
import com.alsoug.keswa.core.database.dao.PriceDao
import com.alsoug.keswa.core.database.dao.ProductDao
import com.alsoug.keswa.core.database.dao.SaleDao
import com.alsoug.keswa.core.database.dao.SaleReturnDao
import com.alsoug.keswa.core.database.dao.SellableDao
import com.alsoug.keswa.core.database.dao.SettingDao
import com.alsoug.keswa.core.database.dao.StockCountDao
import com.alsoug.keswa.core.database.dao.StockReceiptDao
import com.alsoug.keswa.core.database.dao.ShiftDao
import com.alsoug.keswa.core.database.dao.UserDao
import com.alsoug.keswa.core.database.dao.StockLedgerDao
import com.alsoug.keswa.core.database.dao.VariantBarcodeDao
import com.alsoug.keswa.core.database.dao.VariantDao
import com.alsoug.keswa.core.database.entities.AppSettingEntity
import com.alsoug.keswa.core.database.entities.AppUserEntity
import com.alsoug.keswa.core.database.entities.CategoryEntity
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.HeldSaleEntity
import com.alsoug.keswa.core.database.entities.HeldSaleLineEntity
import com.alsoug.keswa.core.database.entities.LocationEntity
import com.alsoug.keswa.core.database.entities.PaymentEntity
import com.alsoug.keswa.core.database.entities.PriceEntity
import com.alsoug.keswa.core.database.entities.PriceListEntity
import com.alsoug.keswa.core.database.entities.ProductEntity
import com.alsoug.keswa.core.database.entities.SaleEntity
import com.alsoug.keswa.core.database.entities.SaleLineEntity
import com.alsoug.keswa.core.database.entities.SaleReturnEntity
import com.alsoug.keswa.core.database.entities.SaleReturnLineEntity
import com.alsoug.keswa.core.database.entities.ShiftEntity
import com.alsoug.keswa.core.database.entities.StockCountEntity
import com.alsoug.keswa.core.database.entities.StockCountLineEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.entities.StockReceiptEntity
import com.alsoug.keswa.core.database.entities.StockReceiptLineEntity
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
        AppSettingEntity::class,
        AppUserEntity::class,
        SaleEntity::class,
        SaleLineEntity::class,
        PaymentEntity::class,
        ShiftEntity::class,
        HeldSaleEntity::class,
        HeldSaleLineEntity::class,
        StockReceiptEntity::class,
        StockReceiptLineEntity::class,
        StockCountEntity::class,
        StockCountLineEntity::class,
        SaleReturnEntity::class,
        SaleReturnLineEntity::class,
    ],
    version = 6, // 6: added sale_return/_line
    // 5: added stock_receipt/_line, stock_count/_line; stock_movement gained cost and note
    // 4: added sale, sale_line, payment, shift, held_sale, held_sale_line
    // 3: added app_user (sign-in, roles, lockout)
    // 2: added app_setting (printer and scanner configuration)
    // 1: initial schema — category tree, catalogue, stock ledger, pricing
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
    abstract fun settingDao(): SettingDao
    abstract fun userDao(): UserDao
    abstract fun saleDao(): SaleDao
    abstract fun shiftDao(): ShiftDao
    abstract fun heldSaleDao(): HeldSaleDao
    abstract fun sellableDao(): SellableDao
    abstract fun stockReceiptDao(): StockReceiptDao
    abstract fun stockCountDao(): StockCountDao
    abstract fun saleReturnDao(): SaleReturnDao
}

/**
 * Actual implementations are generated per target by the Room compiler.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object KeswaDatabaseConstructor : RoomDatabaseConstructor<KeswaDatabase> {
    override fun initialize(): KeswaDatabase
}
