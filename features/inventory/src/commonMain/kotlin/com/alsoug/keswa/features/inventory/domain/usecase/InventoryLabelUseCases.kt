package com.alsoug.keswa.features.inventory.domain.usecase

/**
 * Cohesive facade for variant barcode generation and label/hang-tag printing.
 */
data class InventoryLabelUseCases(
    val ensureBarcode: EnsureVariantBarcodeUseCase,
    val printSingleLabel: PrintSingleVariantLabelUseCase,
    val printHangTags: PrintHangTagsUseCase,
)
