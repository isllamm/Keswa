package com.alsoug.keswa.features.wholesale.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Ageing
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.LedgerEntry
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.model.PriceList
import com.alsoug.keswa.core.domain.model.PriceListType
import com.alsoug.keswa.core.domain.model.Statement
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ICustomerRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.IReceivablesRepository
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.wholesale.domain.usecase.CreateCustomerUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.EnsureTradePriceListUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.GetCustomerAccountUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.ListCustomersUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.SearchCustomersUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.TakePaymentOnAccountUseCase
import com.alsoug.keswa.features.wholesale.presentation.screens.customers.CustomersUiEffect
import com.alsoug.keswa.features.wholesale.presentation.screens.customers.CustomersUiEvent
import com.alsoug.keswa.features.wholesale.presentation.screens.customers.CustomersViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}

private class FakeCustomerRepository : ICustomerRepository {
    val stored = mutableMapOf<String, Customer>()

    override suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        phone: String?,
        priceListId: String,
        creditLimit: Money,
        paymentTermsDays: Int,
    ): Result<Customer> {
        val c = Customer(
            id = id,
            name = name,
            nameAr = nameAr,
            phone = phone,
            taxId = null,
            priceListId = priceListId,
            creditLimit = creditLimit,
            paymentTermsDays = paymentTermsDays,
            isActive = true,
        )
        stored[id] = c
        return Result.success(c)
    }

    override suspend fun update(customer: Customer): Result<Unit> {
        stored[customer.id] = customer
        return Result.success(Unit)
    }

    override suspend fun getById(id: String): Result<Customer?> = Result.success(stored[id])

    override suspend fun getAll(): Result<List<Customer>> = Result.success(stored.values.toList())

    override suspend fun search(term: String, limit: Int): Result<List<Customer>> =
        Result.success(stored.values.filter { it.name.contains(term, ignoreCase = true) || it.nameAr.contains(term) })

    override fun observeAll(): Flow<List<Customer>> = flowOf(stored.values.toList())
}

private class FakeReceivablesRepository : IReceivablesRepository {
    val entries = mutableListOf<LedgerEntry>()

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
    ): Result<LedgerEntry> {
        val entry = LedgerEntry(
            id = id,
            customerId = customerId,
            type = type,
            amount = amount,
            refType = refType,
            refId = refId,
            occurredAt = occurredAt,
            dueAt = dueAt,
            userId = userId,
            note = note,
            authorisedByUserId = authorisedByUserId,
        )
        entries += entry
        return Result.success(entry)
    }

    override suspend fun balance(customerId: String): Result<Money> =
        Result.success(entries.filter { it.customerId == customerId }.fold(Money.ZERO) { acc, e -> acc + e.amount })

    override suspend fun statement(customerId: String, from: Long, to: Long): Result<Statement?> {
        val custEntries = entries.filter { it.customerId == customerId }
        val balance = custEntries.fold(Money.ZERO) { acc, e -> acc + e.amount }
        return Result.success(
            Statement(
                customer = Customer(
                    id = customerId,
                    name = "Customer",
                    nameAr = "عميل",
                    phone = null,
                    taxId = null,
                    priceListId = "pl-trade",
                    creditLimit = Money.ofPounds(1000),
                    paymentTermsDays = 30,
                    isActive = true,
                ),
                openingBalance = Money.ZERO,
                entries = custEntries,
                closingBalance = balance,
            ),
        )
    }

    override suspend fun ageing(customerId: String, nowMillis: Long): Result<Ageing> =
        Result.success(Ageing(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))

    override suspend fun customersOwing(): Result<List<Customer>> = Result.success(emptyList())

    override suspend fun entriesFor(refType: String, refId: String): Result<List<LedgerEntry>> =
        Result.success(entries.filter { it.refType == refType && it.refId == refId })
}

