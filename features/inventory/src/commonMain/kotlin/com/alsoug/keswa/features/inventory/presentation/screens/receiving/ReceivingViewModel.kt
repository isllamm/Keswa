package com.alsoug.keswa.features.inventory.presentation.screens.receiving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.designsystem.message
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.features.inventory.domain.model.InventoryError
import com.alsoug.keswa.features.inventory.domain.usecase.AddLinesResult
import com.alsoug.keswa.features.inventory.domain.usecase.EnrichReceiptLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EntryProblem
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.InventoryLabelUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.LabelRunResult
import com.alsoug.keswa.features.inventory.domain.usecase.PickedEntry
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
            is ReceivingUiEvent.PriceChanged -> _state.update { it.copy(priceEntry = event.value) }
            ReceivingUiEvent.ConfirmLine -> confirmLine()
            ReceivingUiEvent.CancelLine ->
                _state.update {
                    it.copy(pendingItem = null, quantityEntry = "", costEntry = "", priceEntry = "")
                }
            is ReceivingUiEvent.RemoveLine -> remove(event.lineId)
            ReceivingUiEvent.Post -> post()
            ReceivingUiEvent.Discard -> discard()
            ReceivingUiEvent.PrintTags -> print()
            is ReceivingUiEvent.GenerateBarcode -> generateBarcode(event.variantId)
            is ReceivingUiEvent.PrintLineLabels -> printLineLabels(event.lineId, event.copies)
            is ReceivingUiEvent.Open -> open(event.receiptId)
            is ReceivingUiEvent.OpenCategory -> openCategory(event.categoryId)
            is ReceivingUiEvent.GoUpTo -> goUpTo(event.depth)
            is ReceivingUiEvent.PickQuantityChanged ->
                updatePick(event.variantId) { it.copy(quantity = event.value) }
            is ReceivingUiEvent.PickCostChanged ->
                updatePick(event.variantId) { it.copy(cost = event.value) }
            is ReceivingUiEvent.PickPriceChanged ->
                updatePick(event.variantId) { it.copy(price = event.value) }
            ReceivingUiEvent.AddPicked -> addPicked()
            ReceivingUiEvent.ClearPicked ->
                _state.update { it.copy(typed = emptyMap(), problems = emptyMap()) }
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
                onSuccess = { receipt ->
                    show(receipt)
                    browseLevel(categoryId = null, crumbs = emptyList())
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun openCategory(categoryId: String) {
        val current = _state.value
        val cat = current.subCategories.firstOrNull { it.id == categoryId }
        val newCrumbs = if (cat != null) {
            current.crumbs + CategoryCrumb(cat.id, cat.name, cat.nameAr)
        } else {
            current.crumbs
        }
        viewModelScope.launch(dispatchers.io) {
            browseLevel(categoryId = categoryId, crumbs = newCrumbs)
        }
    }

    private fun goUpTo(depth: Int) {
        val newCrumbs = _state.value.crumbs.take(depth.coerceAtLeast(0))
        val targetId = newCrumbs.lastOrNull()?.id
        viewModelScope.launch(dispatchers.io) {
            browseLevel(categoryId = targetId, crumbs = newCrumbs)
        }
    }

    private suspend fun browseLevel(categoryId: String?, crumbs: List<CategoryCrumb>) {
        val location = locationId ?: return
        receiving.browse(categoryId, location).fold(
            onSuccess = { level ->
                _state.update {
                    it.copy(
                        crumbs = crumbs,
                        subCategories = level.children,
                        levelItems = level.items,
                    )
                }
            },
            onFailure = { fail(it) },
        )
    }

    private fun updatePick(variantId: String, transform: (TypedEntry) -> TypedEntry) {
        _state.update { current ->
            val base = current.typed[variantId] ?: seedEntry(current, variantId) ?: return@update current
            val updated = transform(base)
            current.copy(
                typed = current.typed + (variantId to updated),
                problems = current.problems - variantId,
            )
        }
    }

    private fun seedEntry(current: ReceivingUiState, variantId: String): TypedEntry? {
        val item = current.levelItems.firstOrNull { it.variantId == variantId } ?: return null
        val existingLine = current.receipt?.lines?.firstOrNull { it.variantId == variantId }
        return if (existingLine != null) {
            TypedEntry(
                item = item,
                quantity = existingLine.quantity.toString(),
                cost = existingLine.unitCost.format(),
                price = (existingLine.sellPrice ?: item.price)?.format().orEmpty(),
            )
        } else {
            TypedEntry(
                item = item,
                quantity = "",
                cost = item.cost.format(),
                price = item.price?.format().orEmpty(),
            )
        }
    }

    private fun addPicked() {
        val receiptId = _state.value.receipt?.id ?: return
        val entries = _state.value.typed.values.map {
            PickedEntry(
                item = it.item,
                quantity = it.quantity,
                cost = it.cost,
                price = it.price,
            )
        }
        if (entries.isEmpty()) return

        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isAddingLines = true) }
            receiving.addLines(receiptId, entries).fold(
                onSuccess = { result ->
                    when (result) {
                        is AddLinesResult.Added -> {
                            show(result.receipt)
                            _state.update {
                                it.copy(
                                    typed = emptyMap(),
                                    problems = emptyMap(),
                                    isAddingLines = false,
                                )
                            }
                            _effect.emit(
                                ReceivingUiEffect.ShowMessage(message { it.linesAdded(result.lines) }),
                            )
                        }
                        is AddLinesResult.Rejected -> {
                            _state.update {
                                it.copy(
                                    problems = result.problems,
                                    isAddingLines = false,
                                )
                            }
                            _effect.emit(
                                ReceivingUiEffect.ShowError(
                                    message { it.rowsNeedFixing(result.problems.size) },
                                ),
                            )
                        }
                    }
                },
                onFailure = {
                    _state.update { state -> state.copy(isAddingLines = false) }
                    fail(it)
                },
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
                                priceEntry = item.price?.format().orEmpty(),
                            )
                        }
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun confirmLine() {
        val current = _state.value
        val receiptId = current.receipt?.id ?: return
        val item = current.pendingItem ?: return
        val entry = PickedEntry(
            item = item,
            quantity = current.quantityEntry.ifBlank { "0" },
            cost = current.costEntry,
            price = current.priceEntry,
        )

        viewModelScope.launch(dispatchers.io) {
            receiving.addLines(receiptId, listOf(entry)).fold(
                onSuccess = { result ->
                    when (result) {
                        is AddLinesResult.Added -> {
                            show(result.receipt)
                            _state.update {
                                it.copy(
                                    pendingItem = null,
                                    quantityEntry = "",
                                    costEntry = "",
                                    priceEntry = "",
                                )
                            }
                        }
                        is AddLinesResult.Rejected -> {
                            val problem = result.problems.values.firstOrNull()
                            val msg = when (problem) {
                                EntryProblem.NOT_A_QUANTITY -> message { it.notAQuantity }
                                EntryProblem.NOT_AN_AMOUNT -> message { it.notAnAmount }
                                EntryProblem.PRICE_REQUIRED -> message { it.priceRequired }
                                null -> message { it.somethingWentWrong }
                            }
                            _effect.emit(ReceivingUiEffect.ShowError(msg))
                        }
                    }
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
                        it.copy(
                            receipt = null,
                            lines = emptyList(),
                            costChanges = emptyList(),
                            crumbs = emptyList(),
                            subCategories = emptyList(),
                            levelItems = emptyList(),
                            typed = emptyMap(),
                            problems = emptyMap(),
                        )
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
                onSuccess = {
                    _state.value.receipt?.let { show(it) }
                    _effect.emit(ReceivingUiEffect.ShowMessage(message { it.barcodeAttached }))
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun printLineLabels(lineId: String, copies: Int) {
        val receiptId = _state.value.receipt?.id ?: return
        viewModelScope.launch(dispatchers.io) {
            labels.printLineLabels(receiptId, lineId, copies).fold(
                onSuccess = { emitLabelResult(it) },
                onFailure = { fail(it) },
            )
        }
    }

    private fun print() {
        val receiptId = _state.value.receipt?.id ?: return
        viewModelScope.launch(dispatchers.io) {
            labels.printHangTags(receiptId).fold(
                onSuccess = { emitLabelResult(it) },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun emitLabelResult(result: LabelRunResult) {
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
    }

    private fun open(receiptId: String) {
        viewModelScope.launch(dispatchers.io) {
            receiving.getById(receiptId).fold(
                onSuccess = { receipt ->
                    if (receipt != null) {
                        show(receipt)
                        if (receipt.isDraft) {
                            browseLevel(categoryId = null, crumbs = emptyList())
                        }
                    }
                },
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
