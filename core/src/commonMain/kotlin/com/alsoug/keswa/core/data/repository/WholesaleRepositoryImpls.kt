package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.database.dao.AssortmentPackDao
import com.alsoug.keswa.core.database.dao.CustomerDao
import com.alsoug.keswa.core.database.dao.CustomerLedgerDao
import com.alsoug.keswa.core.database.entities.AssortmentPackEntity
import com.alsoug.keswa.core.database.entities.AssortmentPackLineEntity
import com.alsoug.keswa.core.database.entities.CustomerEntity
import com.alsoug.keswa.core.database.entities.CustomerLedgerEntryEntity
import com.alsoug.keswa.core.domain.model.Ageing
import com.alsoug.keswa.core.domain.model.AssortmentPack
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.LedgerEntry
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.model.Statement
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IAssortmentPackRepository
import com.alsoug.keswa.core.domain.repository.ICustomerRepository
import com.alsoug.keswa.core.domain.repository.IReceivablesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CustomerRepositoryImpl(
    private val dao: CustomerDao,
    private val now: () -> Long,
) : ICustomerRepository {

    override suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        phone: String?,
        priceListId: String,
        creditLimit: Money,
        paymentTermsDays: Int,
    ): Result<Customer> = runCatchingCancellable {
        require(name.isNotBlank()) { "a customer needs a name" }
        require(!creditLimit.isNegative) { "a credit limit cannot be negative" }
        require(paymentTermsDays >= 0) { "payment terms cannot be negative" }

        val timestamp = now()
        val entity = CustomerEntity(
            id = id,
            name = name.trim(),
            nameAr = nameAr.trim().ifBlank { name.trim() },
            phone = phone?.trim()?.ifBlank { null },
            taxId = null,
            priceListId = priceListId,
            creditLimitPiastres = creditLimit.piastres,
            paymentTermsDays = paymentTermsDays,
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        dao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun update(customer: Customer): Result<Unit> = runCatchingCancellable {
        val existing = requireNotNull(dao.getById(customer.id)) { "customer not found: ${customer.id}" }
        require(!customer.creditLimit.isNegative) { "a credit limit cannot be negative" }

        dao.upsert(
            existing.copy(
                name = customer.name.trim(),
                nameAr = customer.nameAr.trim(),
                phone = customer.phone,
                taxId = customer.taxId,
                priceListId = customer.priceListId,
                creditLimitPiastres = customer.creditLimit.piastres,
                paymentTermsDays = customer.paymentTermsDays,
                isActive = customer.isActive,
                updatedAt = now(),
            ),
        )
    }

    override suspend fun getById(id: String): Result<Customer?> =
        runCatchingCancellable { dao.getById(id)?.toDomain() }

    override suspend fun getAll(): Result<List<Customer>> =
        runCatchingCancellable { dao.getAll().map { it.toDomain() } }

    override suspend fun search(term: String, limit: Int): Result<List<Customer>> =
        runCatchingCancellable {
            val cleaned = term.trim()
            if (cleaned.isEmpty()) emptyList() else dao.search(cleaned, limit).map { it.toDomain() }
        }

    override fun observeAll(): Flow<List<Customer>> =
        dao.observeAll().map { customers -> customers.map { it.toDomain() } }
}

/**
 * The receivables ledger.
 *
 * One write method and no others: every figure this class returns is derived from the sum of the
 * entries, so there is nothing to keep in step and nothing to edit.
 */
class ReceivablesRepositoryImpl(
    private val dao: CustomerLedgerDao,
    private val customers: CustomerDao,
) : IReceivablesRepository {

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
    ): Result<LedgerEntry> = runCatchingCancellable {
        require(!amount.isZero) { "an entry of zero changes nothing" }
        requireNotNull(customers.getById(customerId)) { "customer not found: $customerId" }

        val entity = CustomerLedgerEntryEntity(
            id = id,
            customerId = customerId,
            entryType = type,
            amountPiastres = amount.piastres,
            refType = refType,
            refId = refId,
            occurredAt = occurredAt,
            dueAt = dueAt,
            userId = userId,
            note = note,
            authorisedByUserId = authorisedByUserId,
        )
        dao.insert(entity)
        entity.toDomain()
    }

    override suspend fun balance(customerId: String): Result<Money> =
        runCatchingCancellable { Money.ofPiastres(dao.balance(customerId)) }

    override suspend fun statement(
        customerId: String,
        from: Long,
        to: Long,
    ): Result<Statement?> = runCatchingCancellable {
        val customer = customers.getById(customerId)?.toDomain() ?: return@runCatchingCancellable null
        val entries = dao.statement(customerId, from, to).map { it.toDomain() }

        // Everything before the window, summed — so the statement's own arithmetic closes rather
        // than starting from a number the reader has to take on trust.
        val closing = Money.ofPiastres(dao.balance(customerId))
        val after = dao.allFor(customerId)
            .filter { it.occurredAt >= to }
            .fold(Money.ZERO) { sum, entry -> sum + Money.ofPiastres(entry.amountPiastres) }
        val movement = entries.fold(Money.ZERO) { sum, entry -> sum + entry.amount }

        Statement(
            customer = customer,
            openingBalance = closing - after - movement,
            entries = entries,
            closingBalance = closing - after,
        )
    }

    /**
     * Ageing, with payments applied oldest-first.
     *
     * The allocation happens here and is never stored: a customer pays 5,000 against four
     * invoices, and making the shop choose which one at the moment the money arrives gets it wrong
     * as often as not.
     */
    override suspend fun ageing(customerId: String, nowMillis: Long): Result<Ageing> =
        runCatchingCancellable {
            val debits = dao.debits(customerId)
            if (debits.isEmpty()) return@runCatchingCancellable Ageing.NOTHING

            // `credited` is negative; flip it into a pot of money to settle debits with.
            var unapplied = -dao.credited(customerId)

            var current = 0L
            var thirty = 0L
            var sixty = 0L
            var ninety = 0L

            debits.forEach { debit ->
                val applied = minOf(unapplied, debit.amountPiastres)
                unapplied -= applied
                val outstanding = debit.amountPiastres - applied
                if (outstanding <= 0) return@forEach

                val due = debit.dueAt ?: debit.occurredAt
                val daysOverdue = ((nowMillis - due) / MILLIS_PER_DAY).toInt()
                when {
                    daysOverdue <= 0 -> current += outstanding
                    daysOverdue <= 30 -> thirty += outstanding
                    daysOverdue <= 60 -> sixty += outstanding
                    else -> ninety += outstanding
                }
            }

            Ageing(
                current = Money.ofPiastres(current),
                thirtyDays = Money.ofPiastres(thirty),
                sixtyDays = Money.ofPiastres(sixty),
                ninetyDaysPlus = Money.ofPiastres(ninety),
            )
        }

    override suspend fun customersOwing(): Result<List<Customer>> = runCatchingCancellable {
        dao.customersOwing().mapNotNull { customers.getById(it)?.toDomain() }
    }

    override suspend fun entriesFor(refType: String, refId: String): Result<List<LedgerEntry>> =
        runCatchingCancellable { dao.forReference(refType, refId).map { it.toDomain() } }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

class AssortmentPackRepositoryImpl(
    private val dao: AssortmentPackDao,
) : IAssortmentPackRepository {

    override suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        price: Money,
    ): Result<AssortmentPack> = runCatchingCancellable {
        require(name.isNotBlank()) { "a pack needs a name" }
        require(!price.isNegative) { "a price cannot be negative" }

        val entity = AssortmentPackEntity(
            id = id,
            name = name.trim(),
            nameAr = nameAr.trim().ifBlank { name.trim() },
            pricePiastres = price.piastres,
            isActive = true,
        )
        dao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun addLine(
        id: String,
        packId: String,
        variantId: String,
        quantity: Int,
    ): Result<AssortmentPack> = runCatchingCancellable {
        require(quantity > 0) { "a pack line must contain something" }
        requireNotNull(dao.getById(packId)) { "pack not found: $packId" }

        // Adding the same variant twice sets the quantity rather than duplicating the line.
        val existing = dao.getLines(packId).firstOrNull { it.variantId == variantId }
        dao.upsertLine(
            AssortmentPackLineEntity(
                id = existing?.id ?: id,
                packId = packId,
                variantId = variantId,
                quantity = quantity,
            ),
        )
        read(packId)
    }

    override suspend fun removeLine(packId: String, lineId: String): Result<AssortmentPack> =
        runCatchingCancellable {
            dao.deleteLine(lineId)
            read(packId)
        }

    override suspend fun getById(id: String): Result<AssortmentPack?> =
        runCatchingCancellable { dao.getById(id)?.let { read(id) } }

    override suspend fun getAll(): Result<List<AssortmentPack>> =
        runCatchingCancellable { dao.getAll().map { read(it.id) } }

    private suspend fun read(id: String): AssortmentPack {
        val pack = requireNotNull(dao.getById(id)) { "pack not found: $id" }
        return pack.toDomain(dao.getLines(id).map { it.toDomain() })
    }
}
