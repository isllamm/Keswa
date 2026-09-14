package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.features.catalog.domain.Ean13

/**
 * Produces the next unused in-store EAN-13.
 *
 * The sequence follows the count of codes already printed. Barcodes are never deleted, so the
 * count only grows; the loop covers the case where a code was imported out of band.
 */
class GenerateInternalBarcodeUseCase(
    private val variants: IVariantRepository,
) {
    suspend operator fun invoke(): Result<String> = runCatching {
        var sequence = variants.ownBarcodeCount().getOrThrow().toLong() + 1
        while (true) {
            val candidate = Ean13.inStore(sequence)
            if (variants.findByBarcode(candidate).getOrThrow() == null) return@runCatching candidate
            sequence += 1
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
}

sealed interface AssignBarcodeResult {
    data object Assigned : AssignBarcodeResult
    data object AlreadyInUse : AssignBarcodeResult
    data object NotAnEan13 : AssignBarcodeResult
    data object VariantNotFound : AssignBarcodeResult
}

/**
 * Attaches the barcode already printed on a garment to the variant it identifies.
 *
 * A variant keeps several: the supplier's and the shop's own both scan to the same SKU, which is
 * the whole reason barcodes are their own table rather than a column.
 */
class AssignSupplierBarcodeUseCase(
    private val variants: IVariantRepository,
) {
    suspend operator fun invoke(variantId: String, barcode: String): Result<AssignBarcodeResult> =
        runCatching {
            val cleaned = barcode.trim()
            if (!Ean13.isValid(cleaned)) return@runCatching AssignBarcodeResult.NotAnEan13

            variants.getById(variantId).getOrThrow()
                ?: return@runCatching AssignBarcodeResult.VariantNotFound

            if (variants.findByBarcode(cleaned).getOrThrow() != null) {
                return@runCatching AssignBarcodeResult.AlreadyInUse
            }

            variants.attachBarcode(cleaned, variantId, BarcodeSource.SUPPLIER).getOrThrow()
            AssignBarcodeResult.Assigned
        }
}
