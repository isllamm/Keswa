package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * A node in the admin's category tree.
 *
 * [path] is the materialised path; [isDescendantOf] is the check that keeps a move from creating a
 * cycle, which would hang every recursive read of the tree.
 */
data class Category(
    val id: String,
    val parentId: String?,
    val name: String,
    val nameAr: String,
    val path: String,
    val depth: Int,
    val sortOrder: Int,
    val isActive: Boolean,
) {
    val isRoot: Boolean get() = parentId == null

    fun isDescendantOf(other: Category): Boolean =
        id != other.id && path.startsWith(other.path)
}

data class Colour(
    val id: String,
    val name: String,
    val nameAr: String,
    val hex: String,
    val sortOrder: Int,
    val isActive: Boolean,
)

/** A style. Carries no stock — its [Variant]s do. */
data class Product(
    val id: String,
    val name: String,
    val nameAr: String,
    val categoryId: String,
    val brandId: String?,
    val supplierId: String?,
    val season: String?,
    val isActive: Boolean,
)

/** A product in one colour: the SKU that carries stock, barcodes and price. */
data class Variant(
    val id: String,
    val productId: String,
    val colourId: String,
    val sku: String,
    val cost: Money,
    val isActive: Boolean,
)

data class Barcode(
    val barcode: String,
    val variantId: String,
    val isPrimary: Boolean,
    val source: BarcodeSource,
)
