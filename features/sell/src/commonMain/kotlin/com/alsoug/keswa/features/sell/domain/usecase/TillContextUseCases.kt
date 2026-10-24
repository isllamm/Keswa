package com.alsoug.keswa.features.sell.domain.usecase

import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository

/**
 * Where this till sells from and which prices it sells at.
 *
 * Resolved once and carried, rather than looked up per scan: it cannot change mid-sale, and
 * threading two ids through every call is how they end up disagreeing.
 */
data class TillContext(
    val locationId: String,
    val priceListId: String,
)

class ResolveTillContextUseCase(
    private val locations: ILocationRepository,
    private val prices: IPriceRepository,
) {
    suspend operator fun invoke(): Result<TillContext> = runCatching {
        val location = requireNotNull(locations.default().getOrThrow()) {
            "no default location — a fresh install seeds one at startup"
        }
        val priceList = requireNotNull(prices.defaultList().getOrThrow()) {
            "no default price list — a fresh install seeds one at startup"
        }
        TillContext(location.id, priceList.id)
    }
}

/** Why a scan did not put anything in the cart. The till says which; it never guesses. */
sealed interface LookupResult {
    data class Found(val item: SellableItem) : LookupResult
    data object NotFound : LookupResult
    data class NotPriced(val item: SellableItem) : LookupResult
}

/**
 * Resolves a scan, or a typed SKU or name, to something sellable.
 *
 * An unknown barcode is **refused, not invented**. Creating a catalogue entry at the till with a
 * queue waiting produces exactly the junk — "item", "t-shirt 2", no category — that nobody goes
 * back and cleans up, and it needs `MANAGE_CATALOGUE`, which the person scanning does not have.
 * Stock acquires its barcode when it arrives, in Phase 6.
 */
class FindSellableUseCase(
    private val sellables: ISellableRepository,
    private val now: () -> Long,
) {
    suspend fun byBarcode(barcode: String, context: TillContext): Result<LookupResult> =
        runCatching {
            classify(
                sellables.byBarcode(barcode, context.priceListId, context.locationId, now())
                    .getOrThrow(),
            )
        }

    suspend fun search(term: String, context: TillContext): Result<List<SellableItem>> =
        sellables.search(term, context.priceListId, context.locationId, now())

    suspend fun refresh(variantId: String, context: TillContext): Result<LookupResult> =
        runCatching {
            classify(
                sellables.byVariantId(variantId, context.priceListId, context.locationId, now())
                    .getOrThrow(),
            )
        }

    private fun classify(item: SellableItem?): LookupResult = when {
        item == null -> LookupResult.NotFound
        // Ringing up a zero because nobody set a price is worse than refusing the line.
        !item.isPriced -> LookupResult.NotPriced(item)
        else -> LookupResult.Found(item)
    }
}
