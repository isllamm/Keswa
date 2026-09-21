package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * A delivery, being unpacked or already received.
 *
 * Nothing moves while it is a [DocumentStatus.DRAFT]. Posting writes every movement and every cost
 * change in one transaction, and is terminal — a mistake afterwards is an adjustment, not a reopen.
 */
data class StockReceipt(
    val id: String,
    val reference: String,
    val supplierName: String,
    val locationId: String,
    val status: DocumentStatus,
    val note: String?,
    val createdAt: Long,
    val createdByUserId: String,
    val postedAt: Long? = null,
    val postedByUserId: String? = null,
    val totalCost: Money = Money.ZERO,
    val lines: List<StockReceiptLine> = emptyList(),
) {
    val isDraft: Boolean get() = status == DocumentStatus.DRAFT

    val pieceCount: Int get() = lines.sumOf { it.quantity }
}

data class StockReceiptLine(
    val id: String,
    val receiptId: String,
    val lineNumber: Int,
    val variantId: String,
    val quantity: Int,
    val unitCost: Money,
    val lineTotal: Money,
)

/**
 * A count in progress, or the record of one.
 *
 * Blind: [StockCountLine.expected] is null on every line until the count is posted.
 */
data class StockCount(
    val id: String,
    val locationId: String,
    val status: DocumentStatus,
    val note: String?,
    val startedAt: Long,
    val startedByUserId: String,
    val postedAt: Long? = null,
    val postedByUserId: String? = null,
    val lines: List<StockCountLine> = emptyList(),
) {
    val isOpen: Boolean get() = status == DocumentStatus.DRAFT

    /** Only meaningful after posting, which is the only time the expected figures exist. */
    val discrepancies: List<StockCountLine> get() = lines.filter { (it.variance ?: 0) != 0 }
}

data class StockCountLine(
    val id: String,
    val countId: String,
    val lineNumber: Int,
    val variantId: String,
    val counted: Int,
    val expected: Int? = null,
    val variance: Int? = null,
)
