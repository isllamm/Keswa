package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.model.StockCountLine
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountLineUiModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Enriches raw [StockCountLine] items with catalogue descriptions and SKUs in batch.
 *
 * Eliminates N+1 database queries when refreshing count screen by resolving distinct variant IDs
 * concurrently using [supervisorScope]. Expected quantities remain strictly governed by the domain/schema
 * (null until posted).
 */
class EnrichCountLinesUseCase(
    private val find: FindStockItemUseCase,
) {
    private data class ItemInfo(
        val sku: String,
        val description: String,
        val descriptionAr: String,
    )

    suspend operator fun invoke(
        lines: List<StockCountLine>,
        locationId: String?,
    ): List<CountLineUiModel> {
        if (lines.isEmpty()) return emptyList()

        val distinctVariantIds = lines.map { it.variantId }.distinct()

        val itemMap = supervisorScope {
            distinctVariantIds.map { variantId ->
                async {
                    val item = locationId?.let { find.byVariantId(variantId, it).getOrNull() }
                    variantId to ItemInfo(
                        sku = item?.sku ?: variantId,
                        description = item?.description.orEmpty(),
                        descriptionAr = item?.descriptionAr.orEmpty(),
                    )
                }
            }.awaitAll().toMap()
        }

        return lines.map { line ->
            val info = itemMap[line.variantId]
            CountLineUiModel(
                lineId = line.id,
                variantId = line.variantId,
                sku = info?.sku ?: line.variantId,
                description = info?.description.orEmpty(),
                descriptionAr = info?.descriptionAr.orEmpty(),
                counted = line.counted,
                expected = line.expected,
                variance = line.variance,
            )
        }
    }
}
