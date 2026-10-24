package com.alsoug.keswa.features.wholesale.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.PriceListType
import com.alsoug.keswa.core.domain.model.Statement
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ICustomerRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.IReceivablesRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Creates a customer.
 *
 * The credit limit defaults to **zero — cash only**. A customer nobody has yet decided to trust is
 * a customer who pays now, and making that the default means trust is granted deliberately rather
 * than inherited from a form's blank field.
 */
class CreateCustomerUseCase(
    private val customers: ICustomerRepository,
    private val tradeList: EnsureTradePriceListUseCase,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        name: String,
        nameAr: String,
        phone: String?,
        creditLimit: Money = Money.ZERO,
        paymentTermsDays: Int = DEFAULT_TERMS_DAYS,
        priceListId: String? = null,
    ): Result<Customer> = runCatching {
        sessions.require(Permission.MANAGE_CATALOGUE)

        // The trade list by default, created if this is the first wholesale customer. A trade
        // customer silently buying at retail prices is a bug that shows up as an argument.
        val list = priceListId ?: tradeList().getOrThrow()

        customers.create(ids.newId(), name, nameAr, phone, list, creditLimit, paymentTermsDays)
            .getOrThrow()
    }

    private companion object {
        /** Net 30 — the default in the trade, and changeable per customer. */
        const val DEFAULT_TERMS_DAYS = 30
    }
}

/**
 * Changes a customer's terms, limit or price list.
 *
 * `MANAGE_USERS` rather than `MANAGE_CATALOGUE`: raising somebody's credit limit is an act of
 * trust with money attached, not a catalogue edit.
 */
class UpdateCustomerUseCase(
    private val customers: ICustomerRepository,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(customer: Customer): Result<Unit> = runCatching {
        sessions.require(Permission.MANAGE_USERS)
        customers.update(customer).getOrThrow()
    }
}

class ListCustomersUseCase(private val customers: ICustomerRepository) {
    suspend operator fun invoke(): Result<List<Customer>> = customers.getAll()
}

class SearchCustomersUseCase(private val customers: ICustomerRepository) {
    suspend operator fun invoke(term: String): Result<List<Customer>> = customers.search(term)
}

/** Everyone who owes something, worst first — the list an owner opens on a Sunday morning. */
class ListDebtorsUseCase(private val receivables: IReceivablesRepository) {
    suspend operator fun invoke(): Result<List<Customer>> = receivables.customersOwing()
}

class GetStatementUseCase(
    private val receivables: IReceivablesRepository,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(customerId: String, from: Long, to: Long): Result<Statement?> =
        runCatching {
            // What a customer owes is a business figure, not a till one.
            sessions.require(Permission.VIEW_SHOP_ANALYTICS)
            receivables.statement(customerId, from, to).getOrThrow()
        }
}

/** Ensures a WHOLESALE list exists, so a trade customer has somewhere to be put. */
class EnsureTradePriceListUseCase(private val prices: IPriceRepository) {
    suspend operator fun invoke(): Result<String> = runCatching {
        prices.listsOfType(PriceListType.WHOLESALE).getOrThrow().firstOrNull()?.id
            ?: prices.createList(TRADE_LIST_ID, "Trade", "الجملة", PriceListType.WHOLESALE)
                .getOrThrow().id
    }

    private companion object {
        const val TRADE_LIST_ID = "pricelist-trade"
    }
}
