package keswa.data.catalog

import keswa.data.db.KeswaDatabase
import keswa.domain.auth.StoreContext
import keswa.domain.catalog.StockLevelRepository
import keswa.domain.catalog.StockLevelRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SqlStockLevelRepository(
    private val database: KeswaDatabase,
    private val storeContext: StoreContext,
    private val ioDispatcher: CoroutineDispatcher,
) : StockLevelRepository {

    override suspend fun onHand(query: String): List<StockLevelRow> = withContext(ioDispatcher) {
        database.catalogStockLevelQueries.selectOnHand(storeContext.tenantId(), query).executeAsList()
            .map { StockLevelRow(it.variantId, it.sku, it.productName, it.nameSuffix, it.qtyOnHand, it.avgCostMinor) }
    }
}
