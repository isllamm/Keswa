package com.alsoug.keswa.features.sell.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Payment
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleLine
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.permissions
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.model.CreditPolicy
import com.alsoug.keswa.core.domain.repository.ICustomerRepository
import com.alsoug.keswa.core.domain.repository.IReceivablesRepository
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.SaleDraft
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require
import com.alsoug.keswa.features.sell.domain.model.Basket

/** One tender the cashier has entered, before it is a row. */
data class Tender(
    val method: TenderMethod,
    val amount: Money,
    val tendered: Money = amount,
    val reference: String? = null,
)

/**
 * What happened when the till tried to take the money.
 *
 * [Completed] carries warnings rather than refusing: selling stock the shop's figures say it does
 * not have is an ordinary event — the figure is more often wrong than the customer's hands — and
 * refusing it just teaches the shop to work around the till.
 */
sealed interface SaleResult {
    data class Completed(val sale: Sale, val warnings: List<SaleWarning>) : SaleResult
    data object EmptyBasket : SaleResult
    data class UnderTendered(val shortBy: Money) : SaleResult

    /**
     * Over the customer's credit limit, and refused.
     *
     * A stop rather than a warning: a limit that can be clicked through on a busy morning is not a
     * limit, and the busy morning is what it exists for. An admin can still approve it in place.
     */
    data class OverCreditLimit(val balance: Money, val limit: Money, val over: Money) : SaleResult

    /** A limit of zero. Nobody has decided to trust this customer yet. */
    data object CustomerIsCashOnly : SaleResult

    /** A credit tender with nobody to bill. */
    data object NoCustomerForCredit : SaleResult
}

sealed interface SaleWarning {
    data class SoldBelowStock(val sku: String, val onHand: Int, val sold: Int) : SaleWarning
}

/**
 * Takes payment and commits everything that follows.
 *
 * Permission is checked **here**, not in the screen: hiding a button is a usability affordance, and
 * on an offline desktop app the user owns the machine the UI runs on. A seller has `SELL`; a
 * discount or an overridden price needs someone who has `DISCOUNT_LINE` or `OVERRIDE_PRICE`, which
 * is what [Basket.lines]' `authorisedByUserId` records.
 *
 * Printing is deliberately not here. A sale that committed but did not print can be reprinted; a
 * sale that printed but did not commit is a customer holding a receipt for a transaction the shop
 * has no record of.
 */
