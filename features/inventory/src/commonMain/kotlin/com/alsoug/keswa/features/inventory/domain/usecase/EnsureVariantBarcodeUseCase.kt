package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.barcode.Ean13
import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.core.domain.repository.IVariantRepository

/**
 * Ensures that a variant has a primary EAN-13 barcode registered.
 *
 * If the variant already has a primary barcode, it is returned directly. If it lacks one, an
 * in-store EAN-13 barcode is auto-generated and attached as [BarcodeSource.STORE_GENERATED].
 */
class EnsureVariantBarcodeUseCase(
    private val variants: IVariantRepository,
) {
    suspend operator fun invoke(variantId: String): Result<String> = runCatching {
        val existingBarcodes = variants.barcodesFor(variantId).getOrThrow()
        val primary = existingBarcodes.firstOrNull { it.isPrimary }?.barcode
            ?: existingBarcodes.firstOrNull()?.barcode

        if (primary != null) {
            return@runCatching primary
        }

        var sequence = variants.ownBarcodeCount().getOrThrow().toLong() + 1
        var generatedBarcode: String
        while (true) {
            val candidate = Ean13.inStore(sequence)
            if (variants.findByBarcode(candidate).getOrThrow() == null) {
                generatedBarcode = candidate
                break
            }
            sequence += 1
        }

        variants.attachBarcode(
            barcode = generatedBarcode,
            variantId = variantId,
            source = BarcodeSource.OWN,
            isPrimary = true,
        ).getOrThrow()

        generatedBarcode
    }
}
