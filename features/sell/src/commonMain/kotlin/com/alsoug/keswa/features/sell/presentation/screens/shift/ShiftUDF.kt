package com.alsoug.keswa.features.sell.presentation.screens.shift

import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.ZReport

data class ShiftUiState(
    val isLoading: Boolean = false,
    val shift: Shift? = null,
    val countedCash: String = "",
    val note: String = "",
    /**
     * Only ever set **after** a close.
     *
     * Deliberate: a cashier who can see what the drawer should hold counts until it agrees, and the
     * discrepancy that would have told the owner something disappears. Blind counting is the only
     * kind that finds anything.
     */
    val report: ZReport? = null,
) {
    val canClose: Boolean get() = shift != null && countedCash.isNotBlank() && report == null
}

sealed interface ShiftUiEvent {
    data object Load : ShiftUiEvent
    data class CountedCashChanged(val value: String) : ShiftUiEvent
    data class NoteChanged(val value: String) : ShiftUiEvent
    data object Close : ShiftUiEvent
    data object Done : ShiftUiEvent
}

sealed interface ShiftNavigation {
    data object Back : ShiftNavigation
}

sealed interface ShiftUiEffect {
    data class ShowError(val message: String) : ShiftUiEffect
    data class ShowMessage(val message: String) : ShiftUiEffect
}
