package com.alsoug.keswa.features.inventory.presentation.screens.importer

import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.features.inventory.domain.usecase.ImportProblem
import com.alsoug.keswa.features.inventory.domain.usecase.ImportRow
import com.alsoug.keswa.features.inventory.domain.usecase.ImportSummary

data class ImportUiState(
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val text: String = "",
    /** Populated only by a clean parse — a file with one bad row produces problems, not rows. */
    val rows: List<ImportRow> = emptyList(),
    val problems: List<ImportProblem> = emptyList(),
    val summary: ImportSummary? = null,
) {
    val hasParsed: Boolean get() = rows.isNotEmpty()

    val canApply: Boolean get() = hasParsed && problems.isEmpty() && !isApplying && summary == null

    val pieceCount: Int get() = rows.sumOf { it.quantity }
}

sealed interface ImportUiEvent {
    data object Load : ImportUiEvent
    data class TextChanged(val value: String) : ImportUiEvent
    data object Check : ImportUiEvent
    data object Apply : ImportUiEvent
    data object Reset : ImportUiEvent
}

sealed interface ImportNavigation {
    data object Done : ImportNavigation
}

sealed interface ImportUiEffect {
    data class ShowError(val message: Message) : ImportUiEffect
    data class ShowMessage(val message: Message) : ImportUiEffect
}
