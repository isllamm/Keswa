package keswa.domain.catalog

import keswa.core.common.AppError
import keswa.core.common.ErrorKey

data class Category(val id: String, val nameAr: String, val nameEn: String?, val parentId: String?)

data class Brand(val id: String, val nameAr: String, val nameEn: String?)

data class Product(
    val id: String,
    val code: String,
    val nameAr: String,
    val nameEn: String?,
    val categoryId: String?,
    val brandId: String?,
    val isActive: Boolean,
    val taxRateBp: Int,
)

data class ProductVariant(
    val id: String,
    val productId: String,
    val sku: String,
    val nameSuffix: String?,
    val costMinor: Long,
    val currencyCode: String,
    val isActive: Boolean,
    val barcodes: List<Barcode> = emptyList(),
)

data class Barcode(val id: String, val variantId: String, val code: String, val isPrimary: Boolean)

/** A product with its full variant matrix — what a catalog screen actually needs to render. */
data class ProductWithVariants(val product: Product, val variants: List<ProductVariant>)

sealed class CatalogError(override val key: ErrorKey) : AppError {
    data class TooManyVariants(val count: Int) : CatalogError(ErrorKey("error.catalog.too_many_variants"))
    data object DuplicateCode : CatalogError(ErrorKey("error.catalog.duplicate_code"))
    data object DuplicateSku : CatalogError(ErrorKey("error.catalog.duplicate_sku"))
    data object DuplicateBarcode : CatalogError(ErrorKey("error.catalog.duplicate_barcode"))
    data object NameRequired : CatalogError(ErrorKey("error.catalog.name_required"))
    data object NoVariants : CatalogError(ErrorKey("error.catalog.no_variants"))
}
