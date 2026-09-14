package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.PriceListType

/**
 * A named set of prices. A single RETAIL list is seeded at install; adding WHOLESALE later is then
 * a data change rather than a migration (Phase 7, gated on Q1).
 */
@Entity(tableName = "price_list")
data class PriceListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val type: PriceListType,
    val isDefault: Boolean,
    val isActive: Boolean,
)

/**
 * The price of one variant on one list, optionally bounded in time so a seasonal markdown can be
 * scheduled rather than applied by hand.
 */
@Entity(
    tableName = "price",
    foreignKeys = [
        ForeignKey(
            entity = PriceListEntity::class,
            parentColumns = ["id"],
            childColumns = ["priceListId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("variantId", "priceListId"), Index("priceListId")],
)
data class PriceEntity(
    @PrimaryKey val id: String,
    val priceListId: String,
    val variantId: String,
    val pricePiastres: Long,
    val validFrom: Long,
    val validTo: Long?,
)
