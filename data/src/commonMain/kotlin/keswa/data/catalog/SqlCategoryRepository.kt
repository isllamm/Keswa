package keswa.data.catalog

import keswa.core.common.Clock
import keswa.core.common.MonotonicUlidFactory
import keswa.data.db.KeswaDatabase
import keswa.domain.auth.StoreContext
import keswa.domain.catalog.Category
import keswa.domain.catalog.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import migrations.Category as CategoryRow

class SqlCategoryRepository(
    private val database: KeswaDatabase,
    private val clock: Clock,
    private val idFactory: MonotonicUlidFactory,
    private val deviceId: String,
    private val storeContext: StoreContext,
    private val ioDispatcher: CoroutineDispatcher,
) : CategoryRepository {

    override suspend fun listAll(): List<Category> = withContext(ioDispatcher) {
        database.categoryQueries.selectAll(storeContext.tenantId()).executeAsList().map { it.toDomain() }
    }

    override suspend fun create(nameAr: String, nameEn: String?): Category = withContext(ioDispatcher) {
        val id = idFactory.next()
        val now = clock.nowEpochMillis()
        database.categoryQueries.insert(id, storeContext.tenantId(), nameAr, nameEn, now, now, deviceId)
        Category(id, nameAr, nameEn, null)
    }
}

private fun CategoryRow.toDomain() = Category(id, name_ar, name_en, parent_id)
