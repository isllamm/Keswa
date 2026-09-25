package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.model.StockReceiptLine
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceiptLineUiModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Enriches raw [StockReceiptLine] items with catalogue descriptions, SKUs, and barcodes in batch.
 *
 * Eliminates N+1 database queries on UI refreshes by extracting distinct variant IDs,
 * fetching metadata concurrently using [supervisorScope], and assembling models in memory.
 */
class EnrichReceiptLinesUseCase(
    private val find: FindStockItemUseCase,
    private val variants: IVariantRepository,
) {
    private data class VariantMetadata(
        val sku: String,
        val description: String,
        val descriptionAr: String,
        val barcode: String?,
    )

    suspend operator fun invoke(
        lines: List<StockReceiptLine>,
        locationId: String?,
    ): List<ReceiptLineUiModel> {
        if (lines.isEmpty()) return emptyList()

        val distinctVariantIds = lines.map { it.variantId }.distinct()

        val metadataMap = supervisorScope {
            distinctVariantIds.map { variantId ->
                async {
                    val item = locationId?.let { find.byVariantId(variantId, it).getOrNull() }
                    val barcodes = variants.barcodesFor(variantId).getOrNull()
                    val primaryBarcode = barcodes?.firstOrNull { it.isPrimary }?.barcode
                        ?: barcodes?.firstOrNull()?.barcode

                    variantId to VariantMetadata(
                        sku = item?.sku ?: variantId,
                        description = item?.description.orEmpty(),
                        descriptionAr = item?.descriptionAr.orEmpty(),
                        barcode = primaryBarcode,
                    )
                }
            }.awaitAll().toMap()
        }

        return lines.map { line ->
            val meta = metadataMap[line.variantId]
            ReceiptLineUiModel(
                lineId = line.id,
                variantId = line.variantId,
                sku = meta?.sku ?: line.variantId,
                description = meta?.description.orEmpty(),
                descriptionAr = meta?.descriptionAr.orEmpty(),
                quantity = line.quantity,
                unitCost = line.unitCost,
                lineTotal = line.lineTotal,
                barcode = meta?.barcode,
            )
        }
    }
}
