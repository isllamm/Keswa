package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository

/**
 * Gives a fresh install something to work with. Idempotent, so it runs at every startup.
 *
 * Four things, each of which the app is unusable without:
 *
 * - **Colours**, because colour is the only variant axis — without one, no product can be given
 *   a SKU and the catalogue is not merely empty but unusable.
 * - **A root category**, so the first product has somewhere to go.
 * - **A location**, because `stock_movement.locationId` is `RESTRICT` — the first sale would fail
 *   on a foreign key without it.
 * - **A retail price list**, because a till cannot ring up a variant that has no price.
 *
 * The last two arrived with the sell flow in Phase 5: the tables had existed since v1 and nothing
 * had ever needed a row in them.
 */
class SeedShopUseCase(
    private val colours: IColourRepository,
    private val categories: ICategoryRepository,
    private val locations: ILocationRepository,
    private val prices: IPriceRepository,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        if (colours.getAll().getOrThrow().isEmpty()) {
            DEFAULT_COLOURS.forEachIndexed { index, (id, names) ->
                val (name, nameAr, hex) = names
                colours.create(id, name, nameAr, hex, sortOrder = index).getOrThrow()
            }
        }
        if (categories.getTree().getOrThrow().isEmpty()) {
            categories.create(
                id = "cat-uncategorised",
                parentId = null,
                name = "Uncategorised",
                nameAr = "غير مصنّف",
            ).getOrThrow()
        }
        locations.ensureDefault(DEFAULT_LOCATION_ID, "Shop", "المحل").getOrThrow()
        prices.ensureDefaultList(DEFAULT_PRICE_LIST_ID, "Retail", "التجزئة").getOrThrow()
    }

    private companion object {
        // Fixed ids, like the colours: a re-run cannot duplicate them, and two installs agree when
        // sync lands in Phase 9.
        const val DEFAULT_LOCATION_ID = "loc-shop"
        const val DEFAULT_PRICE_LIST_ID = "pricelist-retail"

        // Fixed ids so a re-run cannot duplicate them, and so two installs agree when sync lands.
        val DEFAULT_COLOURS = listOf(
            "colour-white" to Triple("White", "أبيض", "#f2f2ef"),
            "colour-black" to Triple("Black", "أسود", "#1a1a1a"),
            "colour-navy" to Triple("Navy", "كحلي", "#20304f"),
            "colour-blue" to Triple("Blue", "أزرق", "#3e6fa8"),
            "colour-red" to Triple("Red", "أحمر", "#b3322f"),
            "colour-green" to Triple("Green", "أخضر", "#3f7a53"),
            "colour-beige" to Triple("Beige", "بيج", "#cfbfa4"),
        )
    }
}
