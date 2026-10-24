package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.Ageing
import com.alsoug.keswa.core.domain.model.AssortmentPack
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.LedgerEntry
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.model.Statement
import com.alsoug.keswa.core.domain.money.Money
import kotlinx.coroutines.flow.Flow

interface ICustomerRepository {

    suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        phone: String?,
        priceListId: String,
        creditLimit: Money,
        paymentTermsDays: Int,
    ): Result<Customer>

    suspend fun update(customer: Customer): Result<Unit>

    suspend fun getById(id: String): Result<Customer?>

    suspend fun getAll(): Result<List<Customer>>

    suspend fun search(term: String, limit: Int = 25): Result<List<Customer>>

    fun observeAll(): Flow<List<Customer>>
}

/**
 * What customers owe.
 *
 * Append-only: there is a `record`, and there is no update and no delete. The balance is a sum and
 * has no other definition — which is what makes it auditable, mergeable and impossible to quietly
 * edit.
 */
interface IReceivablesRepository {

    suspend fun record(
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
        authorisedByUserId: String? = null,
    ): Result<LedgerEntry>

    suspend fun balance(customerId: String): Result<Money>

    suspend fun statement(customerId: String, from: Long, to: Long): Result<Statement?>

    /**
     * Overdue buckets, computed from each debit's own due date.
     *
     * Payments apply oldest-first — the convention, and a *view*: the ledger records that money
     * arrived, not which invoice somebody decided it belonged to.
     */
    suspend fun ageing(customerId: String, nowMillis: Long): Result<Ageing>

    suspend fun customersOwing(): Result<List<Customer>>

    suspend fun entriesFor(refType: String, refId: String): Result<List<LedgerEntry>>
}

interface IAssortmentPackRepository {

    suspend fun create(
        id: String,
        name: String,
        nameAr: String,
        price: Money,
    ): Result<AssortmentPack>

    suspend fun addLine(id: String, packId: String, variantId: String, quantity: Int): Result<AssortmentPack>

    suspend fun removeLine(packId: String, lineId: String): Result<AssortmentPack>

    suspend fun getById(id: String): Result<AssortmentPack?>

    suspend fun getAll(): Result<List<AssortmentPack>>
}
