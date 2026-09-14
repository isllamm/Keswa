package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.IColourRepository

/**
 * Gives a fresh install something to work with.
 *
 * A brand-new database has no colours, and a colour is the only variant axis — so without this the
 * catalogue is not merely empty, it is unusable: no product can be given a SKU. Idempotent, so it
 * is safe to run at every startup.
 */
class SeedCatalogUseCase(
    private val colours: IColourRepository,
    private val categories: ICategoryRepository,
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
    }

    private companion object {
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
