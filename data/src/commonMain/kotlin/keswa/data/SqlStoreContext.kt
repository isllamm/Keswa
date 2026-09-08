package keswa.data

import keswa.data.db.KeswaDatabase
import keswa.domain.auth.StoreContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** One store per install through Phase 4 — see docs/data-model.md §3. */
class SqlStoreContext(
    private val database: KeswaDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : StoreContext {

    override suspend fun tenantId(): String = withContext(ioDispatcher) {
        database.tenantQueries.selectFirst().executeAsOne().id
    }

    override suspend fun storeId(): String = withContext(ioDispatcher) {
        database.storeQueries.selectFirst().executeAsOne().id
    }
}
