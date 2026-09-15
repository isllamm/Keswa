package com.alsoug.keswa.core.printing.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * What a receipt says, independent of how it is drawn.
 *
 * Pure data so the layout can be unit-tested and the rendering swapped per platform. Both
 * languages travel together because an Egyptian receipt carries both.
 */
data class Receipt(
    val shopName: String,
    val shopNameAr: String,
    val addressLine: String,
    val lines: List<ReceiptLine>,
    val subtotal: Money,
    val discount: Money,
    val vat: Money,
    val total: Money,
    val itemCount: Int,
    val saleId: String,
    val timestamp: String,
    val footerAr: String = "شكرًا لتسوقكم معنا",
) {
    val hasDiscount: Boolean get() = !discount.isZero
}

data class ReceiptLine(
    val quantity: Int,
    val description: String,
    val descriptionAr: String,
    val amount: Money,
)
