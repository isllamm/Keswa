package com.alsoug.keswa.features.inventory.presentation.screens.count

import com.alsoug.keswa.core.domain.model.StockCount

/**
 * A counted line as the screen shows it.
 *
 * [expected] and [variance] are null while the count is open, and the screen has no other source
 * for them — which is what makes the count blind in practice rather than in principle.
 */
data class CountLineUiModel(
    val lineId: String,
    val variantId: String,
    val sku: String,
    val description: String,
    val counted: Int,
    val expected: Int? = null,
    val variance: Int? = null,
)

data class CountUiState(
    val isLoading: Boolean = false,
    val isPosting: Boolean = false,
    val count: StockCount? = null,
    val lines: List<CountLineUiModel> = emptyList(),
    val scanEntry: String = "",
    val pendingSku: String? = null,
    val pendingVariantId: String? = null,
    val countedEntry: String = "",
    val note: String = "",
) {
    val isOpen: Boolean get() = count?.isOpen == true

    val isPosted: Boolean get() = count != null && !count.isOpen

    val canPost: Boolean get() = isOpen && lines.isNotEmpty() && !isPosting

    val discrepancies: List<CountLineUiModel> get() = lines.filter { (it.variance ?: 0) != 0 }
}

sealed interface CountUiEvent {
    data object Load : CountUiEvent
    data object Start : CountUiEvent
    data class ScanEntryChanged(val value: String) : CountUiEvent
    data class Scanned(val barcode: String) : CountUiEvent
    data class CountedChanged(val value: String) : CountUiEvent
    data object ConfirmLine : CountUiEvent
    data object CancelLine : CountUiEvent
    data class NoteChanged(val value: String) : CountUiEvent
    data object Post : CountUiEvent
    data object Discard : CountUiEvent
}

sealed interface CountNavigation {
    data object Done : CountNavigation
}

sealed interface CountUiEffect {
    data class ShowError(val message: String) : CountUiEffect
    data class ShowMessage(val message: String) : CountUiEffect
}