class CompleteSaleUseCase(
    private val sales: ISaleRepository,
    private val sessions: ISessionManager,
    private val users: IUserRepository,
    private val customers: ICustomerRepository,
    private val receivables: IReceivablesRepository,
    private val calculate: CalculateBasketTotalUseCase,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {

    suspend operator fun invoke(
        basket: Basket,
        tenders: List<Tender>,
        context: TillContext,
        shiftId: String?,
        vatBasisPoints: Int,
        customerId: String? = null,
        creditAuthorisedByUserId: String? = null,
    ): Result<SaleResult> = runCatching {
        sessions.require(Permission.SELL)
        if (basket.isEmpty) return@runCatching SaleResult.EmptyBasket

        val user = requireNotNull(sessions.current.value) { "no session" }.user
        requireAuthorisation(basket)

        val totals = calculate(basket, vatBasisPoints)
        val settled = tenders.fold(Money.ZERO) { sum, tender -> sum + tender.amount }
        if (settled < totals.total) {
            return@runCatching SaleResult.UnderTendered(totals.total - settled)
        }

        val onAccount = tenders
            .filter { it.method == TenderMethod.CREDIT }
            .fold(Money.ZERO) { sum, tender -> sum + tender.amount }
        if (!onAccount.isZero) {
            // Nothing is written until the limit has been checked against the real balance.
            checkCredit(customerId, onAccount, creditAuthorisedByUserId)
                ?.let { return@runCatching it }
        }

        val saleId = ids.newId()
        val timestamp = now()
        val handedOver = tenders.fold(Money.ZERO) { sum, tender -> sum + tender.tendered }

        val draft = SaleDraft(
            id = saleId,
            locationId = context.locationId,
            priceListId = context.priceListId,
            userId = user.id,
            shiftId = shiftId,
            customerId = customerId,
            creditAuthorisedByUserId = creditAuthorisedByUserId,
            subtotal = totals.subtotal,
            discount = totals.discount,
            tax = totals.tax,
            total = totals.total,
            tendered = handedOver,
            change = handedOver - totals.total,
            occurredAt = timestamp,
            lines = totals.lines.mapIndexed { index, allocated ->
                SaleLine(
                    id = ids.newId(),
                    saleId = saleId,
                    lineNumber = index + 1,
                    variantId = allocated.line.variantId,
                    description = allocated.line.description,
                    descriptionAr = allocated.line.descriptionAr,
                    quantity = allocated.line.quantity,
                    unitPrice = allocated.line.unitPrice,
                    lineDiscount = allocated.line.lineDiscount,
                    orderDiscount = allocated.orderDiscount,
                    lineTotal = allocated.lineTotal,
                    tax = allocated.tax,
                    unitCost = allocated.line.unitCost,
                    authorisedByUserId = allocated.line.authorisedByUserId,
                )
            },
            payments = tenders.map { tender ->
                Payment(
                    id = ids.newId(),
                    saleId = saleId,
                    method = tender.method,
                    amount = tender.amount,
                    tendered = tender.tendered,
                    reference = tender.reference,
                    occurredAt = timestamp,
                )
            },
        )

        SaleResult.Completed(sales.record(draft).getOrThrow(), basket.warnings())
    }

    /**
     * The credit limit, checked against the balance as it actually is.
     *
     * Returns the refusal to report, or null to proceed. An approval from somebody with
     * `VOID_SALE`-level authority lets it through, and is recorded on the ledger entry — an
     * over-limit sale nobody can trace is not a control.
     */
    private suspend fun checkCredit(
        customerId: String?,
        amount: Money,
        approvedByUserId: String?,
    ): SaleResult? {
        if (customerId == null) return SaleResult.NoCustomerForCredit

        val customer = customers.getById(customerId).getOrThrow()
            ?: return SaleResult.NoCustomerForCredit
        if (CreditPolicy.isCashOnly(customer.creditLimit)) return SaleResult.CustomerIsCashOnly

        val balance = receivables.balance(customerId).getOrThrow()
        if (!CreditPolicy.wouldExceed(balance, customer.creditLimit, amount)) return null

        // Over the limit: only a verified approval gets past, and it is looked up, not trusted.
        if (approvedByUserId != null) {
            val approver = users.findById(approvedByUserId).getOrThrow()
                ?: throw Error.ForbiddenAccess("approval names a user who does not exist")
            if (Permission.VOID_SALE in approver.user.role.permissions) return null
            throw Error.ForbiddenAccess("approver cannot grant credit over the limit")
        }

        return SaleResult.OverCreditLimit(
            balance = balance,
            limit = customer.creditLimit,
            over = CreditPolicy.excess(balance, customer.creditLimit, amount),
        )
    }

    /**
     * A discount or an overridden price needs a permission the person at the till may not have.
     *
     * The line carries whoever approved it, so this passes when a seller had an admin authorise it
     * in place — the flow that actually happens in a shop, rather than signing the seller out and
     * back in around every markdown.
     *
     * **The recorded approval is looked up, not trusted.** An id in a field is a claim the UI
     * makes; one bug away from a discount nobody approved sitting in the ledger for good.
     */
    private suspend fun requireAuthorisation(basket: Basket) {
        basket.lines.forEach { line ->
            if (line.isDiscounted) approve(Permission.DISCOUNT_LINE, line.authorisedByUserId)
            if (line.isPriceOverridden) approve(Permission.OVERRIDE_PRICE, line.authorisedByUserId)
        }
        if (!basket.orderDiscount.isZero) {
            approve(Permission.DISCOUNT_LINE, basket.orderDiscountAuthorisedByUserId)
        }
    }

    private suspend fun approve(permission: Permission, authorisedByUserId: String?) {
        if (authorisedByUserId == null) {
            sessions.current.value.require(permission)
            return
        }
        val approver = users.findById(authorisedByUserId).getOrThrow()
            ?: throw Error.ForbiddenAccess("approval names a user who does not exist")
        if (permission !in approver.user.role.permissions) {
            throw Error.ForbiddenAccess("approver cannot grant ${permission.name}")
        }
    }

    private fun Basket.warnings(): List<SaleWarning> = lines
        .filter { it.exceedsStock }
        .map { SaleWarning.SoldBelowStock(it.sku, it.onHand, it.quantity) }
}

/**
 * Reverses a completed sale.
 *
 * Takes the user who approved it rather than reading the session, because the Phase 4 rule is that
 * a void needs a fresh credential **regardless of who is signed in** — see [ReauthenticateUseCase].
 * Voiding is the one action at a till that destroys revenue, so it should cost somebody a
 * deliberate act rather than being reachable by whoever happens to be standing there.
 *
 * The approver's permission is checked here too: a use case that trusts its caller to have done
 * the check is a use case with no check.
 */
class VoidSaleUseCase(
    private val sales: ISaleRepository,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        saleId: String,
        reason: String,
        authorisedBy: User,
    ): Result<Sale> = runCatching {
        if (Permission.VOID_SALE !in authorisedBy.role.permissions) {
            throw Error.ForbiddenAccess("not permitted: ${Permission.VOID_SALE.name}")
        }
        if (reason.isBlank()) throw Error.InvalidData("a void needs a reason")

        sales.void(saleId, authorisedBy.id, reason.trim(), now()).getOrThrow()
    }
}
