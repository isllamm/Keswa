package com.alsoug.keswa.features.inventory.presentation.screens.adjust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.features.inventory.domain.usecase.AdjustStockUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StockHistoryUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdjustViewModel(
    private val resolveLocation: ResolveStockLocationUseCase,
    private val find: FindStockItemUseCase,
    private val adjust: AdjustStockUseCase,
    private val history: StockHistoryUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(AdjustUiState())
    val state: StateFlow<AdjustUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<AdjustNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<AdjustUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var locationId: String? = null

    fun onEvent(event: AdjustUiEvent) {
        when (event) {
            AdjustUiEvent.Load -> load()
            is AdjustUiEvent.ScanEntryChanged -> _state.update { it.copy(scanEntry = event.value) }
            is AdjustUiEvent.Scanned -> scan(event.barcode)
            is AdjustUiEvent.QuantityChanged -> _state.update { it.copy(quantityEntry = event.value) }
            is AdjustUiEvent.ReasonChanged -> _state.update { it.copy(reason = event.reason) }
            is AdjustUiEvent.NoteChanged -> _state.update { it.copy(note = event.value) }
            AdjustUiEvent.Apply -> apply()
            AdjustUiEvent.Clear -> _state.update {
                it.copy(item = null, quantityEntry = "", note = "", history = emptyList())
            }
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

    private fun scan(barcode: String) {
        val location = locationId ?: return
        viewModelScope.launch(dispatchers.io) {
            find.byBarcode(barcode, location).fold(
                onSuccess = { item ->
                    if (item == null) {
                        _effect.emit(AdjustUiEffect.ShowError("Not in the catalogue: $barcode"))
                    } else {
                        // What the ledger already says, so the person adjusting can see whether an
                        // earlier correction already covered this. Read before the update, which
                        // re-runs its block on contention.
                        val movements = history(item.variantId, location).getOrElse { emptyList() }
                        _state.update { it.copy(item = item, scanEntry = "", history = movements) }
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun apply() {
        val location = locationId ?: return
        val current = _state.value
        val item = current.item ?: return
        val quantity = current.quantityEntry.toIntOrNull() ?: return reject("That is not a quantity")

        viewModelScope.launch(dispatchers.io) {
            adjust(item.variantId, location, quantity, current.reason, current.note).fold(
                onSuccess = {
                    // Read before updating, not inside it: `update` re-runs its block on
                    // contention, and these are database calls.
                    val refreshed = find.byVariantId(item.variantId, location).getOrNull()
                    val movements = history(item.variantId, location).getOrElse { emptyList() }
                    _state.update {
                        it.copy(
                            item = refreshed ?: it.item,
                            quantityEntry = "",
                            note = "",
                            history = movements,
                        )
                    }
                    _effect.emit(AdjustUiEffect.ShowMessage("Adjusted by $quantity"))
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun reject(message: String) {
        viewModelScope.launch { _effect.emit(AdjustUiEffect.ShowError(message)) }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false) }
        _effect.emit(AdjustUiEffect.ShowError(cause.message ?: "Something went wrong"))
    }
}
