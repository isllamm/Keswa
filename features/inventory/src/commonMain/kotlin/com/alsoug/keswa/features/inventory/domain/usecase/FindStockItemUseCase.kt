package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository

/**
 * Resolves a scan or a typed term to something in the catalogue, for the stockroom rather than the
 * till.
 *
 * Unlike the till's version this does **not** care whether the item has a price: stock arrives
 * before anyone decides what to sell it for, and refusing to receive an unpriced garment would
 * make pricing a precondition of unpacking a box.
 */
class FindStockItemUseCase(
    private val sellables: ISellableRepository,
    private val prices: IPriceRepository,
    private val now: () -> Long,
) {
    suspend fun byBarcode(barcode: String, locationId: String): Result<SellableItem?> =
        runCatching {
            sellables.byBarcode(barcode, priceListId(), locationId, now()).getOrThrow()
        }

    suspend fun search(term: String, locationId: String): Result<List<SellableItem>> =
        runCatching {
            sellables.search(term, priceListId(), locationId, now()).getOrThrow()
        }

    suspend fun byVariantId(variantId: String, locationId: String): Result<SellableItem?> =
        runCatching {
            sellables.byVariantId(variantId, priceListId(), locationId, now()).getOrThrow()
        }

    private suspend fun priceListId(): String =
        prices.defaultList().getOrThrow()?.id.orEmpty()
}
