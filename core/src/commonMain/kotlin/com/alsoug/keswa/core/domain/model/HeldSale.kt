package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * A cart parked so the till can serve the next person.
 *
 * Not a [Sale] and deliberately not a status on one: it has not happened, and keeping it out of the
 * sale table means no revenue query needs a filter that someone will eventually forget.
 *
 * The only rows in the app that never sync — a parked cart belongs to the till it was parked on.
 */
data class HeldSale(
    val id: String,
    val label: String,
    val locationId: String,
    val userId: String,
    val heldAt: Long,
    val lines: List<HeldSaleLine> = emptyList(),
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }
}

data class HeldSaleLine(
    val id: String,
    val heldSaleId: String,
    val lineNumber: Int,
    val variantId: String,
    val quantity: Int,
    val unitPrice: Money,
    val lineDiscount: Money,
    val authorisedByUserId: String?,
)
