package com.alsoug.keswa.core.data.mapper

import com.alsoug.keswa.core.database.entities.CategoryEntity
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.LocationEntity
import com.alsoug.keswa.core.database.entities.ProductEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.entities.VariantEntity
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.core.domain.model.Location
import com.alsoug.keswa.core.domain.model.Product
import com.alsoug.keswa.core.domain.model.StockMovement
import com.alsoug.keswa.core.domain.model.Variant
import com.alsoug.keswa.core.domain.money.Money

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    parentId = parentId,
    name = name,
    nameAr = nameAr,
    path = path,
    depth = depth,
    sortOrder = sortOrder,
    isActive = isActive,
)

fun ColourEntity.toDomain(): Colour =
    Colour(id = id, name = name, nameAr = nameAr, hex = hex, sortOrder = sortOrder, isActive = isActive)

fun ProductEntity.toDomain(): Product = Product(
    id = id,
    name = name,
    nameAr = nameAr,
    categoryId = categoryId,
    brandId = brandId,
    supplierId = supplierId,
    season = season,
    isActive = isActive,
)

fun VariantEntity.toDomain(): Variant = Variant(
    id = id,
    productId = productId,
    colourId = colourId,
    sku = sku,
    cost = Money.ofPiastres(costPiastres),
    isActive = isActive,
)

fun StockMovementEntity.toDomain(): StockMovement = StockMovement(
    id = id,
    variantId = variantId,
    locationId = locationId,
    quantity = quantity,
    reason = reason,
    refType = refType,
    refId = refId,
    occurredAt = occurredAt,
    userId = userId,
)

fun StockMovement.toEntity(): StockMovementEntity = StockMovementEntity(
    id = id,
    variantId = variantId,
    locationId = locationId,
    quantity = quantity,
    reason = reason,
    refType = refType,
    refId = refId,
    occurredAt = occurredAt,
    userId = userId,
)

fun LocationEntity.toDomain(): Location =
    Location(id = id, name = name, nameAr = nameAr, type = type, isDefault = isDefault, isActive = isActive)
