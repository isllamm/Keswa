package com.alsoug.keswa.features.inventory.presentation.screens.count

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.designsystem.message
import com.alsoug.keswa.core.domain.model.StockCount
import com.alsoug.keswa.features.inventory.domain.model.InventoryError
import com.alsoug.keswa.features.inventory.domain.usecase.EnrichCountLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StockCountUseCases
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds a blind count.
 *
 * There is nothing here that reads an expected quantity while the count is open, because there is
 * no way to ask for one — the column is null until posting. That is deliberate: the discipline is
 * in the schema, not in this class remembering to be careful.
 */
class CountViewModel(
    private val resolveLocation: ResolveStockLocationUseCase,
    private val find: FindStockItemUseCase,
    private val counts: StockCountUseCases,
    private val enrichLines: EnrichCountLinesUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CountUiState())
    val state: StateFlow<CountUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<CountNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<CountUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var locationId: String? = null

    fun onEvent(event: CountUiEvent) {
        when (event) {
            CountUiEvent.Load -> load()
            CountUiEvent.Start -> start()
            is CountUiEvent.ScanEntryChanged -> _state.update { it.copy(scanEntry = event.value) }
            is CountUiEvent.Scanned -> scan(event.barcode)
            is CountUiEvent.CountedChanged -> _state.update { it.copy(countedEntry = event.value) }
            CountUiEvent.ConfirmLine -> confirmLine()
            CountUiEvent.CancelLine -> _state.update {
                it.copy(pendingSku = null, pendingVariantId = null, countedEntry = "")
            }
            is CountUiEvent.NoteChanged -> _state.update { it.copy(note = event.value) }
            CountUiEvent.Post -> post()
            CountUiEvent.Discard -> discard()
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            resolveLocation().fold(
                onSuccess = { resolved ->
                    locationId = resolved
                    val open = counts.current(resolved).getOrNull()
                    _state.update { it.copy(isLoading = false) }
                    open?.let { show(it) }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun start() {
        val location = locationId ?: return
        viewModelScope.launch(dispatchers.io) {
            counts.start(location).fold(onSuccess = { show(it) }, onFailure = { fail(it) })
        }
    }

    private fun scan(barcode: String) {
        val location = locationId ?: return
        if (_state.value.count == null) return reject(message { it.startACountFirst })

        viewModelScope.launch(dispatchers.io) {
            find.byBarcode(barcode, location).fold(
                onSuccess = { item ->
                    if (item == null) {
                        _effect.emit(CountUiEffect.ShowError(message { it.notInCatalogue(barcode) }))
                    } else {
                        _state.update {
                            it.copy(
                                pendingSku = item.sku,
                                pendingVariantId = item.variantId,
                                scanEntry = "",
                                // Empty, not pre-filled: a count that starts from a number is not
                                // a count.
                                countedEntry = "",
                            )
                        }
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun confirmLine() {
        val countId = _state.value.count?.id ?: return
        val variantId = _state.value.pendingVariantId ?: return
        val counted = _state.value.countedEntry.toIntOrNull()
            ?: return reject(message { it.notAQuantity })

        viewModelScope.launch(dispatchers.io) {
            counts.countVariant(countId, variantId, counted).fold(
                onSuccess = { count ->
                    show(count)
                    _state.update {
                        it.copy(pendingSku = null, pendingVariantId = null, countedEntry = "")
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun post() {
        val countId = _state.value.count?.id ?: return
        val note = _state.value.note
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isPosting = true) }
            counts.post(countId, note).fold(
                onSuccess = { count ->
                    show(count)
                    _state.update { it.copy(isPosting = false) }
                    val off = count.discrepancies.size
                    _effect.emit(
                        CountUiEffect.ShowMessage(
                            message { if (off == 0) it.everythingMatched else it.linesDidNotMatch(off) },
                        ),
                    )
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun discard() {
        val countId = _state.value.count?.id ?: return
        viewModelScope.launch(dispatchers.io) {
            counts.discard(countId).fold(
                onSuccess = {
                    _state.update { it.copy(count = null, lines = emptyList()) }
                    _navigation.emit(CountNavigation.Done)
                },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun show(count: StockCount) {
        val lines = enrichLines(count.lines, locationId)
        _state.update { it.copy(count = count, lines = lines) }
    }

    private fun reject(message: Message) {
        viewModelScope.launch { _effect.emit(CountUiEffect.ShowError(message)) }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false, isPosting = false) }
        val msg = when (cause) {
            is InventoryError -> message { cause.resolveMessage(it) }
            else -> message { it.somethingWentWrong }
        }
        _effect.emit(CountUiEffect.ShowError(msg))
    }
}
