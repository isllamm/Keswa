package keswa.data.catalog

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import keswa.core.common.FixedClock
import keswa.core.common.MonotonicUlidFactory
import keswa.data.Seeder
import keswa.data.SqlStoreContext
import keswa.data.SqlUnitOfWork
import keswa.data.db.KeswaDatabase
import keswa.domain.Principal
import keswa.domain.auth.HashedPin
import keswa.domain.auth.PasswordHasher
import keswa.domain.catalog.NewProductRequest
import keswa.domain.catalog.NewProductVariantRequest
import keswa.domain.catalog.OptionValueInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val fakeHasher = object : PasswordHasher {
    override fun hash(plainPin: String) = HashedPin(plainPin, "salt")
    override fun verify(plainPin: String, hash: String, salt: String) = plainPin == hash
}

private class Harness {
    val clock = FixedClock(1_700_000_000_000L)
    val idFactory = MonotonicUlidFactory(clock)
    val database: KeswaDatabase = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { KeswaDatabase.Schema.create(it) }
        .let { KeswaDatabase(it) }
    val storeContext = SqlStoreContext(database, Dispatchers.Unconfined)
    val unitOfWork = SqlUnitOfWork(database, clock, idFactory, Dispatchers.Unconfined)
    val products = SqlProductRepository(database, clock, idFactory, storeContext, unitOfWork, Dispatchers.Unconfined)
    val stockLevels = SqlStockLevelRepository(database, storeContext, Dispatchers.Unconfined)

    private lateinit var cachedPrincipal: Principal

    init {
        Seeder(database, clock, idFactory, fakeHasher).seedIfEmpty("device-1")
    }

    suspend fun principal(): Principal {
        if (!::cachedPrincipal.isInitialized) {
            val tenantId = storeContext.tenantId()
            val storeId = storeContext.storeId()
            val owner = database.appUserQueries.selectActiveByStore(tenantId, storeId).executeAsList().single()
            cachedPrincipal = Principal(owner.id, tenantId, storeId, "device-1", "OWNER", emptySet())
        }
        return cachedPrincipal
    }
}

private fun variant(suffix: String, qty: Int = 0, cost: Long = 500, barcode: String? = null) =
    NewProductVariantRequest(
        optionValues = if (suffix.isEmpty()) emptyList() else listOf(OptionValueInput(suffix)),
        nameSuffix = suffix, costMinor = cost, openingQty = qty, barcodeCode = barcode,
    )

class CatalogIntegrationTest {

    @Test
    fun `creating a product persists it with all its variants and barcodes`() = runTest {
        val h = Harness()
        val principal = h.principal()

        val result = h.products.create(
            principal,
            keswa.domain.catalog.ResolvedNewProduct(
                code = "PRD0001", nameAr = "قميص قطن", nameEn = "Cotton Shirt", categoryId = null, brandId = null,
                taxRateBp = 0, currencyCode = "EGP",
                variants = listOf(
                    keswa.domain.catalog.NewVariantInput(
                        sku = "PRD0001-01", optionValues = listOf(OptionValueInput("S")), nameSuffix = "S",
                        costMinor = 500, openingQty = 10, barcodeCode = "1111111",
                    ),
                    keswa.domain.catalog.NewVariantInput(
                        sku = "PRD0001-02", optionValues = listOf(OptionValueInput("M")), nameSuffix = "M",
                        costMinor = 500, openingQty = 5, barcodeCode = null,
                    ),
                ),
            ),
        )

        assertIs<keswa.core.common.AppResult.Ok<*>>(result)
        val created = (result as keswa.core.common.AppResult.Ok).value
        assertEquals(2, created.variants.size)
        assertEquals("PRD0001-01", created.variants[0].sku)

        val found = h.products.search("قميص")
        assertEquals(1, found.size)
        assertEquals(2, found.single().variants.size)
    }

    @Test
    fun `opening quantity is reflected in stock on hand`() = runTest {
        val h = Harness()
        val principal = h.principal()

        h.products.create(
            principal,
            keswa.domain.catalog.ResolvedNewProduct(
                "PRD0001", "بنطلون", null, null, null, 0, "EGP",
                listOf(keswa.domain.catalog.NewVariantInput("PRD0001-01", listOf(OptionValueInput("32")), "32", 2000, 7, null)),
            ),
        )

        val onHand = h.stockLevels.onHand("")
        assertEquals(1, onHand.size)
        assertEquals(7L, onHand.single().qtyOnHand)
        assertEquals(2000L, onHand.single().avgCostMinor)
    }

    @Test
    fun `a variant with zero opening quantity produces no stock level row`() = runTest {
        val h = Harness()
        val principal = h.principal()

        h.products.create(
            principal,
            keswa.domain.catalog.ResolvedNewProduct(
                "PRD0001", "حزام", null, null, null, 0, "EGP",
                listOf(keswa.domain.catalog.NewVariantInput("PRD0001-01", emptyList(), "", 500, 0, null)),
            ),
        )

        assertTrue(h.stockLevels.onHand("").isEmpty())
    }

    @Test
    fun `duplicate sku across two separate create calls is rejected by isSkuTaken`() = runTest {
        val h = Harness()
        val principal = h.principal()
        h.products.create(
            principal,
            keswa.domain.catalog.ResolvedNewProduct(
                "PRD0001", "شنطة", null, null, null, 0, "EGP",
                listOf(keswa.domain.catalog.NewVariantInput("PRD0001-01", emptyList(), "", 500, 0, null)),
            ),
        )
        assertTrue(h.products.isSkuTaken("PRD0001-01"))
        assertTrue(!h.products.isSkuTaken("PRD0001-99"))
    }

    @Test
    fun `next product code increments from the current count`() = runTest {
        val h = Harness()
        val principal = h.principal()
        assertEquals("PRD0001", h.products.nextProductCode())
        h.products.create(
            principal,
            keswa.domain.catalog.ResolvedNewProduct(
                "PRD0001", "منتج", null, null, null, 0, "EGP",
                listOf(keswa.domain.catalog.NewVariantInput("PRD0001-01", emptyList(), "", 500, 0, null)),
            ),
        )
        assertEquals("PRD0002", h.products.nextProductCode())
    }

    @Test
    fun `outbox and audit rows are written for a product creation`() = runTest {
        val h = Harness()
        val principal = h.principal()
        val before = h.database.outboxEntryQueries.countAll().executeAsOne()

        h.products.create(
            principal,
            keswa.domain.catalog.ResolvedNewProduct(
                "PRD0001", "فستان", null, null, null, 0, "EGP",
                listOf(
                    keswa.domain.catalog.NewVariantInput("PRD0001-01", emptyList(), "", 500, 3, "222222"),
                ),
            ),
        )

        val after = h.database.outboxEntryQueries.countAll().executeAsOne()
        // product + variant + barcode + stock_movement + stock_level = 5 new outbox rows
        assertEquals(5L, after - before)

        val audit = h.database.auditEventQueries.selectRecent(principal.tenantId, 10).executeAsList()
        assertTrue(audit.any { it.entity_type == "product" && it.action == "CREATE" })
    }
}
