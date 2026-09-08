package keswa.domain.catalog

import keswa.core.common.AppResult
import keswa.domain.Principal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val principal = Principal("u1", "t1", "s1", "d1", "OWNER", emptySet())

private class FakeProducts(
    private val takenSkus: MutableSet<String> = mutableSetOf(),
    private val takenBarcodes: MutableSet<String> = mutableSetOf(),
) : ProductRepository {
    var lastCreated: ResolvedNewProduct? = null
    var codeCounter = 1

    override suspend fun search(query: String) = emptyList<ProductWithVariants>()
    override suspend fun nextProductCode() = "PRD${(codeCounter++).toString().padStart(3, '0')}"
    override suspend fun isSkuTaken(sku: String) = sku in takenSkus
    override suspend fun isBarcodeTaken(code: String) = code in takenBarcodes

    override suspend fun create(principal: Principal, input: ResolvedNewProduct): AppResult<ProductWithVariants> {
        lastCreated = input
        return AppResult.Ok(
            ProductWithVariants(
                product = Product(
                    id = "prod-1", code = input.code, nameAr = input.nameAr, nameEn = input.nameEn,
                    categoryId = input.categoryId, brandId = input.brandId, isActive = true,
                    taxRateBp = input.taxRateBp,
                ),
                variants = emptyList(),
            ),
        )
    }
}

private fun variantRequest(nameSuffix: String, barcode: String? = null) = NewProductVariantRequest(
    optionValues = emptyList(), nameSuffix = nameSuffix, costMinor = 1000, openingQty = 0, barcodeCode = barcode,
)

class CreateProductWithVariantsTest {

    @Test
    fun `blank name is rejected`() = runTest {
        val useCase = CreateProductWithVariants(FakeProducts())
        val result = useCase(
            principal,
            NewProductRequest("PRD001", "  ", null, null, null, 0, "EGP", listOf(variantRequest("S"))),
        )
        assertEquals(AppResult.Err(CatalogError.NameRequired), result)
    }

    @Test
    fun `no variants is rejected`() = runTest {
        val useCase = CreateProductWithVariants(FakeProducts())
        val result = useCase(principal, NewProductRequest(null, "قميص", null, null, null, 0, "EGP", emptyList()))
        assertEquals(AppResult.Err(CatalogError.NoVariants), result)
    }

    @Test
    fun `duplicate barcodes within the same request are rejected`() = runTest {
        val useCase = CreateProductWithVariants(FakeProducts())
        val result = useCase(
            principal,
            NewProductRequest(
                null, "قميص", null, null, null, 0, "EGP",
                listOf(variantRequest("S", "111"), variantRequest("M", "111")),
            ),
        )
        assertEquals(AppResult.Err(CatalogError.DuplicateBarcode), result)
    }

    @Test
    fun `a barcode already used by another variant is rejected`() = runTest {
        val products = FakeProducts(takenBarcodes = mutableSetOf("999"))
        val useCase = CreateProductWithVariants(products)
        val result = useCase(
            principal,
            NewProductRequest(null, "قميص", null, null, null, 0, "EGP", listOf(variantRequest("S", "999"))),
        )
        assertEquals(AppResult.Err(CatalogError.DuplicateBarcode), result)
    }

    @Test
    fun `auto-generates a product code and sequential skus when none is given`() = runTest {
        val products = FakeProducts()
        val useCase = CreateProductWithVariants(products)
        val result = useCase(
            principal,
            NewProductRequest(
                code = null, nameAr = "قميص", nameEn = "Shirt", categoryId = null, brandId = null,
                taxRateBp = 0, currencyCode = "EGP",
                variants = listOf(variantRequest("S"), variantRequest("M"), variantRequest("L")),
            ),
        )
        assertIs<AppResult.Ok<ProductWithVariants>>(result)
        val created = products.lastCreated!!
        assertEquals("PRD001", created.code)
        assertEquals(listOf("PRD001-01", "PRD001-02", "PRD001-03"), created.variants.map { it.sku })
    }

    @Test
    fun `a sku collision with an existing variant is rejected`() = runTest {
        val products = FakeProducts(takenSkus = mutableSetOf("PRD001-02"))
        val useCase = CreateProductWithVariants(products)
        val result = useCase(
            principal,
            NewProductRequest(
                "PRD001", "قميص", null, null, null, 0, "EGP",
                listOf(variantRequest("S"), variantRequest("M")),
            ),
        )
        assertEquals(AppResult.Err(CatalogError.DuplicateSku), result)
    }

    @Test
    fun `blank optional fields are normalized to null`() = runTest {
        val products = FakeProducts()
        val useCase = CreateProductWithVariants(products)
        useCase(
            principal,
            NewProductRequest("PRD001", "  قميص  ", "  ", null, null, 0, "EGP", listOf(variantRequest("S", "  "))),
        )
        val created = products.lastCreated!!
        assertEquals("قميص", created.nameAr)
        assertEquals(null, created.nameEn)
        assertEquals(null, created.variants.single().barcodeCode)
    }
}
