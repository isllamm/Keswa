package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.repository.ICategoryRepository

/** What one level of the picker shows: where you can go down to, and what is filed right here. */
data class CategoryLevel(
    val children: List<Category>,
    val items: List<SellableItem>,
)

class BrowseCategoryUseCase(
    private val categories: ICategoryRepository,
    private val find: FindStockItemUseCase,
) {
    /** [categoryId] null is the top level. It has no items: a product is always filed under a category. */
    suspend operator fun invoke(categoryId: String?, locationId: String): Result<CategoryLevel> =
        runCatchingCancellable {
            val children = categories.children(categoryId).getOrThrow()
            val items = if (categoryId == null) {
                emptyList()
            } else {
                find.inCategory(categoryId, locationId).getOrThrow()
            }
            CategoryLevel(children = children, items = items)
        }
}
