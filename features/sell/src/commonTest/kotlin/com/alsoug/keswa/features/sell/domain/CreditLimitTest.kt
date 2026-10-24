package com.alsoug.keswa.features.sell.domain

import com.alsoug.keswa.core.domain.model.Ageing
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.LedgerEntry
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.model.Statement
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ICustomerRepository
import com.alsoug.keswa.core.domain.repository.IReceivablesRepository
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.usecase.CalculateBasketTotalUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CompleteSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.SaleResult
import com.alsoug.keswa.features.sell.domain.usecase.Tender
import com.alsoug.keswa.features.sell.domain.usecase.TillContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

internal class FakeCustomers(private val customer: Customer?) : ICustomerRepository {
    override suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        phone: String?,
        priceListId: String,
        creditLimit: Money,
        paymentTermsDays: Int,
    ): Result<Customer> = error("not used")

    override suspend fun update(customer: Customer): Result<Unit> = error("not used")

    override suspend fun getById(id: String): Result<Customer?> =
        Result.success(customer?.takeIf { it.id == id })

    override suspend fun getAll(): Result<List<Customer>> = Result.success(listOfNotNull(customer))

    override suspend fun search(term: String, limit: Int): Result<List<Customer>> =
        Result.success(emptyList())

    override fun observeAll(): Flow<List<Customer>> = flowOf(listOfNotNull(customer))
}

internal class FakeReceivables(private val balance: Money) : IReceivablesRepository {
    override suspend fun record(
        id: String,
        customerId: String,
        type: LedgerEntryType,
        amount: Money,
        refType: String?,
        refId: String?,
        occurredAt: Long,
        dueAt: Long?,
        userId: String,
        note: String?,
        authorisedByUserId: String?,
    ): Result<LedgerEntry> = error("the sale's own transaction writes this")

    override suspend fun balance(customerId: String): Result<Money> = Result.success(balance)

    override suspend fun statement(
        customerId: String,
        from: Long,
        to: Long,
    ): Result<Statement?> = Result.success(null)

    override suspend fun ageing(customerId: String, nowMillis: Long): Result<Ageing> =
        Result.success(Ageing.NOTHING)

    override suspend fun customersOwing(): Result<List<Customer>> = Result.success(emptyList())

    override suspend fun entriesFor(
        refType: String,
        refId: String,
    ): Result<List<LedgerEntry>> = Result.success(emptyList())
}

/**
 * The credit limit as a **hard stop**.
 *
 * A limit that warns is a limit that gets clicked through on a busy morning, and the busy morning
 * is the entire reason it exists. This is the plan's check 3, and it costs real money when wrong.
 */
class CreditLimitTest {

    private val sales = RecordingSaleRepository()
    private val users = FakeUserRepository()
    private val sessions = InMemorySessionManager()
    private val till = TillContext(locationId = "loc-shop", priceListId = "pricelist-trade")

    private val seller = User("usr-seller", "sara", "Sara", "سارة", UserRole.SELLER)
    private val admin = User("usr-admin", "owner", "Owner", "المالك", UserRole.ADMIN)

    private fun customer(limit: Long) = Customer(
        id = "cus-1",
        name = "Nasr Textiles",
        nameAr = "نصر للنسيج",
        phone = null,
        taxId = null,
        priceListId = "pricelist-trade",
        creditLimit = Money.ofPounds(limit),
        paymentTermsDays = 30,
        isActive = true,
    )

    private fun complete(limit: Long, balance: Long) = CompleteSaleUseCase(
        sales = sales,
        sessions = sessions,
        users = users,
        customers = FakeCustomers(customer(limit)),
        receivables = FakeReceivables(Money.ofPounds(balance)),
        calculate = CalculateBasketTotalUseCase(),
        ids = SequentialIds("sale"),
        now = { 1_757_000_000_000 },
    )

    private fun basket() = Basket().add(sellable(price = Money.ofPounds(180)), quantity = 2)

