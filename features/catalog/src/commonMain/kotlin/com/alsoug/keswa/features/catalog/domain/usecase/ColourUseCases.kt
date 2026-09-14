package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.core.domain.model.Variant
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.features.catalog.domain.Ean13
import com.alsoug.keswa.features.catalog.domain.Sku

sealed interface AddColourResult {
    data class Added(val variant: Variant, val barcode: String) : AddColourResult
    data object AlreadyStocked : AddColourResult
    data object ProductNotFound : AddColourResult
    data object ColourNotFound : AddColourResult
}

/**
 * Gives a product one more colour — which means one more SKU, with its own barcode.
 *
 * The SKU is readable (`OXF-NAV`) because it is what staff read off a tag when a scanner fails.
 * The barcode is a real in-store EAN-13 so any scanner reads it without configuration.
 */
class AddColourToProductUseCase(
    private val products: IProductRepository,
    private val colours: IColourRepository,
    private val variants: IVariantRepository,
    private val generateBarcode: GenerateInternalBarcodeUseCase,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        productId: String,
        colourId: String,
        cost: Money = Money.ZERO,
    ): Result<AddColourResult> = runCatching {
        val product = products.getById(productId).getOrThrow()
            ?: return@runCatching AddColourResult.ProductNotFound
        val colour = colours.getAll().getOrThrow().firstOrNull { it.id == colourId }
            ?: return@runCatching AddColourResult.ColourNotFound

        val existing = variants.forProduct(productId).getOrThrow()
        if (existing.any { it.colourId == colourId }) {
            return@runCatching AddColourResult.AlreadyStocked
        }

        val variant = variants.addColour(
            id = ids.newId(),
            productId = productId,
            colourId = colourId,
            sku = uniqueSku(product.name, colour),
            cost = cost,
        ).getOrThrow()

        val barcode = generateBarcode().getOrThrow()
        variants.attachBarcode(barcode, variant.id, BarcodeSource.OWN, isPrimary = true).getOrThrow()

        AddColourResult.Added(variant, barcode)
    }

    private suspend fun uniqueSku(productName: String, colour: Colour): String {
        val base = Sku.of(productName, colour.name)
        var attempt = 1
        while (true) {
            val candidate = Sku.disambiguate(base, attempt)
            if (!variants.skuExists(candidate).getOrThrow()) return candidate
            attempt += 1
        }
    }
}

sealed interface RemoveColourResult {
    data object Removed : RemoveColourResult
    data class HasStock(val onHand: Int) : RemoveColourResult
    data object NotFound : RemoveColourResult
}

/**
 * Retires a colour from a product.
 *
 * Refuses while stock remains, and never deletes: the ledger references this variant and is
 * append-only, so the row has to outlive the decision to stop selling it. Deactivating keeps the
 * history readable.
 */
class RemoveColourFromProductUseCase(
    private val variants: IVariantRepository,
) {
    suspend operator fun invoke(variantId: String): Result<RemoveColourResult> = runCatching {
        variants.getById(variantId).getOrThrow() ?: return@runCatching RemoveColourResult.NotFound

        val onHand = variants.onHand(variantId).getOrThrow()
        if (onHand != 0) return@runCatching RemoveColourResult.HasStock(onHand)

        variants.deactivate(variantId).getOrThrow()
        RemoveColourResult.Removed
    }
}

class CreateColourUseCase(
    private val colours: IColourRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(name: String, nameAr: String, hex: String): Result<Colour> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("a colour needs a name"))
        }
        return colours.create(
            id = ids.newId(),
            name = trimmed,
            nameAr = nameAr.trim().ifEmpty { trimmed },
            hex = hex,
        )
    }
}
