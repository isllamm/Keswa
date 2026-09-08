package keswa.data.catalog

import keswa.core.common.AppResult
import keswa.core.common.Clock
import keswa.core.common.CommonError
import keswa.core.common.MonotonicUlidFactory
import keswa.core.common.ArabicText
import keswa.data.OutboxOp
import keswa.data.UnitOfWork
import keswa.data.db.KeswaDatabase
import keswa.domain.Principal
import keswa.domain.auth.StoreContext
import keswa.domain.catalog.Barcode
import keswa.domain.catalog.Product
import keswa.domain.catalog.ProductRepository
import keswa.domain.catalog.ProductVariant
import keswa.domain.catalog.ProductWithVariants
import keswa.domain.catalog.ResolvedNewProduct
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import migrations.Product as ProductRow
import migrations.Product_variant as VariantRow

/**
 * Persists the whole product + variant matrix in one [UnitOfWork] transaction — product, each
 * variant, each barcode, and (if given) each variant's opening stock movement + level, so a
 * partial failure never leaves half a product behind. See docs/architecture.md §4.
 *
 * ASSUMPTION: `product_option`/`product_option_value`/`variant_option_value` are left unpopulated
 * for now — nothing in Phase 0 queries "all size-M variants" yet. The human-readable combo lives
 * on `product_variant.name_suffix`. Populating the normalized tables is additive later.
 */
class SqlProductRepository(
    private val database: KeswaDatabase,
    private val clock: Clock,
    private val idFactory: MonotonicUlidFactory,
    private val storeContext: StoreContext,
    private val unitOfWork: UnitOfWork,
    private val ioDispatcher: CoroutineDispatcher,
) : ProductRepository {

    override suspend fun search(query: String): List<ProductWithVariants> = withContext(ioDispatcher) {
        val tenantId = storeContext.tenantId()
        database.productQueries.searchActive(tenantId, query).executeAsList().map { product ->
            val variants = database.productVariantQueries.selectByProduct(tenantId, product.id).executeAsList()
                .map { variant ->
                    val barcodes = database.catalogBarcodeQueries.selectByVariant(tenantId, variant.id)
                        .executeAsList().map { Barcode(it.id, it.variant_id, it.code, it.is_primary) }
                    variant.toDomain(barcodes)
                }
            ProductWithVariants(product.toDomain(), variants)
        }
    }

    override suspend fun nextProductCode(): String = withContext(ioDispatcher) {
        val count = database.productQueries.countAll(storeContext.tenantId()).executeAsOne()
        "PRD${(count + 1).toString().padStart(4, '0')}"
    }

    override suspend fun isSkuTaken(sku: String): Boolean = withContext(ioDispatcher) {
        database.productQueries.selectSkuTaken(storeContext.tenantId(), sku).executeAsOne() > 0
    }

    override suspend fun isBarcodeTaken(code: String): Boolean = withContext(ioDispatcher) {
        database.productQueries.selectBarcodeTaken(storeContext.tenantId(), code).executeAsOne() > 0
    }

    override suspend fun create(principal: Principal, input: ResolvedNewProduct): AppResult<ProductWithVariants> {
        val tenantId = principal.tenantId
        val storeId = principal.storeId

        // Looked up once per call rather than cached at construction time — Phase 0 has one
        // store, so this is a couple of cheap indexed reads, not a hot path.
        val salesFloor = database.locationQueries.selectByKind(tenantId, storeId, "SALES_FLOOR").executeAsOneOrNull()
        val adjustment = database.locationQueries.selectByKind(tenantId, storeId, "ADJUSTMENT").executeAsOneOrNull()
        if (salesFloor == null || adjustment == null) {
            return AppResult.Err(CommonError.Unexpected("required locations missing — reseed the database"))
        }

        return unitOfWork.transaction(principal) {
            val now = clock.nowEpochMillis()
            val productId = idFactory.next()

            database.productQueries.insert(
                productId, tenantId, storeId, input.code, input.nameAr, input.nameEn,
                ArabicText.sortKey(input.nameAr), input.categoryId, input.brandId, input.taxRateBp.toLong(),
                now, now, principal.deviceId,
            )
            recordAudit(
                entityType = "product",
                entityId = productId,
                action = "CREATE",
                summaryJson = """{"code":"${input.code}"}""",
            )
            enqueueOutbox("product", productId, OutboxOp.INSERT, now)

            val variants = input.variants.mapIndexed { index, v ->
                val variantId = idFactory.next()
                database.productVariantQueries.insert(
                    variantId, tenantId, storeId, productId, v.sku, v.nameSuffix.ifBlank { null },
                    v.costMinor, input.currencyCode, index.toLong(), now, now, principal.deviceId,
                )
                enqueueOutbox("product_variant", variantId, OutboxOp.INSERT, now)

                val barcodes = mutableListOf<Barcode>()
                val barcodeCode = v.barcodeCode
                if (barcodeCode != null) {
                    val barcodeId = idFactory.next()
                    database.catalogBarcodeQueries.insert(
                        barcodeId, tenantId, variantId, barcodeCode, "INTERNAL", now, now, principal.deviceId,
                    )
                    enqueueOutbox("barcode", barcodeId, OutboxOp.INSERT, now)
                    barcodes += Barcode(barcodeId, variantId, barcodeCode, isPrimary = true)
                }

                if (v.openingQty > 0) {
                    val movementId = idFactory.next()
                    database.catalogStockMovementQueries.insertOpeningBalance(
                        movementId, tenantId, storeId, now, principal.deviceId,
                        now, variantId, adjustment.id, salesFloor.id,
                        v.openingQty.toLong(), v.costMinor, input.currencyCode,
                        principal.userId,
                    )
                    enqueueOutbox("stock_movement", movementId, OutboxOp.INSERT, now)

                    database.catalogStockLevelQueries.insertInitial(
                        tenantId, storeId, salesFloor.id, variantId, v.openingQty.toLong(), v.costMinor, now,
                    )
                    enqueueOutbox("stock_level", variantId, OutboxOp.INSERT, now)
                }

                ProductVariant(variantId, productId, v.sku, v.nameSuffix, v.costMinor, input.currencyCode, true, barcodes)
            }

            ProductWithVariants(
                Product(productId, input.code, input.nameAr, input.nameEn, input.categoryId, input.brandId, true, input.taxRateBp),
                variants,
            )
        }
    }
}

private fun ProductRow.toDomain() = Product(id, code, name_ar, name_en, category_id, brand_id, is_active, tax_rate_bp.toInt())

private fun VariantRow.toDomain(barcodes: List<Barcode>) =
    ProductVariant(id, product_id, sku, name_suffix, cost_minor, currency_code, is_active, barcodes)
