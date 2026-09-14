package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Product
import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository

class CreateProductUseCase(
    private val products: IProductRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        name: String,
        nameAr: String,
        categoryId: String,
    ): Result<Product> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("a product needs a name"))
        }
        return products.create(
            id = ids.newId(),
            name = trimmed,
            nameAr = nameAr.trim().ifEmpty { trimmed },
            categoryId = categoryId,
        )
    }
}

/**
 * Every product filed under [categoryId], including its sub-categories.
 *
 * The rollup is the whole reason the category path exists: selecting "T-shirts" must return what
 * is filed under "Round neck" too, or the tree is decoration.
 */
class GetProductsInCategoryUseCase(
    private val categories: ICategoryRepository,
    private val products: IProductRepository,
) {
    suspend operator fun invoke(categoryId: String): Result<List<Product>> {
        val path = categories.pathOf(categoryId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalArgumentException("category not found: $categoryId"))
        return products.inCategoryTree(path)
    }
}

class SearchCatalogUseCase(
    private val products: IProductRepository,
) {
    /** Matches either language, so an Arabic-speaking cashier and an English catalogue agree. */
    suspend operator fun invoke(query: String, withinPath: String = "/"): Result<List<Product>> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return Result.success(emptyList())
        return products.inCategoryTree(withinPath).map { found ->
            found.filter {
                it.name.lowercase().contains(needle) || it.nameAr.contains(query.trim())
            }
        }
    }
}
