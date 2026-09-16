package com.alsoug.keswa.features.sell.domain.model

import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money

/**
 * One line of the cart in front of the cashier.
 *
 * [listPrice] is kept beside [unitPrice] so an override is a fact the line carries rather than
 * something the UI has to remember — the receipt, the permission check and the audit trail all
 * need to know that a price was changed by hand.
 */
data class BasketLine(
    val variantId: String,
    val sku: String,
    val description: String,
    val descriptionAr: String,
    val quantity: Int,
    val unitPrice: Money,
    val listPrice: Money,
    val unitCost: Money,
    val lineDiscount: Money = Money.ZERO,
    val onHand: Int = 0,
    val authorisedByUserId: String? = null,
) {
    val isPriceOverridden: Boolean get() = unitPrice != listPrice

    val isDiscounted: Boolean get() = !lineDiscount.isZero

    val gross: Money get() = unitPrice * quantity

    val net: Money get() = gross - lineDiscount

    /** True when the shop's figures say this line sells stock it does not have. */
    val exceedsStock: Boolean get() = quantity > onHand
}

/**
 * The cart — **domain state, not UI state** (ADR-021).
 *
 * Everything that changes a cart is a pure function here, so the ViewModel holds the basket and
 * delegates, and the same rules apply whether a line was added by a scan, a search or a resumed
 * hold.
 */
data class Basket(
    val lines: List<BasketLine> = emptyList(),
    val orderDiscount: Money = Money.ZERO,
    val orderDiscountAuthorisedByUserId: String? = null,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    val itemCount: Int get() = lines.sumOf { it.quantity }

    val hasOverride: Boolean get() = lines.any { it.isPriceOverridden }

    val hasDiscount: Boolean get() = !orderDiscount.isZero || lines.any { it.isDiscounted }

    val sellsBelowStock: Boolean get() = lines.any { it.exceedsStock }

    /**
     * Adds [item] at its list price, or bumps the quantity if it is already in the cart.
     *
     * Scanning the same shirt three times should read as `3 ×`, not as three identical lines a
     * customer then has to be talked through.
     */
    fun add(item: SellableItem, quantity: Int = 1): Basket {
        val price = requireNotNull(item.price) { "variant has no price: ${item.sku}" }
        require(quantity > 0) { "quantity must be positive" }

        val existing = lines.indexOfFirst { it.variantId == item.variantId && !it.isPriceOverridden }
        if (existing >= 0) return withQuantity(existing, lines[existing].quantity + quantity)

        val line = BasketLine(
            variantId = item.variantId,
            sku = item.sku,
            description = item.description,
            descriptionAr = item.descriptionAr,
            quantity = quantity,
            unitPrice = price,
            listPrice = price,
            unitCost = item.cost,
            onHand = item.onHand,
        )
        require(line.gross.piastres <= MAX_LINE_PIASTRES) { "line total is implausibly large" }
        return copy(lines = lines + line)
    }

    fun withQuantity(index: Int, quantity: Int): Basket {
        if (quantity <= 0) return removeAt(index)
        val updated = lines[index].copy(quantity = quantity)
        require(updated.gross.piastres <= MAX_LINE_PIASTRES) { "line total is implausibly large" }
        return replace(index, updated)
    }

    fun withUnitPrice(index: Int, price: Money, authorisedByUserId: String?): Basket {
        require(!price.isNegative) { "a price cannot be negative" }
        val updated = lines[index].copy(unitPrice = price, authorisedByUserId = authorisedByUserId)
        require(updated.gross.piastres <= MAX_LINE_PIASTRES) { "line total is implausibly large" }
        return replace(index, updated)
    }

    fun withLineDiscount(index: Int, discount: Money, authorisedByUserId: String?): Basket {
        val line = lines[index]
        require(!discount.isNegative) { "a discount cannot be negative" }
        require(discount <= line.gross) { "a discount cannot exceed the line" }
        return replace(index, line.copy(lineDiscount = discount, authorisedByUserId = authorisedByUserId))
    }

    /**
     * Sets a discount on the whole order, which is then spread across the lines when totals are
     * computed so that the receipt's lines always add up to the figure printed under them.
     */
    fun withOrderDiscount(discount: Money, authorisedByUserId: String?): Basket {
        require(!discount.isNegative) { "a discount cannot be negative" }
        require(discount <= lines.fold(Money.ZERO) { sum, line -> sum + line.net }) {
            "a discount cannot exceed the order"
        }
        return copy(orderDiscount = discount, orderDiscountAuthorisedByUserId = authorisedByUserId)
    }

    fun removeAt(index: Int): Basket {
        val remaining = lines.filterIndexed { position, _ -> position != index }
        // An order discount set against a bigger order must not survive into a smaller one.
        val ceiling = remaining.fold(Money.ZERO) { sum, line -> sum + line.net }
        return copy(
            lines = remaining,
            orderDiscount = if (orderDiscount > ceiling) ceiling else orderDiscount,
        )
    }

    fun clear(): Basket = Basket()

    private fun replace(index: Int, line: BasketLine): Basket {
        val updated = lines.toMutableList().also { it[index] = line }
        val ceiling = updated.fold(Money.ZERO) { sum, existing -> sum + existing.net }
        return copy(
            lines = updated,
            orderDiscount = if (orderDiscount > ceiling) ceiling else orderDiscount,
        )
    }

    companion object {
        /**
         * One million pounds on a single line.
         *
         * Two jobs: it catches a mistyped price before it reaches the ledger, and it keeps line
         * amounts inside the `Int` range that `Money.allocate`'s weights need.
         */
        const val MAX_LINE_PIASTRES = 100_000_000L
    }
}

/** A line's share of the order after allocation — what actually goes on the receipt and the row. */
data class LineTotals(
    val line: BasketLine,
    val orderDiscount: Money,
    val lineTotal: Money,
    val tax: Money,
)

/**
 * The arithmetic of a cart, computed in one place.
 *
 * [subtotal] minus [discount] is exactly [total], and the per-line figures sum to the same — that
 * is the whole point of computing them together rather than in the view.
 */
data class BasketTotals(
    val subtotal: Money,
    val discount: Money,
    val tax: Money,
    val total: Money,
    val itemCount: Int,
    val lines: List<LineTotals>,
) {
    companion object {
        val EMPTY = BasketTotals(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0, emptyList())
    }
}
