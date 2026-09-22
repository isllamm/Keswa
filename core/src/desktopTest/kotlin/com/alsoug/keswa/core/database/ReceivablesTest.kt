package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.data.repository.CustomerRepositoryImpl
import com.alsoug.keswa.core.data.repository.ReceivablesRepositoryImpl
import com.alsoug.keswa.core.database.entities.PriceListEntity
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.model.PriceListType
import com.alsoug.keswa.core.domain.money.Money
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * What customers owe, as a sum over an append-only ledger.
 *
 * The same design as the stock ledger and for the same reasons — so the interesting tests are the
 * same ones: the balance walks, corrections are entries rather than edits, and nothing needs a
 * stored figure to stay in step with.
 */
class ReceivablesTest {

    private val database = createTestDatabase()
    private val customers = CustomerRepositoryImpl(database.customerDao()) { NOW }
    private val receivables = ReceivablesRepositoryImpl(
        database.customerLedgerDao(),
        database.customerDao(),
    )

    @AfterTest
    fun tearDown() = database.close()

    private companion object {
        const val NOW = 1_757_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
        const val TRADE_LIST = "pricelist-trade"
    }

    private suspend fun customer(limit: Long = 50_000, terms: Int = 30): String {
        database.priceDao().upsertList(
            PriceListEntity(TRADE_LIST, "Trade", "الجملة", PriceListType.WHOLESALE, false, true),
        )
        return customers.create(
            id = "cus-1",
            name = "Nasr Textiles",
            nameAr = "نصر للنسيج",
            phone = "0100",
            priceListId = TRADE_LIST,
            creditLimit = Money.ofPounds(limit),
            paymentTermsDays = terms,
        ).getOrThrow().id
    }

    private suspend fun entry(
        id: String,
        customerId: String,
        type: LedgerEntryType,
        amount: Money,
        at: Long = NOW,
        dueAt: Long? = null,
    ) = receivables.record(
        id = id,
        customerId = customerId,
        type = type,
        amount = amount,
        refType = null,
        refId = null,
        occurredAt = at,
        dueAt = dueAt,
        userId = "usr-1",
        note = null,
    ).getOrThrow()

    @Test
    fun `the balance is the sum of the entries and nothing else`() = runBlocking {
        val id = customer()

        entry("e1", id, LedgerEntryType.INVOICE, Money.ofPounds(18_400))
        entry("e2", id, LedgerEntryType.PAYMENT, Money.ofPounds(-4_200))
        entry("e3", id, LedgerEntryType.INVOICE, Money.ofPounds(6_000))
        entry("e4", id, LedgerEntryType.CREDIT_NOTE, Money.ofPounds(-1_000))

        assertEquals(Money.ofPounds(19_200), receivables.balance(id).getOrThrow())
    }

    @Test
    fun `a customer who has never traded owes nothing`() = runBlocking {
        val id = customer()

        assertEquals(Money.ZERO, receivables.balance(id).getOrThrow())
        assertEquals(com.alsoug.keswa.core.domain.model.Ageing.NOTHING, receivables.ageing(id, NOW).getOrThrow())
    }

    @Test
    fun `available credit falls as the balance rises`() = runBlocking {
        val id = customer(limit = 50_000)
        entry("e1", id, LedgerEntryType.INVOICE, Money.ofPounds(18_400))

        val found = customers.getById(id).getOrThrow()!!
        val balance = receivables.balance(id).getOrThrow()

        assertEquals(Money.ofPounds(31_600), found.availableCredit(balance))
    }

    @Test
    fun `available credit never goes negative`() = runBlocking {
        val id = customer(limit = 10_000)
        entry("e1", id, LedgerEntryType.INVOICE, Money.ofPounds(18_400))

        val found = customers.getById(id).getOrThrow()!!
        // Over the limit is a state the shop can be in; a negative "available" is not a number
        // anybody can act on.
        assertEquals(Money.ZERO, found.availableCredit(receivables.balance(id).getOrThrow()))
    }

    @Test
    fun `a zero limit means cash only`() = runBlocking {
        val id = customer(limit = 0)

        assertTrue(!customers.getById(id).getOrThrow()!!.sellsOnAccount)
    }

