package com.alsoug.keswa.features.wholesale.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.LedgerEntry
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.permissions
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ICustomerRepository
import com.alsoug.keswa.core.domain.repository.IReceivablesRepository
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager

/** What a credit check said, and why. */
sealed interface CreditDecision {
    data class Allowed(val availableAfter: Money) : CreditDecision

    /** Over the limit. An admin can still approve it, and the approval goes on the entry. */
    data class OverLimit(
        val balance: Money,
        val limit: Money,
        val over: Money,
    ) : CreditDecision

    /** A limit of zero: this customer is cash only until somebody decides otherwise. */
    data object CashOnly : CreditDecision

    data object NoSuchCustomer : CreditDecision
}

/**
 * Whether this customer may take these goods on account.
 *
 * **A hard stop, not a warning.** A credit limit that warns is a credit limit that gets clicked
 * through on a busy morning, and the busy morning is the entire reason it exists. Going over needs
 * an admin's approval in place, which is recorded on the ledger entry.
 */
class CheckCreditUseCase(
    private val customers: ICustomerRepository,
    private val receivables: IReceivablesRepository,
) {
    suspend operator fun invoke(customerId: String, amount: Money): Result<CreditDecision> =
        runCatching {
            val customer = customers.getById(customerId).getOrThrow()
                ?: return@runCatching CreditDecision.NoSuchCustomer
            if (!customer.sellsOnAccount) return@runCatching CreditDecision.CashOnly

            val balance = receivables.balance(customerId).getOrThrow()
            val after = balance + amount

            if (after > customer.creditLimit) {
                CreditDecision.OverLimit(
                    balance = balance,
                    limit = customer.creditLimit,
                    over = after - customer.creditLimit,
                )
            } else {
                CreditDecision.Allowed(availableAfter = customer.availableCredit(after))
            }
        }
}

/**
 * Opens a receivable for goods that left on account.
 *
 * Called from the same transaction as the sale it belongs to — goods out and money owed are one
 * event, and a shop that recorded one without the other would be giving stock away.
 *
 * The due date comes from the customer's terms and lands on the entry, because the entry is the
 * thing that falls due and ageing reads it directly.
 */
class OpenReceivableUseCase(
    private val customers: ICustomerRepository,
    private val receivables: IReceivablesRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        customerId: String,
        amount: Money,
        saleId: String,
        occurredAt: Long,
        userId: String,
        authorisedByUserId: String? = null,
    ): Result<LedgerEntry> = runCatching {
        val customer = requireNotNull(customers.getById(customerId).getOrThrow()) {
            "customer not found: $customerId"
        }

        receivables.record(
            id = ids.newId(),
            customerId = customerId,
            type = LedgerEntryType.INVOICE,
            // Positive: they owe more.
            amount = amount,
            refType = REF_INVOICE,
            refId = saleId,
            occurredAt = occurredAt,
            dueAt = occurredAt + customer.paymentTermsDays * MILLIS_PER_DAY,
            userId = userId,
            note = null,
            authorisedByUserId = authorisedByUserId,
        ).getOrThrow()
    }

    companion object {
        const val REF_INVOICE = "INVOICE"
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/**
 * Takes money against the account, not against an invoice.
 *
 * A customer pays 5,000 against four outstanding invoices, or on account against nothing in
 * particular. Forcing the shop to name an invoice at the moment the money arrives is a decision
 * they usually cannot make and get wrong as often as not — so allocation is left to the ageing
 * report, oldest-first, and is never stored.
 */
class TakePaymentOnAccountUseCase(
    private val receivables: IReceivablesRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        customerId: String,
        amount: Money,
        note: String?,
    ): Result<LedgerEntry> = runCatching {
        requirePermission(sessions.current.value?.user, Permission.SELL)
        require(!amount.isNegative && !amount.isZero) { "a payment must be more than zero" }
        val user = requireNotNull(sessions.current.value) { "no session" }.user

        receivables.record(
            id = ids.newId(),
            customerId = customerId,
            type = LedgerEntryType.PAYMENT,
            // Negative: they owe less.
            amount = -amount,
            refType = null,
            refId = null,
            occurredAt = now(),
            dueAt = null,
            userId = user.id,
            note = note?.trim()?.ifBlank { null },
        ).getOrThrow()
    }
}

/**
 * Credits an account for goods that came back, or a mistake that needs reversing.
 *
 * Never an edit of the invoice: the invoice happened. Same rule as everything else in this app,
 * and the same rule an accountant would insist on.
 */
class IssueCreditNoteUseCase(
    private val receivables: IReceivablesRepository,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        customerId: String,
        amount: Money,
        reason: String,
        againstRefId: String?,
        authorisedBy: User,
    ): Result<LedgerEntry> = runCatching {
        requirePermission(authorisedBy, Permission.REFUND_ANY)
        require(!amount.isNegative && !amount.isZero) { "a credit note must be more than zero" }
        if (reason.isBlank()) throw Error.InvalidData("a credit note needs a reason")

        receivables.record(
            id = ids.newId(),
            customerId = customerId,
            type = LedgerEntryType.CREDIT_NOTE,
            amount = -amount,
            refType = againstRefId?.let { REF_CREDIT_NOTE },
            refId = againstRefId,
            occurredAt = now(),
            dueAt = null,
            userId = authorisedBy.id,
            note = reason.trim(),
            authorisedByUserId = authorisedBy.id,
        ).getOrThrow()
    }

    companion object {
        const val REF_CREDIT_NOTE = "CREDIT_NOTE"
    }
}

internal fun requirePermission(user: User?, permission: Permission) {
    if (user == null || permission !in user.role.permissions) {
        throw Error.ForbiddenAccess("not permitted: ${permission.name}")
    }
}

/** A customer's balance and how overdue it is, for the till and the statement screen. */
class GetCustomerAccountUseCase(
    private val customers: ICustomerRepository,
    private val receivables: IReceivablesRepository,
    private val now: () -> Long,
) {
    data class Account(
        val customer: Customer,
        val balance: Money,
        val available: Money,
        val ageing: com.alsoug.keswa.core.domain.model.Ageing,
    )

    suspend operator fun invoke(customerId: String): Result<Account?> = runCatching {
        val customer = customers.getById(customerId).getOrThrow() ?: return@runCatching null
        val balance = receivables.balance(customerId).getOrThrow()

        Account(
            customer = customer,
            balance = balance,
            available = customer.availableCredit(balance),
            ageing = receivables.ageing(customerId, now()).getOrThrow(),
        )
    }
}
