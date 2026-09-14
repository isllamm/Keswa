package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.repository.ICategoryRepository

/**
 * Creates a category, optionally beneath [parentId].
 *
 * The admin owns this tree entirely — there is no fixed taxonomy to fit into.
 */
class CreateCategoryUseCase(
    private val repository: ICategoryRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        parentId: String?,
        name: String,
        nameAr: String,
    ): Result<Category> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("a category needs a name"))
        }
        return repository.create(
            id = ids.newId(),
            parentId = parentId,
            name = trimmed,
            nameAr = nameAr.trim().ifEmpty { trimmed },
        )
    }
}

/**
 * Re-parents a category, taking its whole subtree with it.
 *
 * Delegates the path rewrite and the cycle guard to the repository, where they belong — they are
 * invariants of the stored tree, not of this interaction.
 */
class MoveCategoryUseCase(
    private val repository: ICategoryRepository,
) {
    suspend operator fun invoke(categoryId: String, newParentId: String?): Result<Unit> =
        repository.move(categoryId, newParentId)
}

class GetCategoryTreeUseCase(
    private val repository: ICategoryRepository,
) {
    suspend operator fun invoke(): Result<List<Category>> = repository.getTree()
}
