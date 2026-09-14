package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A node in the admin-defined category tree — "T-shirts", with "Round neck" beneath it.
 *
 * [path] is a materialised path (`/tshirts/roundneck/`) so "everything under T-shirts, including
 * sub-categories" is an indexed prefix query rather than a recursive one. It drives the catalogue
 * tree, the category filter and every analytics rollup, so it is maintained on every write.
 */
@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("parentId"), Index("path")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val nameAr: String,
    val path: String,
    val depth: Int,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
