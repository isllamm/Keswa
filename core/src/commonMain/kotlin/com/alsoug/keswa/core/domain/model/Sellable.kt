package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * Something the till can ring up: a variant, priced, with what the shop thinks it has.
 *
 * [price] is nullable because an unpriced variant is a real state — the catalogue can create a SKU
 * before anyone decides what it sells for — and the till refuses to guess rather than ringing up a
 * zero.
 */
data class SellableItem(
    val variantId: String,
    val productId: String,
    val sku: String,
    val name: String,
    val nameAr: String,
    val colourName: String,
    val colourNameAr: String,
    val cost: Money,
    val price: Money?,
    val onHand: Int,
) {
    val isPriced: Boolean get() = price != null

    /** What goes on the receipt: the style and its colour, which is how a customer identifies it. */
    val description: String get() = "$name — $colourName"

    val descriptionAr: String get() = "$nameAr — $colourNameAr"
}
