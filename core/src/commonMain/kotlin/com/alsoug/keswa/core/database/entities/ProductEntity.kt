package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A style — "Round-neck t-shirt". Carries no stock; its [VariantEntity] rows do.
 */
@Entity(
    tableName = "product",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val categoryId: String,
    val brandId: String?,
    val supplierId: String?,
    val season: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
