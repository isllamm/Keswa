package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * One person's stint at the till, from opening float to closing count.
 *
 * The reason to have shifts at all is that a discrepancy found on the day is a conversation, and
 * one found at the month end is an accusation nobody can settle.
 */
data class Shift(
    val id: String,
    val locationId: String,
    val openedByUserId: String,
    val openedAt: Long,
    val openingFloat: Money,
    val closedAt: Long? = null,
    val closedByUserId: String? = null,
    val countedCash: Money? = null,
    val expectedCash: Money? = null,
    val note: String? = null,
) {
    val isOpen: Boolean get() = closedAt == null

    /** Positive if the drawer holds more than it should, negative if it is short. */
    val difference: Money?
        get() = if (countedCash != null && expectedCash != null) countedCash - expectedCash else null
}

/**
 * What a shift did, as read off at close.
 *
 * [expectedCash] is stored on the shift rather than recomputed on demand: a report that changes its
 * own history when a later correction lands is not a report anyone can act on.
 */
data class ZReport(
    val shift: Shift,
    val saleCount: Int,
    val voidedCount: Int,
    val grossSales: Money,
    val discounts: Money,
    val tax: Money,
    val netSales: Money,
    val cashTaken: Money,
    val cardTaken: Money,
    val changeGiven: Money,
    val expectedCash: Money,
    val countedCash: Money?,
    /** Sales rung up outside any shift — worth seeing, because it should usually be none. */
    val salesOutsideShift: Int,
) {
    val difference: Money? get() = countedCash?.let { it - expectedCash }
}
