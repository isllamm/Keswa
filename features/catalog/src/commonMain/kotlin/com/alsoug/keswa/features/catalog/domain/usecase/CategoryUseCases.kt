package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Creates a category, optionally beneath [parentId].
 *
 * The admin owns this tree entirely — there is no fixed taxonomy to fit into.
 */
class CreateCategoryUseCase(
    private val repository: ICategoryRepository,
    private val ids: IdGenerator,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(
        parentId: String?,
        name: String,
        nameAr: String,
    ): Result<Category> = runCatchingCancellable {
        sessions.require(Permission.MANAGE_CATALOGUE)
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "a category needs a name" }
        repository.create(
            id = ids.newId(),
            parentId = parentId,
            name = trimmed,
            nameAr = nameAr.trim().ifEmpty { trimmed },
        ).getOrThrow()
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
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(categoryId: String, newParentId: String?): Result<Unit> =
        runCatchingCancellable {
            sessions.require(Permission.MANAGE_CATALOGUE)
            repository.move(categoryId, newParentId).getOrThrow()
        }
}

class RenameCategoryUseCase(
    private val repository: ICategoryRepository,
    private val sessions: ISessionManager,
) {
    /** A blank English name is refused; a blank Arabic name falls back to the English, as create does. */
    suspend operator fun invoke(categoryId: String, name: String, nameAr: String): Result<Category> =
        runCatchingCancellable {
            sessions.require(Permission.MANAGE_CATALOGUE)
            val trimmed = name.trim()
            require(trimmed.isNotEmpty()) { "a category needs a name" }
            repository.rename(
                id = categoryId,
                name = trimmed,
                nameAr = nameAr.trim().ifEmpty { trimmed },
            ).getOrThrow()
        }
}

class SetCategoryActiveUseCase(
    private val repository: ICategoryRepository,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(categoryId: String, isActive: Boolean): Result<Unit> =
        runCatchingCancellable {
            sessions.require(Permission.MANAGE_CATALOGUE)
            repository.setActive(categoryId, isActive).getOrThrow()
        }
}

class GetCategoryTreeUseCase(
    private val repository: ICategoryRepository,
) {
    suspend operator fun invoke(): Result<List<Category>> = repository.getTree()
}
