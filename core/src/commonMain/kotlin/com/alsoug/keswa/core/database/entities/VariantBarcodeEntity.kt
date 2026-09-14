package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.BarcodeSource

/**
 * A barcode that resolves to a variant.
 *
 * Its own table rather than a column on `variant`, because a garment routinely carries the
 * supplier's EAN-13 *and* the shop's own printed code, and both must scan to the same variant.
 * The barcode is the primary key, so a scan is an O(1) lookup on the till's hot path.
 */
@Entity(
    tableName = "variant_barcode",
    foreignKeys = [
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("variantId")],
)
data class VariantBarcodeEntity(
    @PrimaryKey val barcode: String,
    val variantId: String,
    val isPrimary: Boolean,
    val source: BarcodeSource,
    val createdAt: Long,
)
