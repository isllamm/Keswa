package keswa.domain.catalog

import keswa.core.common.AppResult
import keswa.domain.Principal

interface CategoryRepository {
    suspend fun listAll(): List<Category>
    suspend fun create(nameAr: String, nameEn: String?): Category
}

interface BrandRepository {
    suspend fun listAll(): List<Brand>
    suspend fun create(nameAr: String, nameEn: String?): Brand
}

data class NewVariantInput(
    val sku: String,
    val optionValues: List<OptionValueInput>,
    val nameSuffix: String,
    val costMinor: Long,
    val openingQty: Int,
    val barcodeCode: String?,
)

data class ResolvedNewProduct(
    val code: String,
    val nameAr: String,
    val nameEn: String?,
    val categoryId: String?,
    val brandId: String?,
    val taxRateBp: Int,
    val currencyCode: String,
    val variants: List<NewVariantInput>,
)

interface ProductRepository {
    suspend fun search(query: String): List<ProductWithVariants>
    suspend fun nextProductCode(): String
    suspend fun isSkuTaken(sku: String): Boolean
    suspend fun isBarcodeTaken(code: String): Boolean
    suspend fun create(principal: Principal, input: ResolvedNewProduct): AppResult<ProductWithVariants>
}

interface StockLevelRepository {
    suspend fun onHand(query: String): List<StockLevelRow>
}

data class StockLevelRow(
    val variantId: String,
    val sku: String,
    val productName: String,
    val nameSuffix: String?,
    val qtyOnHand: Long,
    val avgCostMinor: Long,
)
