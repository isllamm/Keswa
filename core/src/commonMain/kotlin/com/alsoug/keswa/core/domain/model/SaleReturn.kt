package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * Goods coming back, and the money going out.
 *
 * Its own document rather than a negative sale: the original has to keep its figures, and a return
 * carries facts a sale has no use for. Voided rather than edited, like everything else that moves
 * money or stock.
 */
data class SaleReturn(
    val id: String,
    val returnNumber: Long,
    val originalSaleId: String?,
    val locationId: String,
    val userId: String,
    val shiftId: String?,
    val status: SaleStatus,
    val reason: String,
    val refundMethod: TenderMethod,
    val refundAmount: Money,
    val subtotal: Money,
    val tax: Money,
    val occurredAt: Long,
    val exchangeSaleId: String? = null,
    val authorisedByUserId: String? = null,
    val voidedAt: Long? = null,
    val voidedByUserId: String? = null,
    val voidReason: String? = null,
    val lines: List<SaleReturnLine> = emptyList(),
) {
    val isVoided: Boolean get() = status == SaleStatus.VOIDED

    val hasReceipt: Boolean get() = originalSaleId != null

    val isExchange: Boolean get() = exchangeSaleId != null

    val itemCount: Int get() = lines.sumOf { it.quantity }
}

data class SaleReturnLine(
    val id: String,
    val returnId: String,
    val lineNumber: Int,
    val saleLineId: String?,
    val variantId: String,
    val description: String,
    val quantity: Int,
    val unitRefund: Money,
    val lineRefund: Money,
    val condition: ReturnCondition,
    /** Carried over from the sale line, so a change of mind does not move the average cost. */
    val unitCost: Money,
) {
    val goesBackOnTheRail: Boolean get() = condition == ReturnCondition.SELLABLE
}

/**
 * A line of an original sale, with what is still returnable against it.
 *
 * [returnable] is what was sold minus what has already come back — the guard against the same
 * jacket being returned three times against one receipt.
 */
data class ReturnableLine(
    val saleLineId: String,
    val variantId: String,
    val description: String,
    val soldQuantity: Int,
    val alreadyReturned: Int,
    val unitPrice: Money,
    val unitCost: Money,
) {
    val returnable: Int get() = soldQuantity - alreadyReturned

    val isFullyReturned: Boolean get() = returnable <= 0
}
