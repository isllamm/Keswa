package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * A shop that buys from this one.
 *
 * [creditLimit] of zero means cash only — the right default for a customer nobody has decided to
 * trust yet, and the reason the limit is a hard stop rather than a warning.
 */
data class Customer(
    val id: String,
    val name: String,
    val nameAr: String,
    val phone: String?,
    val taxId: String?,
    val priceListId: String,
    val creditLimit: Money,
    val paymentTermsDays: Int,
    val isActive: Boolean,
) {
    val sellsOnAccount: Boolean get() = !creditLimit.isZero

    /** What this customer may still take on account, given what they already owe. */
    fun availableCredit(balance: Money): Money =
        (creditLimit - balance).let { if (it.isNegative) Money.ZERO else it }
}

/** One immutable change in what a customer owes. Positive is owed, negative is paid. */
data class LedgerEntry(
    val id: String,
    val customerId: String,
    val type: LedgerEntryType,
    val amount: Money,
    val refType: String?,
    val refId: String?,
    val occurredAt: Long,
    val dueAt: Long?,
    val userId: String,
    val note: String?,
    val authorisedByUserId: String? = null,
) {
    val isDebit: Boolean get() = !amount.isNegative

    fun isOverdueAt(nowMillis: Long): Boolean = isDebit && dueAt != null && nowMillis > dueAt
}

/** A statement: the entries over a period, with the balance they walk to. */
data class Statement(
    val customer: Customer,
    val openingBalance: Money,
    val entries: List<LedgerEntry>,
    val closingBalance: Money,
) {
    val invoiced: Money
        get() = entries.filter { it.isDebit }.fold(Money.ZERO) { sum, it -> sum + it.amount }

    val paid: Money
        get() = entries.filterNot { it.isDebit }.fold(Money.ZERO) { sum, it -> sum + it.amount }
}

/**
 * How overdue a balance is, in the conventional buckets.
 *
 * Computed from each debit's own due date rather than stored, so nothing can fall out of step.
 * Payments are applied oldest-first, which is the convention and is a *view* — the ledger records
 * that money arrived, not which invoice somebody decided it belonged to.
 */
data class Ageing(
    val current: Money,
    val thirtyDays: Money,
    val sixtyDays: Money,
    val ninetyDaysPlus: Money,
) {
    val total: Money get() = current + thirtyDays + sixtyDays + ninetyDaysPlus

    val overdue: Money get() = thirtyDays + sixtyDays + ninetyDaysPlus

    companion object {
        val NOTHING = Ageing(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO)
    }
}

/** A carton sold as one thing, at one price, containing several variants. */
data class AssortmentPack(
    val id: String,
    val name: String,
    val nameAr: String,
    val price: Money,
    val isActive: Boolean,
    val lines: List<AssortmentPackLine> = emptyList(),
) {
    val pieceCount: Int get() = lines.sumOf { it.quantity }
}

data class AssortmentPackLine(
    val id: String,
    val packId: String,
    val variantId: String,
    val quantity: Int,
)
