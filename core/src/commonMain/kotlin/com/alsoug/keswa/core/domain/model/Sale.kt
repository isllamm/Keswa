package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * A completed transaction: what was sold, for how much, by whom, and how it was paid for.
 *
 * Never edited. A mistake is corrected by voiding — which marks this row and writes compensating
 * stock movements — so the shop's takings have the same append-only guarantee as its stock.
 */
data class Sale(
    val id: String,
    val receiptNumber: Long,
    val locationId: String,
    val priceListId: String,
    val userId: String,
    val shiftId: String?,
    /** Null for a retail walk-in; set for wholesale, whether on account or paid at once. */
    val customerId: String? = null,
    val status: SaleStatus,
    val subtotal: Money,
    val discount: Money,
    val tax: Money,
    val total: Money,
    val tendered: Money,
    val change: Money,
    val occurredAt: Long,
    val voidedAt: Long? = null,
    val voidedByUserId: String? = null,
    val voidReason: String? = null,
    val lines: List<SaleLine> = emptyList(),
    val payments: List<Payment> = emptyList(),
) {
    val isVoided: Boolean get() = status == SaleStatus.VOIDED

    val itemCount: Int get() = lines.sumOf { it.quantity }
}

/**
 * One line of a sale.
 *
 * [unitCost] is a snapshot taken at the moment of sale. Margin computed against today's cost would
 * rewrite every historical report the next time a supplier raises a price.
 *
 * [orderDiscount] is this line's allocated share of an order-level discount, so the lines always
 * sum back to the header — see `Money.allocate`.
 */
data class SaleLine(
    val id: String,
    val saleId: String,
    val lineNumber: Int,
    val variantId: String,
    val description: String,
    val descriptionAr: String,
    val quantity: Int,
    val unitPrice: Money,
    val lineDiscount: Money,
    val orderDiscount: Money,
    val lineTotal: Money,
    val tax: Money,
    val unitCost: Money,
    /** Who approved a discount or an override on this line, if one was needed. */
    val authorisedByUserId: String? = null,
    /** The assortment pack this line was expanded from, so a receipt can group the carton. */
    val packId: String? = null,
)

/**
 * One tender against a sale. Several per sale is the normal case, not the exception.
 *
 * [amount] is what this tender settles; [tendered] is what the customer handed over. They differ
 * only for cash, and the difference is change.
 */
data class Payment(
    val id: String,
    val saleId: String,
    val method: TenderMethod,
    val amount: Money,
    val tendered: Money,
    val reference: String?,
    val occurredAt: Long,
)
