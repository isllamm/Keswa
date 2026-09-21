package com.alsoug.keswa.features.inventory.presentation.screens.adjust

import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.StockMovement

data class AdjustUiState(
    val isLoading: Boolean = false,
    val scanEntry: String = "",
    val item: SellableItem? = null,
    val quantityEntry: String = "",
    val reason: MovementReason = MovementReason.DAMAGE,
    val note: String = "",
    val history: List<StockMovement> = emptyList(),
) {
    /**
     * A reason in words is required, not optional.
     *
     * `DAMAGE` says the category; "three shirts water-damaged in the stockroom" is the fact, and
     * the fact is what makes the ledger an audit trail rather than a list of numbers.
     */
    val canApply: Boolean
        get() = item != null && quantityEntry.toIntOrNull()?.takeIf { it != 0 } != null &&
            note.isNotBlank()
}

sealed interface AdjustUiEvent {
    data object Load : AdjustUiEvent
    data class ScanEntryChanged(val value: String) : AdjustUiEvent
    data class Scanned(val barcode: String) : AdjustUiEvent
    data class QuantityChanged(val value: String) : AdjustUiEvent
    data class ReasonChanged(val reason: MovementReason) : AdjustUiEvent
    data class NoteChanged(val value: String) : AdjustUiEvent
    data object Apply : AdjustUiEvent
    data object Clear : AdjustUiEvent
}

sealed interface AdjustNavigation {
    data object Done : AdjustNavigation
}

sealed interface AdjustUiEffect {
    data class ShowError(val message: String) : AdjustUiEffect
    data class ShowMessage(val message: String) : AdjustUiEffect
}