private class FakeWholesalePriceRepository : IPriceRepository {
    override suspend fun defaultList(): Result<PriceList?> =
        Result.success(PriceList("pl-retail", "Retail", "قطاعي", PriceListType.RETAIL, isDefault = true, isActive = true))
    override suspend fun ensureDefaultList(id: String, name: String, nameAr: String): Result<PriceList> =
        Result.success(PriceList(id, name, nameAr, PriceListType.RETAIL, isDefault = true, isActive = true))
    override suspend fun listsOfType(type: PriceListType): Result<List<PriceList>> =
        Result.success(listOf(PriceList("pl-trade", "Trade", "الجملة", PriceListType.WHOLESALE, isDefault = false, isActive = true)))
    override suspend fun createList(id: String, name: String, nameAr: String, type: PriceListType): Result<PriceList> =
        Result.success(PriceList(id, name, nameAr, type, isDefault = false, isActive = true))
    override suspend fun effectivePrice(variantId: String, priceListId: String, at: Long): Result<Money?> = Result.success(null)
    override suspend fun setPrice(id: String, variantId: String, priceListId: String, price: Money, from: Long): Result<Unit> = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = scheduler
        override val main: CoroutineDispatcher = scheduler
        override val default: CoroutineDispatcher = scheduler
    }

    private val now = { 1_757_000_000_000L }
    private val customers = FakeCustomerRepository()
    private val receivables = FakeReceivablesRepository()
    private val prices = FakeWholesalePriceRepository()
    private val sessions = InMemorySessionManager()
    private val ids = SequentialIds("cust")

    private val admin = User("admin-1", "admin", "Admin", "مسؤول", UserRole.ADMIN)

    private fun createViewModel() = CustomersViewModel(
        listCustomers = ListCustomersUseCase(customers),
        searchCustomers = SearchCustomersUseCase(customers),
        account = GetCustomerAccountUseCase(customers, receivables, now),
        createCustomer = CreateCustomerUseCase(customers, EnsureTradePriceListUseCase(prices), sessions, ids),
        takePayment = TakePaymentOnAccountUseCase(receivables, sessions, SequentialIds("pay"), now),
        receivables = receivables,
        now = now,
        dispatchers = dispatchers,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(scheduler)
        sessions.signIn(admin, now())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load populates customer list into state`() = runTest(scheduler) {
        customers.create("c-1", "Al-Amal Boutique", "بوتيك الأمل", "0100000001", "pl-trade", Money.ofPounds(5000), 30)
        customers.create("c-2", "Nour Stores", "محلات نور", "0100000002", "pl-trade", Money.ofPounds(10000), 45)

        val vm = createViewModel()
        vm.onEvent(CustomersUiEvent.Load)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.customers.size)
        assertEquals("c-1", state.customers.first().customer.id)
    }

    @Test
    fun `search filters customers by name`() = runTest(scheduler) {
        customers.create("c-1", "Al-Amal Boutique", "بوتيك الأمل", "0100000001", "pl-trade", Money.ofPounds(5000), 30)
        customers.create("c-2", "Nour Stores", "محلات نور", "0100000002", "pl-trade", Money.ofPounds(10000), 45)

        val vm = createViewModel()
        vm.onEvent(CustomersUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(CustomersUiEvent.QueryChanged("Nour"))
        vm.onEvent(CustomersUiEvent.Search)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.customers.size)
        assertEquals("c-2", vm.state.value.customers.single().customer.id)
    }

    @Test
    fun `select customer loads account and statement entries`() = runTest(scheduler) {
        customers.create("c-1", "Al-Amal Boutique", "بوتيك الأمل", "0100000001", "pl-trade", Money.ofPounds(5000), 30)
        receivables.record("e-1", "c-1", LedgerEntryType.INVOICE, Money.ofPounds(1200), null, null, now(), null, "admin-1", null, null)

        val vm = createViewModel()
        vm.onEvent(CustomersUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(CustomersUiEvent.Select("c-1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.selected)
        assertEquals("c-1", state.selected.id)
        assertEquals(Money.ofPounds(1200), state.balance)
        assertEquals(1, state.entries.size)
    }

    @Test
    fun `deselect clears selected customer and ledger entries`() = runTest(scheduler) {
        customers.create("c-1", "Al-Amal Boutique", "بوتيك الأمل", "0100000001", "pl-trade", Money.ofPounds(5000), 30)

        val vm = createViewModel()
        vm.onEvent(CustomersUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(CustomersUiEvent.Select("c-1"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.selected)

        vm.onEvent(CustomersUiEvent.Deselect)
        assertNull(vm.state.value.selected)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test
    fun `take payment records credit payment and updates statement`() = runTest(scheduler) {
        customers.create("c-1", "Al-Amal Boutique", "بوتيك الأمل", "0100000001", "pl-trade", Money.ofPounds(5000), 30)
        receivables.record("e-1", "c-1", LedgerEntryType.INVOICE, Money.ofPounds(2000), null, null, now(), null, "admin-1", null, null)

        val vm = createViewModel()
        val effects = mutableListOf<CustomersUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(CustomersUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(CustomersUiEvent.Select("c-1"))
        advanceUntilIdle()

        vm.onEvent(CustomersUiEvent.PaymentChanged("500.00"))
        vm.onEvent(CustomersUiEvent.PaymentNoteChanged("Partial cash payment"))
        vm.onEvent(CustomersUiEvent.TakePayment)
        advanceUntilIdle()

        assertEquals(2, receivables.entries.size)
        val payment = receivables.entries.last()
        assertEquals(LedgerEntryType.PAYMENT, payment.type)
        assertEquals(-Money.ofPounds(500), payment.amount)
        assertEquals("Partial cash payment", payment.note)

        assertEquals(Money.ofPounds(1500), vm.state.value.balance)
        assertEquals("", vm.state.value.paymentEntry)
        job.cancel()
    }

    @Test
    fun `create customer adds new customer to repository and state`() = runTest(scheduler) {
        val vm = createViewModel()

        vm.onEvent(CustomersUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(CustomersUiEvent.StartCreating)
        assertTrue(vm.state.value.isCreating)

        vm.onEvent(CustomersUiEvent.NewNameChanged("Cairo Fashion"))
        vm.onEvent(CustomersUiEvent.NewPhoneChanged("0111222333"))
        vm.onEvent(CustomersUiEvent.NewLimitChanged("8000"))
        vm.onEvent(CustomersUiEvent.NewTermsChanged("60"))
        vm.onEvent(CustomersUiEvent.Create)
        advanceUntilIdle()

        assertFalse(vm.state.value.isCreating)
        assertEquals(1, customers.stored.size)
        val created = customers.stored.values.first()
        assertEquals("Cairo Fashion", created.name)
        assertEquals(Money.ofPounds(8000), created.creditLimit)
        assertEquals(60, created.paymentTermsDays)

        assertEquals(1, vm.state.value.customers.size)
        assertEquals(created.id, vm.state.value.customers.first().customer.id)
    }
}
