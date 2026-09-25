package com.alsoug.keswa.features.inventory.presentation.screens.receiving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.designsystem.message
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.domain.model.InventoryError
import com.alsoug.keswa.features.inventory.domain.usecase.EnrichReceiptLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.InventoryLabelUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.LabelRunResult
import com.alsoug.keswa.features.inventory.domain.usecase.ReceivingUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReceivingViewModel(
    private val resolveLocation: ResolveStockLocationUseCase,
    private val find: FindStockItemUseCase,
    private val receiving: ReceivingUseCases,
    private val enrichLines: EnrichReceiptLinesUseCase,
    private val labels: InventoryLabelUseCases,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceivingUiState())
    val state: StateFlow<ReceivingUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<ReceivingNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<ReceivingUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var locationId: String? = null

    fun onEvent(event: ReceivingUiEvent) {
        when (event) {
            ReceivingUiEvent.Load -> load()
            is ReceivingUiEvent.ReferenceChanged -> _state.update { it.copy(reference = event.value) }
            is ReceivingUiEvent.SupplierChanged -> _state.update { it.copy(supplierName = event.value) }
            ReceivingUiEvent.StartReceipt -> start()
            is ReceivingUiEvent.ScanEntryChanged -> _state.update { it.copy(scanEntry = event.value) }
            is ReceivingUiEvent.Scanned -> scan(event.barcode)
            is ReceivingUiEvent.QuantityChanged -> _state.update { it.copy(quantityEntry = event.value) }
            is ReceivingUiEvent.CostChanged -> _state.update { it.copy(costEntry = event.value) }
            ReceivingUiEvent.ConfirmLine -> confirmLine()
            ReceivingUiEvent.CancelLine ->
                _state.update { it.copy(pendingItem = null, quantityEntry = "", costEntry = "") }
            is ReceivingUiEvent.RemoveLine -> remove(event.lineId)
            ReceivingUiEvent.Post -> post()
            ReceivingUiEvent.Discard -> discard()
            ReceivingUiEvent.PrintTags -> print()
            is ReceivingUiEvent.GenerateBarcode -> generateBarcode(event.variantId)
            is ReceivingUiEvent.PrintVariantTag -> printSingleVariant(event.variantId)
            is ReceivingUiEvent.Open -> open(event.receiptId)
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            resolveLocation().fold(
                onSuccess = { resolved ->
                    locationId = resolved
                    // Read before updating: `update` re-runs its block on contention, and this is
                    // a database call.
                    val recent = receiving.recent(resolved).getOrElse { emptyList() }
                    _state.update { it.copy(isLoading = false, recent = recent) }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun start() {
        val location = locationId ?: return
        val current = _state.value
        viewModelScope.launch(dispatchers.io) {
            receiving.start(current.reference, current.supplierName, location).fold(
                onSuccess = { receipt -> show(receipt) },
                onFailure = { fail(it) },
            )
        }
    }

    private fun scan(barcode: String) {
        val location = locationId ?: return
        if (_state.value.receipt == null) {
            return reject(message { it.startADeliveryFirst })
        }
        viewModelScope.launch(dispatchers.io) {
            find.byBarcode(barcode, location).fold(
                onSuccess = { item ->
                    if (item == null) {
                        _effect.emit(ReceivingUiEffect.ShowError(message { it.notInCatalogue(barcode) }))
                    } else {
                        // The last cost is offered as the default, because a repeat order at the
                        // same price is the common case and retyping it is where mistakes come from.
                        _state.update {
                            it.copy(
                                pendingItem = item,
                                scanEntry = "",
                                quantityEntry = "1",
                                costEntry = item.cost.format(),
                            )
                        }
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun confirmLine() {
        val receiptId = _state.value.receipt?.id ?: return
        val item = _state.value.pendingItem ?: return
        val quantity = _state.value.quantityEntry.toIntOrNull()
            ?: return reject(message { it.notAQuantity })
        val cost = Money.parse(_state.value.costEntry) ?: return reject(message { it.notAnAmount })

        viewModelScope.launch(dispatchers.io) {
            receiving.addLine(receiptId, item.variantId, quantity, cost).fold(
                onSuccess = { receipt ->
                    show(receipt)
                    _state.update { it.copy(pendingItem = null, quantityEntry = "", costEntry = "") }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun remove(lineId: String) {
        val receiptId = _state.value.receipt?.id ?: return
        viewModelScope.launch(dispatchers.io) {
            receiving.removeLine(receiptId, lineId).fold(onSuccess = { show(it) }, onFailure = { fail(it) })
        }
    }

    private fun post() {
        val receiptId = _state.value.receipt?.id ?: return
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isPosting = true) }
            receiving.post(receiptId).fold(
                onSuccess = { posted ->
                    show(posted.receipt)
                    val recent = locationId?.let { receiving.recent(it).getOrNull() }
                    _state.update {
                        it.copy(
                            isPosting = false,
                            costChanges = posted.costChanges.filter { change -> change.hasChanged },
                            recent = recent ?: it.recent,
                        )
                    }
                    _effect.emit(
                        ReceivingUiEffect.ShowMessage(message { it.receivedPieces(posted.receipt.pieceCount) }),
                    )
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun discard() {
        val receiptId = _state.value.receipt?.id ?: return
        viewModelScope.launch(dispatchers.io) {
            receiving.discard(receiptId).fold(
                onSuccess = {
                    _state.update {
                        it.copy(receipt = null, lines = emptyList(), costChanges = emptyList())
                    }
                    _navigation.emit(ReceivingNavigation.Done)
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun generateBarcode(variantId: String) {
        viewModelScope.launch(dispatchers.io) {
            labels.ensureBarcode(variantId).fold(
                onSuccess = { code ->
                    _state.value.receipt?.let { show(it) }
                    _effect.emit(ReceivingUiEffect.ShowMessage(message { it.barcodeAttached }))
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun printSingleVariant(variantId: String) {
        viewModelScope.launch(dispatchers.io) {
            labels.printSingleLabel(variantId, 1).fold(
                onSuccess = { result ->
                    when (result) {
                        is LabelRunResult.Printed ->
                            _effect.emit(ReceivingUiEffect.ShowMessage(message { it.tagsSent(result.tags) }))
                        LabelRunResult.NoPrinter ->
                            _effect.emit(ReceivingUiEffect.ShowError(message { it.noLabelPrinterConfigured }))
                        is LabelRunResult.Unreachable ->
                            _effect.emit(ReceivingUiEffect.ShowError(message { it.labelPrinterSilent }))
                        is LabelRunResult.Incomplete -> _effect.emit(
                            ReceivingUiEffect.ShowError(
                                message { it.tagsSentSomeSkipped(result.tags, result.skipped.size) },
                            ),
                        )
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun print() {
        val receiptId = _state.value.receipt?.id ?: return
        viewModelScope.launch(dispatchers.io) {
            labels.printHangTags(receiptId).fold(
                onSuccess = { result ->
                    when (result) {
                        is LabelRunResult.Printed ->
                            _effect.emit(ReceivingUiEffect.ShowMessage(message { it.tagsSent(result.tags) }))
                        LabelRunResult.NoPrinter ->
                            _effect.emit(ReceivingUiEffect.ShowError(message { it.noLabelPrinterConfigured }))
                        is LabelRunResult.Unreachable ->
                            _effect.emit(ReceivingUiEffect.ShowError(message { it.labelPrinterSilent }))
                        // Named rather than skipped silently: a tag with no barcode cannot be
                        // scanned at the till, which is the only reason it exists.
                        is LabelRunResult.Incomplete -> _effect.emit(
                            ReceivingUiEffect.ShowError(
                                message { it.tagsSentSomeSkipped(result.tags, result.skipped.size) },
                            ),
                        )
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun open(receiptId: String) {
        viewModelScope.launch(dispatchers.io) {
            receiving.getById(receiptId).fold(
                onSuccess = { receipt -> receipt?.let { show(it) } },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun show(receipt: StockReceipt) {
        val lines = enrichLines(receipt.lines, locationId)
        _state.update { it.copy(receipt = receipt, lines = lines) }
    }

    private fun reject(message: Message) {
        viewModelScope.launch { _effect.emit(ReceivingUiEffect.ShowError(message)) }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false, isPosting = false) }
        val msg = when (cause) {
            is InventoryError -> message { cause.resolveMessage(it) }
            else -> message { it.somethingWentWrong }
        }
        _effect.emit(ReceivingUiEffect.ShowError(msg))
    }
}
