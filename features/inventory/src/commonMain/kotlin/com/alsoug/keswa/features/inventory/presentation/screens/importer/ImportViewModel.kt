package com.alsoug.keswa.features.inventory.presentation.screens.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.features.inventory.domain.usecase.ApplyCatalogueImportUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ImportResult
import com.alsoug.keswa.features.inventory.domain.usecase.ParseCatalogueImportUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Check, then apply — never both in one press.
 *
 * Importing a supplier's spreadsheet creates products, SKUs, prices and stock in one go, and none
 * of that is undoable. Seeing what will happen before it happens is the whole safety mechanism.
 */
class ImportViewModel(
    private val resolveLocation: ResolveStockLocationUseCase,
    private val parse: ParseCatalogueImportUseCase,
    private val apply: ApplyCatalogueImportUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<ImportNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<ImportUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var locationId: String? = null

    fun onEvent(event: ImportUiEvent) {
        when (event) {
            ImportUiEvent.Load -> load()
            is ImportUiEvent.TextChanged ->
                _state.update { it.copy(text = event.value, rows = emptyList(), problems = emptyList()) }
            ImportUiEvent.Check -> check()
            ImportUiEvent.Apply -> applyImport()
            ImportUiEvent.Reset -> _state.update { ImportUiState() }
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            resolveLocation().fold(
                onSuccess = { resolved ->
                    locationId = resolved
                    _state.update { it.copy(isLoading = false) }
                },
                onFailure = { fail(it) },
            )
        }
    }

    /** Pure, so no dispatcher and no coroutine: parsing a spreadsheet touches nothing. */
    private fun check() {
        when (val result = parse(_state.value.text)) {
            is ImportResult.Parsed ->
                _state.update { it.copy(rows = result.rows, problems = emptyList()) }
            is ImportResult.Rejected ->
                _state.update { it.copy(rows = emptyList(), problems = result.problems) }
        }
    }

    private fun applyImport() {
        val location = locationId ?: return
        val rows = _state.value.rows
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isApplying = true) }
            apply(rows, location).fold(
                onSuccess = { summary ->
                    _state.update { it.copy(isApplying = false, summary = summary) }
                    _effect.emit(
                        ImportUiEffect.ShowMessage(
                            "${summary.variantsCreated} SKUs, ${summary.piecesReceived} pieces",
                        ),
                    )
                },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false, isApplying = false) }
        _effect.emit(ImportUiEffect.ShowError(cause.message ?: "Something went wrong"))
    }
}
