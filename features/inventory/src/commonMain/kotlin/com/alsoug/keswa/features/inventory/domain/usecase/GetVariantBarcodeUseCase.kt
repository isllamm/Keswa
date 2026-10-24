package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.repository.IVariantRepository

/**
 * Resolves the primary or first active barcode registered for a variant.
 *
 * Keeps ViewModels pure by preventing direct repository injection (ADR-021).
 */
class GetVariantBarcodeUseCase(
    private val variants: IVariantRepository,
) {
    suspend operator fun invoke(variantId: String): Result<String?> = runCatching {
        val barcodes = variants.barcodesFor(variantId).getOrThrow()
        barcodes.firstOrNull { it.isPrimary }?.barcode ?: barcodes.firstOrNull()?.barcode
    }
}
