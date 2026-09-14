package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stock-keeping unit: a product in one colour. Colour is the only variant axis (KD-001 era
 * decision, 14 Sep) — stock, barcodes and price all hang off this row, never off the product.
 *
 * `onDelete = RESTRICT` throughout: a variant with ledger history must not be deletable, because
 * CASCADE here would silently destroy stock movements. Deactivate instead.
 */
@Entity(
    tableName = "variant",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ColourEntity::class,
            parentColumns = ["id"],
            childColumns = ["colourId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("productId"),
        Index("colourId"),
        Index(value = ["sku"], unique = true),
        Index(value = ["productId", "colourId"], unique = true),
    ],
)
data class VariantEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val colourId: String,
    val sku: String,
    val costPiastres: Long,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