    private fun onAccount(amount: Long = 360) =
        listOf(Tender(TenderMethod.CREDIT, Money.ofPounds(amount), Money.ZERO))

    @Test
    fun `inside the limit a credit sale goes through`() = runTest {
        sessions.signIn(seller, 0)

        val result = complete(limit = 50_000, balance = 1_000)(
            basket = basket(),
            tenders = onAccount(),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
            customerId = "cus-1",
        ).getOrThrow()

        assertIs<SaleResult.Completed>(result)
        assertEquals("cus-1", sales.recorded.single().customerId)
    }

    @Test
    fun `over the limit it is refused, not warned`() = runTest {
        sessions.signIn(seller, 0)

        val result = complete(limit = 1_000, balance = 900)(
            basket = basket(),
            tenders = onAccount(),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
            customerId = "cus-1",
        ).getOrThrow()

        val refused = assertIs<SaleResult.OverCreditLimit>(result)
        assertEquals(Money.ofPounds(260), refused.over)
        assertTrue(sales.recorded.isEmpty(), "nothing may reach the ledger")
    }

    @Test
    fun `an admin approval lets it past, and is recorded`() = runTest {
        sessions.signIn(seller, 0)
        users.add("usr-admin", "owner", UserRole.ADMIN, "s3cret")

        val result = complete(limit = 1_000, balance = 900)(
            basket = basket(),
            tenders = onAccount(),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
            customerId = "cus-1",
            creditAuthorisedByUserId = "usr-admin",
        ).getOrThrow()

        assertIs<SaleResult.Completed>(result)
        // An over-limit sale nobody can trace is not a control.
        assertEquals("usr-admin", sales.recorded.single().creditAuthorisedByUserId)
    }

    @Test
    fun `an approval from somebody without the authority is refused`() = runTest {
        sessions.signIn(seller, 0)
        users.add("usr-other", "mo", UserRole.SELLER, "1234")

        val thrown = complete(limit = 1_000, balance = 900)(
            basket = basket(),
            tenders = onAccount(),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
            customerId = "cus-1",
            creditAuthorisedByUserId = "usr-other",
        ).exceptionOrNull()

        assertIs<Error.ForbiddenAccess>(thrown)
        assertTrue(sales.recorded.isEmpty())
    }

    @Test
    fun `a zero limit means cash only, whoever is asking`() = runTest {
        sessions.signIn(admin, 0)

        val result = complete(limit = 0, balance = 0)(
            basket = basket(),
            tenders = onAccount(),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
            customerId = "cus-1",
        ).getOrThrow()

        assertIs<SaleResult.CustomerIsCashOnly>(result)
        assertTrue(sales.recorded.isEmpty())
    }

    @Test
    fun `a credit tender with nobody to bill is refused`() = runTest {
        sessions.signIn(seller, 0)

        val result = complete(limit = 50_000, balance = 0)(
            basket = basket(),
            tenders = onAccount(),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
            customerId = null,
        ).getOrThrow()

        assertIs<SaleResult.NoCustomerForCredit>(result)
        assertTrue(sales.recorded.isEmpty())
    }

    @Test
    fun `a cash sale to a credit customer never touches the limit`() = runTest {
        sessions.signIn(seller, 0)

        // Over the limit on the account, but paying cash — nothing to check.
        val result = complete(limit = 1_000, balance = 5_000)(
            basket = basket(),
            tenders = listOf(Tender(TenderMethod.CASH, Money.ofPounds(360), Money.ofPounds(360))),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
            customerId = "cus-1",
        ).getOrThrow()

        assertIs<SaleResult.Completed>(result)
    }

    @Test
    fun `landing exactly on the limit is allowed`() = runTest {
        sessions.signIn(seller, 0)

        // 640 owed plus 360 is exactly 1,000. A limit is a ceiling, not a fence short of one.
        val result = complete(limit = 1_000, balance = 640)(
            basket = basket(),
            tenders = onAccount(),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
            customerId = "cus-1",
        ).getOrThrow()

        assertIs<SaleResult.Completed>(result)
    }
}
