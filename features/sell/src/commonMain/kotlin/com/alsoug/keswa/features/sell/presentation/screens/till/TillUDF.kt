package com.alsoug.keswa.features.sell.presentation.screens.till

import com.alsoug.keswa.core.domain.model.HeldSale
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.model.BasketTotals
import com.alsoug.keswa.features.sell.domain.usecase.Tender

/**
 * What the till is asking someone to approve.
 *
 * Carried in state rather than as a boolean flag per dialog, because the approval and the action it
 * unlocks have to travel together — otherwise the screen has to remember which of three things the
 * password was for.
 */
sealed interface PendingApproval {
    data class LineDiscount(val lineIndex: Int, val amount: Money) : PendingApproval
    data class PriceOverride(val lineIndex: Int, val price: Money) : PendingApproval
    data class OrderDiscount(val amount: Money) : PendingApproval
    data class Void(val saleId: String, val reason: String) : PendingApproval

    val permission: Permission
        get() = when (this) {
            is LineDiscount, is OrderDiscount -> Permission.DISCOUNT_LINE
            is PriceOverride -> Permission.OVERRIDE_PRICE
            is Void -> Permission.VOID_SALE
        }
}

data class TillUiState(
    val isLoading: Boolean = false,
    val isCommitting: Boolean = false,
    val basket: Basket = Basket(),
    val totals: BasketTotals = BasketTotals.EMPTY,
    val query: String = "",
    val results: List<SellableItem> = emptyList(),
    val shift: Shift? = null,
    val heldSales: List<HeldSale> = emptyList(),
    val vatBasisPoints: Int = 0,
    val tenders: List<Tender> = emptyList(),
    val isTendering: Boolean = false,
    val cashEntry: String = "",
    val pendingApproval: PendingApproval? = null,
    /** Set when a sale has just completed, so the screen can offer a reprint. */
    val lastSaleId: String? = null,
    val lastReceiptNumber: Long? = null,
) {
    val canTender: Boolean get() = !basket.isEmpty && !isCommitting

    val settled: Money get() = tenders.fold(Money.ZERO) { sum, tender -> sum + tender.amount }

    val outstanding: Money
        get() = (totals.total - settled).let { if (it.isNegative) Money.ZERO else it }

    val handedOver: Money get() = tenders.fold(Money.ZERO) { sum, tender -> sum + tender.tendered }

    val change: Money
        get() = (handedOver - totals.total).let { if (it.isNegative) Money.ZERO else it }

    val isFullySettled: Boolean get() = !basket.isEmpty && settled >= totals.total
}

sealed interface TillUiEvent {
    data object Load : TillUiEvent
    data class Scanned(val barcode: String) : TillUiEvent
    data class QueryChanged(val value: String) : TillUiEvent
    data object Search : TillUiEvent
    data class PickResult(val variantId: String) : TillUiEvent
    data class QuantityChanged(val lineIndex: Int, val quantity: Int) : TillUiEvent
    data class RemoveLine(val lineIndex: Int) : TillUiEvent
    data class RequestLineDiscount(val lineIndex: Int, val amount: String) : TillUiEvent
    data class RequestPriceOverride(val lineIndex: Int, val price: String) : TillUiEvent
    data class RequestOrderDiscount(val amount: String) : TillUiEvent
    data class RequestVoid(val saleId: String, val reason: String) : TillUiEvent
    data class Approve(val username: String, val password: String) : TillUiEvent
    data object CancelApproval : TillUiEvent

    data object StartTender : TillUiEvent
    data object CancelTender : TillUiEvent
    data class CashEntryChanged(val value: String) : TillUiEvent
    data class AddTender(val method: TenderMethod, val amount: String, val tendered: String) : TillUiEvent
    data class RemoveTender(val index: Int) : TillUiEvent
    data object Complete : TillUiEvent

    data class Hold(val label: String) : TillUiEvent
    data class Resume(val heldSaleId: String) : TillUiEvent
    data class DiscardHeld(val heldSaleId: String) : TillUiEvent
    data object ClearBasket : TillUiEvent
    data object Reprint : TillUiEvent
    data class OpenShift(val float: String) : TillUiEvent
}

sealed interface TillNavigation {
    data object ToShiftClose : TillNavigation
}

sealed interface TillUiEffect {
    data class ShowError(val message: String) : TillUiEffect
    data class ShowMessage(val message: String) : TillUiEffect
    /** Persistent enough to need acknowledging: the shop just sold stock it does not have. */
    data class StockWarning(val message: String) : TillUiEffect
}
