package com.alsoug.keswa.features.catalog.presentation.model

import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.core.domain.model.Product
import com.alsoug.keswa.core.domain.model.Variant

/**
 * A category flattened for display, carrying the depth the tree indents by.
 *
 * The tree is stored as a materialised path, not as nested objects, so flattening happens here
 * rather than in the domain — the shape is a rendering concern.
 */
data class CategoryNodeUiModel(
    val id: String,
    val label: String,
    val depth: Int,
    val hasChildren: Boolean,
)

data class ProductUiModel(
    val id: String,
    val label: String,
    val colourCount: Int,
)

data class ColourRowUiModel(
    val variantId: String,
    val colourId: String,
    val label: String,
    val hex: String,
    val sku: String,
    val barcode: String?,
    val onHand: Int,
)

/** Arabic when the app is in Arabic, English otherwise — chosen once, at the UI boundary. */
fun Category.toUiModel(arabic: Boolean, hasChildren: Boolean) = CategoryNodeUiModel(
    id = id,
    label = if (arabic) nameAr else name,
    depth = depth,
    hasChildren = hasChildren,
)

fun Product.toUiModel(arabic: Boolean, colourCount: Int) = ProductUiModel(
    id = id,
    label = if (arabic) nameAr else name,
    colourCount = colourCount,
)

fun Variant.toUiModel(colour: Colour, arabic: Boolean, barcode: String?, onHand: Int) =
    ColourRowUiModel(
        variantId = id,
        colourId = colour.id,
        label = if (arabic) colour.nameAr else colour.name,
        hex = colour.hex,
        sku = sku,
        barcode = barcode,
        onHand = onHand,
    )
