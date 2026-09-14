package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.database.dao.CategoryDao
import com.alsoug.keswa.core.database.entities.CategoryEntity
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.repository.ICategoryRepository

class CategoryRepositoryImpl(
    private val dao: CategoryDao,
    private val now: () -> Long,
) : ICategoryRepository {

    override suspend fun create(
        id: String,
        parentId: String?,
        name: String,
        nameAr: String,
        sortOrder: Int,
    ): Result<Category> = runCatchingCancellable {
        val parent = parentId?.let {
            requireNotNull(dao.getById(it)) { "parent category not found: $it" }
        }
        val timestamp = now()
        val entity = CategoryEntity(
            id = id,
            parentId = parentId,
            name = name,
            nameAr = nameAr,
            path = pathFor(parent?.path, id),
            depth = (parent?.depth ?: -1) + 1,
            sortOrder = sortOrder,
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        dao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun move(categoryId: String, newParentId: String?): Result<Unit> =
        runCatchingCancellable {
            val category = requireNotNull(dao.getById(categoryId)) {
                "category not found: $categoryId"
            }
            require(newParentId != categoryId) { "a category cannot be its own parent" }

            val newParent = newParentId?.let {
                requireNotNull(dao.getById(it)) { "parent category not found: $it" }
            }
            // The cycle guard. Without it the tree closes on itself and every path query hangs.
            require(newParent == null || !newParent.path.startsWith(category.path)) {
                "cannot move a category beneath its own descendant"
            }

            val newPath = pathFor(newParent?.path, categoryId)
            val depthDelta = ((newParent?.depth ?: -1) + 1) - category.depth
            val timestamp = now()

            // The whole subtree moves with it — a path is only useful if it stays true of every
            // descendant, so this rewrite is the operation, not a side effect of it.
            val rewritten = dao.getSubtree(category.path).map { node ->
                node.copy(
                    parentId = if (node.id == categoryId) newParentId else node.parentId,
                    path = newPath + node.path.removePrefix(category.path),
                    depth = node.depth + depthDelta,
                    updatedAt = timestamp,
                )
            }
            dao.upsertAll(rewritten)
        }

    override suspend fun getTree(): Result<List<Category>> = runCatchingCancellable {
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun getById(id: String): Result<Category?> = runCatchingCancellable {
        dao.getById(id)?.toDomain()
    }

    override suspend fun pathOf(id: String): Result<String?> = runCatchingCancellable {
        dao.getById(id)?.path
    }

    private fun pathFor(parentPath: String?, id: String): String =
        if (parentPath == null) "/$id/" else "$parentPath$id/"
}
