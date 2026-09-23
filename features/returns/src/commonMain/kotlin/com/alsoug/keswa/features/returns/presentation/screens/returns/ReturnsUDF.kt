package com.alsoug.keswa.features.returns.presentation.screens.returns

import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.SaleReturn
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money

/** One line of the original sale, with what the cashier has decided is coming back. */
data class ReturnLineUiModel(
    val saleLineId: String,
    val variantId: String,
    val description: String,
    val soldQuantity: Int,
    val alreadyReturned: Int,
    val returnable: Int,
    val unitPrice: Money,
    val unitCost: Money,
    val selectedQuantity: Int = 0,
    val condition: ReturnCondition = ReturnCondition.SELLABLE,
) {
    val isFullyReturned: Boolean get() = returnable <= 0

    val lineRefund: Money get() = unitPrice * selectedQuantity
}

data class ReturnsUiState(
    val isLoading: Boolean = false,
    val isCommitting: Boolean = false,
    val lookupEntry: String = "",
    val saleId: String? = null,
    val receiptNumber: Long? = null,
    val soldAt: Long? = null,
    val daysSince: Int = 0,
    val isInsidePolicy: Boolean = true,
    val returnWindowDays: Int = 0,
    val lines: List<ReturnLineUiModel> = emptyList(),
    val reason: String = "",
    val refundMethod: TenderMethod = TenderMethod.CASH,
    val shiftId: String? = null,
    /** Set while an admin credential is being asked for, so the action and the approval travel together. */
    val isAwaitingApproval: Boolean = false,
    val approvedByUserId: String? = null,
    val lastReturn: SaleReturn? = null,
) {
    val hasSale: Boolean get() = saleId != null

    val selected: List<ReturnLineUiModel> get() = lines.filter { it.selectedQuantity > 0 }

    val refundTotal: Money
        get() = selected.fold(Money.ZERO) { sum, line -> sum + line.lineRefund }

    /**
     * Anything but a plain in-policy return with a receipt needs an admin.
     *
     * The permission is the policy: a good customer on day 40 still gets their money, and the
     * approval is on the record.
     */
    val needsAuthority: Boolean get() = !isInsidePolicy || saleId == null

    val canComplete: Boolean
        get() = selected.isNotEmpty() && reason.isNotBlank() && !isCommitting &&
            (!needsAuthority || approvedByUserId != null)
}

sealed interface ReturnsUiEvent {
    data object Load : ReturnsUiEvent
    data class LookupEntryChanged(val value: String) : ReturnsUiEvent
    data object Lookup : ReturnsUiEvent
    data class QuantityChanged(val saleLineId: String, val quantity: Int) : ReturnsUiEvent
    data class ConditionChanged(val saleLineId: String, val condition: ReturnCondition) : ReturnsUiEvent
    data class ReasonChanged(val value: String) : ReturnsUiEvent
    data class RefundMethodChanged(val method: TenderMethod) : ReturnsUiEvent
    data object RequestApproval : ReturnsUiEvent
    data class Approve(val username: String, val password: String) : ReturnsUiEvent
    data object CancelApproval : ReturnsUiEvent
    data object Complete : ReturnsUiEvent
    data object Clear : ReturnsUiEvent
}

sealed interface ReturnsNavigation {
    data object Done : ReturnsNavigation
}

sealed interface ReturnsUiEffect {
    data class ShowError(val message: Message) : ReturnsUiEffect
    data class ShowMessage(val message: Message) : ReturnsUiEffect
}
