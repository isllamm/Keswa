package keswa.data.catalog

import keswa.core.common.Clock
import keswa.core.common.MonotonicUlidFactory
import keswa.data.db.KeswaDatabase
import keswa.domain.auth.StoreContext
import keswa.domain.catalog.Brand
import keswa.domain.catalog.BrandRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import migrations.Brand as BrandRow

class SqlBrandRepository(
    private val database: KeswaDatabase,
    private val clock: Clock,
    private val idFactory: MonotonicUlidFactory,
    private val deviceId: String,
    private val storeContext: StoreContext,
    private val ioDispatcher: CoroutineDispatcher,
) : BrandRepository {

    override suspend fun listAll(): List<Brand> = withContext(ioDispatcher) {
        database.brandQueries.selectAll(storeContext.tenantId()).executeAsList().map { it.toDomain() }
    }

    override suspend fun create(nameAr: String, nameEn: String?): Brand = withContext(ioDispatcher) {
        val id = idFactory.next()
        val now = clock.nowEpochMillis()
        database.brandQueries.insert(id, storeContext.tenantId(), nameAr, nameEn, now, now, deviceId)
        Brand(id, nameAr, nameEn)
    }
}

private fun BrandRow.toDomain() = Brand(id, name_ar, name_en)
