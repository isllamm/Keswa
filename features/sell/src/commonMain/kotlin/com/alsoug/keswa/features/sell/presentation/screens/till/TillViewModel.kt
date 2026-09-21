package com.alsoug.keswa.features.sell.presentation.screens.till

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.model.BasketTotals
import com.alsoug.keswa.features.sell.domain.usecase.CalculateBasketTotalUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CompleteSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CurrentShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.DiscardHeldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.FindSellableUseCase
import com.alsoug.keswa.features.sell.domain.usecase.HoldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ListHeldSalesUseCase
import com.alsoug.keswa.features.sell.domain.usecase.LookupResult
import com.alsoug.keswa.features.sell.domain.usecase.OpenShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.PrintReceiptUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ReauthResult
import com.alsoug.keswa.features.sell.domain.usecase.ReauthenticateUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ReceiptResult
import com.alsoug.keswa.features.sell.domain.usecase.ResolveTillContextUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ResumeHeldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.SaleResult
import com.alsoug.keswa.features.sell.domain.usecase.SaleWarning
import com.alsoug.keswa.features.sell.domain.usecase.Tender
import com.alsoug.keswa.features.sell.domain.usecase.TillContext
import com.alsoug.keswa.features.sell.domain.usecase.VoidSaleUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Overwrites a credential in place, so it is not left sitting in the heap until collection. */
private fun CharArray.wipe() = fill(Char.MIN_VALUE)

/**
 * Holds the cart and delegates (ADR-021).
 *
 * Every money figure on this screen comes from [CalculateBasketTotalUseCase]; nothing here adds up
 * a column. The basket itself is a domain object, so the same rules apply however a line arrived.
 */
