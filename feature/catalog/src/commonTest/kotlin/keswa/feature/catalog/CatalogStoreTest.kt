package keswa.feature.catalog

import keswa.core.common.AppResult
import keswa.domain.InMemoryPrincipalHolder
import keswa.domain.Principal
import keswa.domain.catalog.CreateProductWithVariants
import keswa.domain.catalog.NewProductRequest
import keswa.domain.catalog.OptionValueInput
import keswa.domain.catalog.Product
import keswa.domain.catalog.ProductRepository
import keswa.domain.catalog.ProductVariant
import keswa.domain.catalog.ProductWithVariants
import keswa.domain.catalog.ResolvedNewProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val principal = Principal("u1", "t1", "s1", "d1", "OWNER", emptySet())

private class FakeProducts : ProductRepository {
    val existing = mutableListOf<ProductWithVariants>()
    var lastCreateInput: ResolvedNewProduct? = null
    var failNextCreateWith: keswa.core.common.AppError? = null

    override suspend fun search(query: String) =
        existing.filter { query.isBlank() || it.product.nameAr.contains(query) }

    override suspend fun nextProductCode() = "PRD0001"
    override suspend fun isSkuTaken(sku: String) = false
    override suspend fun isBarcodeTaken(code: String) = false

    override suspend fun create(principal: Principal, input: ResolvedNewProduct): AppResult<ProductWithVariants> {
        lastCreateInput = input
        failNextCreateWith?.let { return AppResult.Err(it) }
        val created = ProductWithVariants(
            Product("p1", input.code, input.nameAr, input.nameEn, null, null, true, input.taxRateBp),
            input.variants.map { ProductVariant("v-${it.sku}", "p1", it.sku, it.nameSuffix, it.costMinor, input.currencyCode, true) },
        )
        existing += created
        return AppResult.Ok(created)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogStoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun harness(): Pair<CatalogStore, FakeProducts> {
        val products = FakeProducts()
        val principalHolder = InMemoryPrincipalHolder().apply { set(principal) }
        val store = CatalogStore(products, CreateProductWithVariants(products), principalHolder)
        return store to products
    }

    @Test
    fun `screen entered loads the product list`() = runTest(testDispatcher) {
        val (store, _) = harness()
        store.dispatch(CatalogContract.Intent.ScreenEntered)
        testScheduler.advanceUntilIdle()
        assertTrue(!store.state.value.isLoadingList)
    }

    @Test
    fun `generating variants from size and colour values produces the cross product`() = runTest(testDispatcher) {
        val (store, _) = harness()
        store.dispatch(CatalogContract.Intent.SizeValuesChanged("S,M"))
        store.dispatch(CatalogContract.Intent.ColorValuesChanged("Red,Blue"))
        store.dispatch(CatalogContract.Intent.GenerateVariantsClicked)
        testScheduler.advanceUntilIdle()

        assertEquals(4, store.state.value.variantRows.size)
    }

    @Test
    fun `generate with no option values still produces one default variant`() = runTest(testDispatcher) {
        val (store, _) = harness()
        store.dispatch(CatalogContract.Intent.GenerateVariantsClicked)
        testScheduler.advanceUntilIdle()
        assertEquals(1, store.state.value.variantRows.size)
        assertEquals("", store.state.value.variantRows.single().nameSuffix)
    }

    @Test
    fun `editing a variant row only changes that row`() = runTest(testDispatcher) {
        val (store, _) = harness()
        store.dispatch(CatalogContract.Intent.SizeValuesChanged("S,M,L"))
        store.dispatch(CatalogContract.Intent.GenerateVariantsClicked)
        testScheduler.advanceUntilIdle()

        store.dispatch(CatalogContract.Intent.VariantCostChanged(1, "25.50"))
        store.dispatch(CatalogContract.Intent.VariantQtyChanged(1, "10"))
        testScheduler.advanceUntilIdle()

        val rows = store.state.value.variantRows
        assertEquals("", rows[0].costText)
        assertEquals("25.50", rows[1].costText)
        assertEquals("10", rows[1].qtyText)
        assertEquals("", rows[2].costText)
    }

    @Test
    fun `saving a valid product resets to the list view with the new product visible`() = runTest(testDispatcher) {
        val (store, products) = harness()
        store.dispatch(CatalogContract.Intent.NameArChanged("قميص"))
        store.dispatch(CatalogContract.Intent.SizeValuesChanged("S,M"))
        store.dispatch(CatalogContract.Intent.GenerateVariantsClicked)
        store.dispatch(CatalogContract.Intent.VariantCostChanged(0, "20"))
        store.dispatch(CatalogContract.Intent.VariantCostChanged(1, "20"))
        store.dispatch(CatalogContract.Intent.SaveClicked)
        testScheduler.advanceUntilIdle()

        assertTrue(!store.state.value.isCreating)
        assertTrue(!store.state.value.isSaving)
        assertEquals(1, store.state.value.products.size)
        assertNotNull(products.lastCreateInput)
        assertEquals(2000L, products.lastCreateInput!!.variants[0].costMinor)
    }

    @Test
    fun `a second save while the first is in flight is ignored`() = runTest(testDispatcher) {
        val (store, _) = harness()
        store.dispatch(CatalogContract.Intent.NameArChanged("حذاء"))
        store.dispatch(CatalogContract.Intent.GenerateVariantsClicked)

        store.dispatch(CatalogContract.Intent.SaveClicked)
        store.dispatch(CatalogContract.Intent.SaveClicked) // arrives while the first is still "in flight"
        testScheduler.advanceUntilIdle()

        assertTrue(!store.state.value.isSaving)
    }

    @Test
    fun `a save failure surfaces the error and keeps the form open`() = runTest(testDispatcher) {
        val (store, products) = harness()
        products.failNextCreateWith = keswa.domain.catalog.CatalogError.DuplicateBarcode
        store.dispatch(CatalogContract.Intent.AddProductClicked)
        store.dispatch(CatalogContract.Intent.NameArChanged("بنطلون"))
        store.dispatch(CatalogContract.Intent.GenerateVariantsClicked)
        testScheduler.advanceUntilIdle()
        store.dispatch(CatalogContract.Intent.SaveClicked)
        testScheduler.advanceUntilIdle()

        assertTrue(!store.state.value.isSaving)
        assertTrue(store.state.value.isCreating)
        assertNotNull(store.state.value.error)
    }

    @Test
    fun `cancel returns to the list without saving`() = runTest(testDispatcher) {
        val (store, products) = harness()
        store.dispatch(CatalogContract.Intent.AddProductClicked)
        store.dispatch(CatalogContract.Intent.NameArChanged("جاكيت"))
        store.dispatch(CatalogContract.Intent.CancelCreateClicked)

        assertTrue(!store.state.value.isCreating)
        assertNull(products.lastCreateInput)
    }
}
