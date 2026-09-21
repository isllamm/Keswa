package com.alsoug.keswa.features.returns.presentation.screens.returns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.session.ApprovalResult
import com.alsoug.keswa.core.session.ICredentialVerifier
import com.alsoug.keswa.features.returns.domain.usecase.CompleteReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.FindSaleForReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.PrintRefundNoteUseCase
import com.alsoug.keswa.features.returns.domain.usecase.RefundNoteResult
import com.alsoug.keswa.features.returns.domain.usecase.ReturnResult
import com.alsoug.keswa.features.returns.domain.usecase.ReturningLine
import com.alsoug.keswa.features.returns.domain.usecase.SaleLookup
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Overwrites a credential in place, so it is not left sitting in the heap until collection. */
private fun CharArray.wipe() = fill(Char.MIN_VALUE)

class ReturnsViewModel(
    private val findSale: FindSaleForReturnUseCase,
    private val completeReturn: CompleteReturnUseCase,
    private val printRefundNote: PrintRefundNoteUseCase,
    private val verifier: ICredentialVerifier,
    private val settings: ISettingsRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ReturnsUiState())
    val state: StateFlow<ReturnsUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<ReturnsNavigation>(replay = 0, extraBufferCapacity = 1)
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<ReturnsUiEffect>(replay = 0, extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    fun onEvent(event: ReturnsUiEvent) {
        when (event) {
            ReturnsUiEvent.Load -> load()
            is ReturnsUiEvent.LookupEntryChanged -> _state.update { it.copy(lookupEntry = event.value) }
            ReturnsUiEvent.Lookup -> lookup()
            is ReturnsUiEvent.QuantityChanged -> changeQuantity(event.saleLineId, event.quantity)
            is ReturnsUiEvent.ConditionChanged -> _state.update { current ->
                current.copy(
                    lines = current.lines.map {
                        if (it.saleLineId == event.saleLineId) it.copy(condition = event.condition) else it
                    },
                )
            }
            is ReturnsUiEvent.ReasonChanged -> _state.update { it.copy(reason = event.value) }
            is ReturnsUiEvent.RefundMethodChanged -> _state.update { it.copy(refundMethod = event.method) }
            ReturnsUiEvent.RequestApproval -> _state.update { it.copy(isAwaitingApproval = true) }
            is ReturnsUiEvent.Approve -> approve(event.username, event.password)
            ReturnsUiEvent.CancelApproval -> _state.update { it.copy(isAwaitingApproval = false) }
            ReturnsUiEvent.Complete -> complete()
            ReturnsUiEvent.Clear -> _state.update {
                ReturnsUiState(returnWindowDays = it.returnWindowDays)
            }
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            val shop = settings.get().getOrNull()
            _state.update {
                it.copy(isLoading = false, returnWindowDays = shop?.returnWindowDays ?: 0)
            }
        }
    }

    private fun lookup() {
        val entry = _state.value.lookupEntry.trim()
        if (entry.isEmpty()) return

        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            // A receipt number is what somebody types when the QR will not scan, which is a
            // Tuesday. Anything else is treated as the QR's payload — the sale's id.
            val result = entry.toLongOrNull()
                ?.let { findSale.byReceiptNumber(it) }
                ?: findSale.byQrCode(entry)

            result.fold(onSuccess = { show(it) }, onFailure = { fail(it) })
        }
    }

    private suspend fun show(lookup: SaleLookup) {
        _state.update { it.copy(isLoading = false) }
        when (lookup) {
            is SaleLookup.Found -> _state.update { current ->
                current.copy(
                    saleId = lookup.sale.id,
                    receiptNumber = lookup.sale.receiptNumber,
                    soldAt = lookup.sale.occurredAt,
                    daysSince = lookup.daysSince,
                    isInsidePolicy = lookup.isInsidePolicy,
                    shiftId = lookup.sale.shiftId,
                    lookupEntry = "",
                    approvedByUserId = null,
                    lines = lookup.lines.map { line ->
                        ReturnLineUiModel(
                            saleLineId = line.saleLineId,
                            variantId = line.variantId,
                            description = line.description,
                            soldQuantity = line.soldQuantity,
                            alreadyReturned = line.alreadyReturned,
                            returnable = line.returnable,
                            unitPrice = line.unitPrice,
                            unitCost = line.unitCost,
                        )
                    },
                )
            }
            SaleLookup.NotFound -> _effect.emit(ReturnsUiEffect.ShowError("No sale with that receipt"))
            is SaleLookup.Voided ->
                _effect.emit(ReturnsUiEffect.ShowError("That sale was voided — already reversed"))
            SaleLookup.FullyReturned ->
                _effect.emit(ReturnsUiEffect.ShowError("Everything on that receipt has come back"))
        }
    }

    private fun changeQuantity(saleLineId: String, quantity: Int) {
        _state.update { current ->
            current.copy(
                lines = current.lines.map { line ->
                    if (line.saleLineId != saleLineId) {
                        line
                    } else {
                        // Clamped here as an affordance; the use case checks it again against the
                        // database, which is where the real guard lives.
                        line.copy(selectedQuantity = quantity.coerceIn(0, line.returnable))
                    }
                },
            )
        }
    }

    private fun approve(username: String, password: String) {
        val permission = Permission.REFUND_ANY
        val secret = password.toCharArray()
        viewModelScope.launch(dispatchers.io) {
            runCatching { verifier.approve(username, secret, permission) }.fold(
                onSuccess = { result ->
                    when (result) {
                        is ApprovalResult.Approved -> _state.update {
                            it.copy(isAwaitingApproval = false, approvedByUserId = result.user.id)
                        }
                        ApprovalResult.BadCredentials ->
                            _effect.emit(ReturnsUiEffect.ShowError("Incorrect details"))
                        ApprovalResult.NotPermitted ->
                            _effect.emit(ReturnsUiEffect.ShowError("That account cannot approve this"))
                        is ApprovalResult.Locked ->
                            _effect.emit(ReturnsUiEffect.ShowError("Too many attempts — locked for a few minutes"))
                    }
                },
                onFailure = { fail(it) },
            )
            secret.wipe()
        }
    }

    private fun complete() {
        val current = _state.value
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isCommitting = true) }
            completeReturn(
                originalSaleId = current.saleId,
                lines = current.selected.map { line ->
                    ReturningLine(
                        saleLineId = line.saleLineId,
                        variantId = line.variantId,
                        description = line.description,
                        quantity = line.selectedQuantity,
                        unitRefund = line.unitPrice,
                        unitCost = line.unitCost,
                        condition = line.condition,
                    )
                },
                reason = current.reason,
                refundMethod = current.refundMethod,
                shiftId = current.shiftId,
                authorisedByUserId = current.approvedByUserId,
                needsAuthority = current.needsAuthority,
            ).fold(
                onSuccess = { result -> handle(result) },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun handle(result: ReturnResult) {
        _state.update { it.copy(isCommitting = false) }
        when (result) {
            is ReturnResult.Completed -> {
                _state.update {
                    ReturnsUiState(
                        returnWindowDays = it.returnWindowDays,
                        lastReturn = result.saleReturn,
                    )
                }
                _effect.emit(
                    ReturnsUiEffect.ShowMessage(
                        "Refunded ${result.saleReturn.refundAmount.format()} " +
                            "· return #${result.saleReturn.returnNumber}",
                    ),
                )
                // Outside the commit, deliberately: a print failure must never undo a refund.
                printRefundNote(result.saleReturn).fold(
                    onSuccess = { note ->
                        if (note is RefundNoteResult.Unreachable) {
                            _effect.emit(
                                ReturnsUiEffect.ShowError(
                                    "Refund saved, but the printer did not answer",
                                ),
                            )
                        }
                    },
                    onFailure = { fail(it) },
                )
            }
            ReturnResult.NothingToReturn ->
                _effect.emit(ReturnsUiEffect.ShowError("Nothing selected to return"))
            is ReturnResult.ExceedsSold -> _effect.emit(
                ReturnsUiEffect.ShowError(
                    "${result.description}: only ${result.returnable} left to return",
                ),
            )
        }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false, isCommitting = false) }
        _effect.emit(ReturnsUiEffect.ShowError(cause.message ?: "Something went wrong"))
    }
}