class TillViewModel(
    private val resolveContext: ResolveTillContextUseCase,
    private val find: FindSellableUseCase,
    private val calculate: CalculateBasketTotalUseCase,
    private val completeSale: CompleteSaleUseCase,
    private val printReceipt: PrintReceiptUseCase,
    private val reauthenticate: ReauthenticateUseCase,
    private val voidSale: VoidSaleUseCase,
    private val holdSale: HoldSaleUseCase,
    private val resumeHeld: ResumeHeldSaleUseCase,
    private val listHeld: ListHeldSalesUseCase,
    private val discardHeld: DiscardHeldSaleUseCase,
    private val currentShift: CurrentShiftUseCase,
    private val openShift: OpenShiftUseCase,
    private val settings: ISettingsRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(TillUiState())
    val state: StateFlow<TillUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<TillNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<TillUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private var till: TillContext? = null

    fun onEvent(event: TillUiEvent) {
        when (event) {
            TillUiEvent.Load -> load()
            is TillUiEvent.Scanned -> scan(event.barcode)
            is TillUiEvent.QueryChanged -> _state.update { it.copy(query = event.value) }
            TillUiEvent.Search -> search()
            is TillUiEvent.PickResult -> addByVariant(event.variantId)
            is TillUiEvent.QuantityChanged -> mutate { it.withQuantity(event.lineIndex, event.quantity) }
            is TillUiEvent.RemoveLine -> mutate { it.removeAt(event.lineIndex) }
            is TillUiEvent.RequestLineDiscount -> requestLineDiscount(event.lineIndex, event.amount)
            is TillUiEvent.RequestPriceOverride -> requestPriceOverride(event.lineIndex, event.price)
            is TillUiEvent.RequestOrderDiscount -> requestOrderDiscount(event.amount)
            is TillUiEvent.RequestVoid -> _state.update {
                it.copy(pendingApproval = PendingApproval.Void(event.saleId, event.reason))
            }
            is TillUiEvent.Approve -> approve(event.username, event.password)
            TillUiEvent.CancelApproval -> _state.update { it.copy(pendingApproval = null) }

            TillUiEvent.StartTender -> _state.update { it.copy(isTendering = true, tenders = emptyList()) }
            TillUiEvent.CancelTender ->
                _state.update { it.copy(isTendering = false, tenders = emptyList(), cashEntry = "") }
            is TillUiEvent.CashEntryChanged -> _state.update { it.copy(cashEntry = event.value) }
            is TillUiEvent.AddTender -> addTender(event)
            is TillUiEvent.RemoveTender -> _state.update { current ->
                current.copy(tenders = current.tenders.filterIndexed { index, _ -> index != event.index })
            }
            TillUiEvent.Complete -> complete()

            is TillUiEvent.Hold -> hold(event.label)
            is TillUiEvent.Resume -> resume(event.heldSaleId)
            is TillUiEvent.DiscardHeld -> discard(event.heldSaleId)
            TillUiEvent.ClearBasket -> mutate { it.clear() }
            TillUiEvent.Reprint -> reprint()
            is TillUiEvent.OpenShift -> open(event.float)
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            resolveContext().fold(
                onSuccess = { resolved ->
                    till = resolved
                    // Every read happens before the update: `update` re-runs its block on
                    // contention, and these are all database calls.
                    val shop = settings.get().getOrNull()
                    val shift = currentShift(resolved).getOrNull()
                    val held = listHeld(resolved).getOrElse { emptyList() }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            shift = shift,
                            heldSales = held,
                            vatBasisPoints = shop?.vatBasisPoints ?: 0,
                        )
                    }
                    recalculate()
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun scan(barcode: String) {
        val context = till ?: return
        viewModelScope.launch(dispatchers.io) {
            find.byBarcode(barcode, context).fold(
                onSuccess = { result -> apply(result, barcode) },
                onFailure = { fail(it) },
            )
        }
    }

    private fun addByVariant(variantId: String) {
        val context = till ?: return
        viewModelScope.launch(dispatchers.io) {
            find.refresh(variantId, context).fold(
                onSuccess = { result -> apply(result, variantId) },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun apply(result: LookupResult, term: String) {
        when (result) {
            is LookupResult.Found -> {
                mutate { it.add(result.item) }
                _state.update { it.copy(query = "", results = emptyList()) }
            }
            // Refused rather than invented: a catalogue entry created at the till with a queue
            // waiting is junk nobody goes back and cleans up.
            LookupResult.NotFound -> _effect.emit(TillUiEffect.ShowError("Not in the catalogue: $term"))
            is LookupResult.NotPriced ->
                _effect.emit(TillUiEffect.ShowError("${result.item.sku} has no price yet"))
        }
    }

    private fun search() {
        val context = till ?: return
        val term = _state.value.query
        viewModelScope.launch(dispatchers.io) {
            find.search(term, context).fold(
                onSuccess = { found -> _state.update { it.copy(results = found) } },
                onFailure = { fail(it) },
            )
        }
    }

    private fun requestLineDiscount(lineIndex: Int, amount: String) {
        val parsed = Money.parse(amount) ?: return reject("That is not an amount")
        _state.update { it.copy(pendingApproval = PendingApproval.LineDiscount(lineIndex, parsed)) }
        attemptWithSessionAuthority()
    }

    private fun requestPriceOverride(lineIndex: Int, price: String) {
        val parsed = Money.parse(price) ?: return reject("That is not an amount")
        _state.update { it.copy(pendingApproval = PendingApproval.PriceOverride(lineIndex, parsed)) }
        attemptWithSessionAuthority()
    }

    private fun requestOrderDiscount(amount: String) {
        val parsed = Money.parse(amount) ?: return reject("That is not an amount")
        _state.update { it.copy(pendingApproval = PendingApproval.OrderDiscount(parsed)) }
        attemptWithSessionAuthority()
    }

    /**
     * Applies the change straight away when the person at the till already has the authority.
     *
     * An owner working alone should not be asked to type their own password to mark something
     * down. The use case checks permission again regardless — this only decides whether a dialog
     * appears, never whether the rule applies.
     *
     * A void is excluded on purpose: Phase 4's rule is that it needs a fresh credential
     * *regardless of who is signed in*.
     */
    private fun attemptWithSessionAuthority() {
        val pending = _state.value.pendingApproval ?: return
        if (pending is PendingApproval.Void) return
        viewModelScope.launch(dispatchers.io) {
            runCatching { applyApproved(pending, approvedBy = null) }
                .onSuccess { _state.update { it.copy(pendingApproval = null) } }
                .onFailure { /* Not permitted from the session alone: the dialog stays up. */ }
        }
    }

    private fun approve(username: String, password: String) {
        val pending = _state.value.pendingApproval ?: return
        val secret = password.toCharArray()
        viewModelScope.launch(dispatchers.io) {
            reauthenticate(username, secret, pending.permission).fold(
                onSuccess = { result ->
                    when (result) {
                        is ReauthResult.Approved -> {
                            runCatching { applyApproved(pending, result.user) }
                                .onSuccess { _state.update { it.copy(pendingApproval = null) } }
                                .onFailure { fail(it) }
                        }
                        ReauthResult.BadCredentials ->
                            _effect.emit(TillUiEffect.ShowError("Incorrect details"))
                        ReauthResult.NotPermitted ->
                            _effect.emit(TillUiEffect.ShowError("That account cannot approve this"))
                        is ReauthResult.Locked ->
                            _effect.emit(TillUiEffect.ShowError("Too many attempts — locked for a few minutes"))
                    }
                },
                onFailure = { fail(it) },
            )
            secret.wipe()
        }
    }

    private suspend fun applyApproved(pending: PendingApproval, approvedBy: User?) {
        val approverId = approvedBy?.id
        when (pending) {
            is PendingApproval.LineDiscount -> mutate {
                it.withLineDiscount(pending.lineIndex, pending.amount, approverId)
            }
            is PendingApproval.PriceOverride -> mutate {
                it.withUnitPrice(pending.lineIndex, pending.price, approverId)
            }
            is PendingApproval.OrderDiscount -> mutate {
                it.withOrderDiscount(pending.amount, approverId)
            }
            is PendingApproval.Void -> {
                // The use case re-checks this user's permission rather than trusting the screen.
                val approver = requireNotNull(approvedBy) { "a void always needs an approver" }
                voidSale(pending.saleId, pending.reason, approver).getOrThrow()
                _effect.emit(TillUiEffect.ShowMessage("Sale voided"))
            }
        }
    }

    private fun addTender(event: TillUiEvent.AddTender) {
        val amount = Money.parse(event.amount) ?: return reject("That is not an amount")
        val tendered = Money.parse(event.tendered) ?: amount
        if (amount.isZero || amount.isNegative) return reject("A tender must be more than zero")

        _state.update { current ->
            current.copy(
                tenders = current.tenders + Tender(event.method, amount, tendered),
                cashEntry = "",
            )
        }
    }

    private fun complete() {
        val context = till ?: return
        val current = _state.value
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isCommitting = true) }
            completeSale(
                basket = current.basket,
                tenders = current.tenders,
                context = context,
                shiftId = current.shift?.id,
                vatBasisPoints = current.vatBasisPoints,
            ).fold(
                onSuccess = { result -> handle(result) },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun handle(result: SaleResult) {
        _state.update { it.copy(isCommitting = false) }
        when (result) {
            is SaleResult.Completed -> {
                _state.update {
                    it.copy(
                        basket = Basket(),
                        totals = BasketTotals.EMPTY,
                        tenders = emptyList(),
                        isTendering = false,
                        cashEntry = "",
                        lastSaleId = result.sale.id,
                        lastReceiptNumber = result.sale.receiptNumber,
                    )
                }
                result.warnings.forEach { warning ->
                    when (warning) {
                        is SaleWarning.SoldBelowStock -> _effect.emit(
                            TillUiEffect.StockWarning(
                                "${warning.sku}: sold ${warning.sold}, stock said ${warning.onHand}",
                            ),
                        )
                    }
                }
                // Outside the commit, deliberately: a print failure must never undo a sale.
                printReceipt(result.sale).fold(
                    onSuccess = { receipt -> report(receipt, result.sale.receiptNumber) },
                    onFailure = { fail(it) },
                )
            }
            SaleResult.EmptyBasket -> _effect.emit(TillUiEffect.ShowError("Nothing to sell"))
            is SaleResult.UnderTendered ->
                _effect.emit(TillUiEffect.ShowError("Short by ${result.shortBy.format()}"))
        }
    }

    private suspend fun report(result: ReceiptResult, receiptNumber: Long) {
        when (result) {
            ReceiptResult.Printed -> _effect.emit(TillUiEffect.ShowMessage("Sale #$receiptNumber"))
            ReceiptResult.NoPrinter ->
                _effect.emit(TillUiEffect.ShowMessage("Sale #$receiptNumber — no printer configured"))
            is ReceiptResult.Unreachable -> _effect.emit(
                TillUiEffect.ShowError("Sale #$receiptNumber saved, but the printer did not answer"),
            )
        }
    }

    private fun reprint() {
        val saleId = _state.value.lastSaleId ?: return
        viewModelScope.launch(dispatchers.io) {
            printReceipt.reprint(saleId).fold(
                onSuccess = { report(it, _state.value.lastReceiptNumber ?: 0) },
                onFailure = { fail(it) },
            )
        }
    }

    private fun hold(label: String) {
        val context = till ?: return
        val basket = _state.value.basket
        viewModelScope.launch(dispatchers.io) {
            holdSale(basket, label, context).fold(
                onSuccess = {
                    val held = listHeld(context).getOrNull()
                    _state.update { current ->
                        current.copy(
                            basket = Basket(),
                            totals = BasketTotals.EMPTY,
                            heldSales = held ?: current.heldSales,
                        )
                    }
                    _effect.emit(TillUiEffect.ShowMessage("Held"))
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun resume(heldSaleId: String) {
        val context = till ?: return
        viewModelScope.launch(dispatchers.io) {
            resumeHeld(heldSaleId, context).fold(
                onSuccess = { resumed ->
                    val held = listHeld(context).getOrNull()
                    _state.update { current ->
                        current.copy(
                            basket = resumed.basket,
                            heldSales = held ?: current.heldSales,
                        )
                    }
                    recalculate()
                    if (resumed.dropped.isNotEmpty()) {
                        _effect.emit(
                            TillUiEffect.ShowError("${resumed.dropped.size} line(s) no longer sellable"),
                        )
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun discard(heldSaleId: String) {
        val context = till ?: return
        viewModelScope.launch(dispatchers.io) {
            discardHeld(heldSaleId).fold(
                onSuccess = {
                    val held = listHeld(context).getOrNull()
                    _state.update { current -> current.copy(heldSales = held ?: current.heldSales) }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun open(float: String) {
        val context = till ?: return
        val amount = Money.parse(float) ?: return reject("That is not an amount")
        viewModelScope.launch(dispatchers.io) {
            openShift(amount, context).fold(
                onSuccess = { shift ->
                    _state.update { it.copy(shift = shift) }
                    _effect.emit(TillUiEffect.ShowMessage("Shift open"))
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun mutate(change: (Basket) -> Basket) {
        runCatching { change(_state.value.basket) }.fold(
            onSuccess = { basket ->
                _state.update { it.copy(basket = basket) }
                recalculate()
            },
            onFailure = { cause -> reject(cause.message ?: "That change is not allowed") },
        )
    }

    private fun recalculate() {
        _state.update { it.copy(totals = calculate(it.basket, it.vatBasisPoints)) }
    }

    private fun reject(message: String) {
        viewModelScope.launch { _effect.emit(TillUiEffect.ShowError(message)) }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false, isCommitting = false) }
        _effect.emit(TillUiEffect.ShowError(cause.message ?: "Something went wrong"))
    }
}