    @Test
    fun `ageing puts each debit in the bucket its own due date says`() = runBlocking {
        val id = customer(terms = 30)

        // Due in the future, 15 days over, 45 over, and 100 over.
        entry("e1", id, LedgerEntryType.INVOICE, Money.ofPounds(1_000), NOW - DAY, NOW + 10 * DAY)
        entry("e2", id, LedgerEntryType.INVOICE, Money.ofPounds(2_000), NOW - 20 * DAY, NOW - 15 * DAY)
        entry("e3", id, LedgerEntryType.INVOICE, Money.ofPounds(3_000), NOW - 60 * DAY, NOW - 45 * DAY)
        entry("e4", id, LedgerEntryType.INVOICE, Money.ofPounds(4_000), NOW - 130 * DAY, NOW - 100 * DAY)

        val ageing = receivables.ageing(id, NOW).getOrThrow()

        assertEquals(Money.ofPounds(1_000), ageing.current)
        assertEquals(Money.ofPounds(2_000), ageing.thirtyDays)
        assertEquals(Money.ofPounds(3_000), ageing.sixtyDays)
        assertEquals(Money.ofPounds(4_000), ageing.ninetyDaysPlus)
        assertEquals(Money.ofPounds(10_000), ageing.total)
        assertEquals(Money.ofPounds(9_000), ageing.overdue)
    }

    @Test
    fun `a payment settles the oldest debt first`() = runBlocking {
        val id = customer()
        entry("e1", id, LedgerEntryType.INVOICE, Money.ofPounds(3_000), NOW - 130 * DAY, NOW - 100 * DAY)
        entry("e2", id, LedgerEntryType.INVOICE, Money.ofPounds(5_000), NOW - 20 * DAY, NOW - 15 * DAY)

        entry("e3", id, LedgerEntryType.PAYMENT, Money.ofPounds(-3_000), NOW)

        val ageing = receivables.ageing(id, NOW).getOrThrow()

        // The oldest is cleared, the recent one stands — the conventional allocation, and a *view*:
        // the ledger only records that money arrived.
        assertEquals(Money.ZERO, ageing.ninetyDaysPlus)
        assertEquals(Money.ofPounds(5_000), ageing.thirtyDays)
    }

    @Test
    fun `paying more than is owed leaves nothing overdue`() = runBlocking {
        val id = customer()
        entry("e1", id, LedgerEntryType.INVOICE, Money.ofPounds(3_000), NOW - 130 * DAY, NOW - 100 * DAY)
        entry("e2", id, LedgerEntryType.PAYMENT, Money.ofPounds(-5_000), NOW)

        assertEquals(Money.ofPounds(-2_000), receivables.balance(id).getOrThrow())
        assertEquals(Money.ZERO, receivables.ageing(id, NOW).getOrThrow().total)
    }

    @Test
    fun `a statement's arithmetic closes`() = runBlocking {
        val id = customer()
        entry("e1", id, LedgerEntryType.INVOICE, Money.ofPounds(10_000), NOW - 60 * DAY)
        entry("e2", id, LedgerEntryType.PAYMENT, Money.ofPounds(-4_000), NOW - 30 * DAY)
        entry("e3", id, LedgerEntryType.INVOICE, Money.ofPounds(2_500), NOW - 10 * DAY)

        val statement = receivables.statement(id, NOW - 40 * DAY, NOW).getOrThrow()!!

        // Opening plus the period's movement equals closing — so a reader never has to take the
        // first number on trust.
        assertEquals(Money.ofPounds(10_000), statement.openingBalance)
        assertEquals(Money.ofPounds(8_500), statement.closingBalance)
        assertEquals(2, statement.entries.size)
        assertEquals(Money.ofPounds(2_500), statement.invoiced)
        assertEquals(Money.ofPounds(-4_000), statement.paid)
    }

    @Test
    fun `only customers who owe something appear in the debtor list`() = runBlocking {
        val owing = customer()
        database.customerDao().upsert(
            database.customerDao().getById(owing)!!.copy(id = "cus-2", name = "Paid Up"),
        )
        entry("e1", owing, LedgerEntryType.INVOICE, Money.ofPounds(1_000))
        entry("e2", "cus-2", LedgerEntryType.INVOICE, Money.ofPounds(1_000))
        entry("e3", "cus-2", LedgerEntryType.PAYMENT, Money.ofPounds(-1_000))

        val debtors = receivables.customersOwing().getOrThrow()

        assertEquals(listOf(owing), debtors.map { it.id })
    }

    @Test
    fun `an entry of zero is refused`() = runBlocking {
        val id = customer()

        assertTrue(
            receivables.record(
                "e1", id, LedgerEntryType.ADJUSTMENT, Money.ZERO,
                null, null, NOW, null, "usr-1", null,
            ).isFailure,
        )
    }

    @Test
    fun `an entry for somebody who does not exist is refused`() = runBlocking {
        assertTrue(
            receivables.record(
                "e1", "nobody", LedgerEntryType.INVOICE, Money.ofPounds(100),
                null, null, NOW, null, "usr-1", null,
            ).isFailure,
        )
    }
}
