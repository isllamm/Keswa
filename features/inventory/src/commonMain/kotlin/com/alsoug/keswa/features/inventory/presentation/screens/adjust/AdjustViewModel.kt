package com.alsoug.keswa.features.inventory.presentation.screens.adjust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.designsystem.message
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

import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.features.inventory.domain.usecase.EnsureVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.LabelRunResult
import com.alsoug.keswa.features.inventory.domain.usecase.PrintSingleVariantLabelUseCase

class AdjustViewModel(
    private val resolveLocation: ResolveStockLocationUseCase,
    private val find: FindStockItemUseCase,
    private val adjust: AdjustStockUseCase,
    private val history: StockHistoryUseCase,
    private val variants: IVariantRepository,
    private val ensureBarcode: EnsureVariantBarcodeUseCase,
    private val printSingleLabel: PrintSingleVariantLabelUseCase,
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
            AdjustUiEvent.GenerateBarcode -> generateBarcode()
            AdjustUiEvent.PrintLabel -> printLabel()
            AdjustUiEvent.Apply -> apply()
            AdjustUiEvent.Clear -> _state.update {
                it.copy(item = null, barcode = null, quantityEntry = "", note = "", history = emptyList())
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
                        _effect.emit(AdjustUiEffect.ShowError(message { it.notInCatalogue(barcode) }))
                    } else {
                        val movements = history(item.variantId, location).getOrElse { emptyList() }
                        val existingBarcode = variants.barcodesFor(item.variantId).getOrNull()
                            ?.firstOrNull { it.isPrimary }?.barcode
                            ?: variants.barcodesFor(item.variantId).getOrNull()?.firstOrNull()?.barcode
                        _state.update {
                            it.copy(item = item, barcode = existingBarcode, scanEntry = "", history = movements)
                        }
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun generateBarcode() {
        val variantId = _state.value.item?.variantId ?: return
        viewModelScope.launch(dispatchers.io) {
            ensureBarcode(variantId).fold(
                onSuccess = { code ->
                    _state.update { it.copy(barcode = code) }
                    _effect.emit(AdjustUiEffect.ShowMessage(message { it.barcodeAttached }))
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun printLabel() {
        val variantId = _state.value.item?.variantId ?: return
        viewModelScope.launch(dispatchers.io) {
            printSingleLabel(variantId, 1).fold(
                onSuccess = { result ->
                    when (result) {
                        is LabelRunResult.Printed ->
                            _effect.emit(AdjustUiEffect.ShowMessage(message { it.tagsSent(result.tags) }))
                        LabelRunResult.NoPrinter ->
                            _effect.emit(AdjustUiEffect.ShowError(message { it.noLabelPrinterConfigured }))
                        is LabelRunResult.Unreachable ->
                            _effect.emit(AdjustUiEffect.ShowError(message { it.labelPrinterSilent }))
                        is LabelRunResult.Incomplete -> _effect.emit(
                            AdjustUiEffect.ShowError(
                                message { it.tagsSentSomeSkipped(result.tags, result.skipped.size) },
                            ),
                        )
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
        val quantity = current.quantityEntry.toIntOrNull() ?: return reject(message { it.notAQuantity })

        viewModelScope.launch(dispatchers.io) {
            adjust(item.variantId, location, quantity, current.reason, current.note).fold(
                onSuccess = {
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
                    _effect.emit(AdjustUiEffect.ShowMessage(message { it.adjustedBy(quantity) }))
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun reject(message: Message) {
        viewModelScope.launch { _effect.emit(AdjustUiEffect.ShowError(message)) }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false) }
        _effect.emit(AdjustUiEffect.ShowError(message { it.somethingWentWrong }))
    }
}
