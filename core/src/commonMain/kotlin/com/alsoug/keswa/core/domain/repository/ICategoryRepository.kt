package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.Category

/**
 * The admin's category tree.
 *
 * Ids are supplied by the caller rather than generated here: they are client-generated UUIDs so a
 * later sync can upsert idempotently (`cashi_pax` defect F2), and it keeps the tree deterministic
 * under test.
 */
interface ICategoryRepository {

    suspend fun create(
        id: String,
        parentId: String?,
        name: String,
        nameAr: String,
        sortOrder: Int = 0,
    ): Result<Category>

    /**
     * Re-parents [categoryId], rewriting the materialised path and depth of the whole subtree.
     *
     * Fails if [newParentId] is the category itself or one of its descendants — that would make the
     * tree cyclic, and every recursive read would hang.
     */
    suspend fun move(categoryId: String, newParentId: String?): Result<Unit>

    suspend fun getTree(): Result<List<Category>>

    suspend fun getById(id: String): Result<Category?>

    /** [Category.path] of [id], for prefix queries such as "every product beneath this node". */
    suspend fun pathOf(id: String): Result<String?>
}
