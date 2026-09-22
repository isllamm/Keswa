package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.data.mapper.toEntity
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.dao.CustomerDao
import com.alsoug.keswa.core.database.dao.CustomerLedgerDao
import com.alsoug.keswa.core.database.dao.SaleDao
import com.alsoug.keswa.core.database.dao.StockLedgerDao
import com.alsoug.keswa.core.database.entities.CustomerLedgerEntryEntity
import com.alsoug.keswa.core.database.entities.SaleEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.inTransaction
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.SaleDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where a sale becomes permanent.
 *
 * Everything a sale implies — header, lines, tenders, stock, and from Phase 7 the receivable —
 * happens in one transaction. The stock movements and the ledger entry are both built here rather
 * than accepted from the caller, so there is no way to commit a sale without moving what it sold,
 * and no way to hand over goods on account without recording that they are owed for.
 */
class SaleRepositoryImpl(
    private val database: KeswaDatabase,
    private val dao: SaleDao,
    private val ledger: StockLedgerDao,
    private val receivables: CustomerLedgerDao,
    private val customers: CustomerDao,
    private val ids: IdGenerator,
) : ISaleRepository {

    override suspend fun record(draft: SaleDraft): Result<Sale> = runCatchingCancellable {
        require(draft.lines.isNotEmpty()) { "a sale must have at least one line" }

        database.inTransaction {
            val entity = SaleEntity(
                id = draft.id,
                // Allocated inside the transaction, so a sale that never commits leaves no gap.
                receiptNumber = dao.nextReceiptNumber(),
                locationId = draft.locationId,
                priceListId = draft.priceListId,
                userId = draft.userId,
                shiftId = draft.shiftId,
                customerId = draft.customerId,
                status = SaleStatus.COMPLETED,
                subtotalPiastres = draft.subtotal.piastres,
                discountPiastres = draft.discount.piastres,
                taxPiastres = draft.tax.piastres,
                totalPiastres = draft.total.piastres,
                tenderedPiastres = draft.tendered.piastres,
                changePiastres = draft.change.piastres,
                occurredAt = draft.occurredAt,
                voidedAt = null,
                voidedByUserId = null,
                voidReason = null,
            )

            dao.insert(entity)
            dao.insertLines(draft.lines.map { it.toEntity() })
            dao.insertPayments(draft.payments.map { it.toEntity() })
            ledger.recordAll(
                draft.lines.map { line ->
                    StockMovementEntity(
                        id = ids.newId(),
                        variantId = line.variantId,
                        locationId = draft.locationId,
                        quantity = -line.quantity,
                        reason = MovementReason.SALE,
                        refType = REF_SALE,
                        refId = draft.id,
                        occurredAt = draft.occurredAt,
                        userId = draft.userId,
                    )
                },
            )

            openReceivableIfOnAccount(draft)

            entity.toDomain(draft.lines, draft.payments)
        }
    }

    /**
     * Goods out on account are money owed, and the two are one event.
     *
     * Written here rather than by the caller for the same reason the stock movements are: a shop
     * that recorded one without the other would be giving stock away, and the invariant is worth
     * more inside the transaction than in a use case that has to remember.
     */
    private suspend fun openReceivableIfOnAccount(draft: SaleDraft) {
        val customerId = draft.customerId ?: return
        val onAccount = draft.payments
            .filter { it.method == TenderMethod.CREDIT }
            .fold(com.alsoug.keswa.core.domain.money.Money.ZERO) { sum, payment -> sum + payment.amount }
        if (onAccount.isZero) return

        val customer = requireNotNull(customers.getById(customerId)) {
            "customer not found: $customerId"
        }

        receivables.insert(
            CustomerLedgerEntryEntity(
                id = ids.newId(),
                customerId = customerId,
                entryType = LedgerEntryType.INVOICE,
                // Positive: they owe more.
                amountPiastres = onAccount.piastres,
                refType = REF_INVOICE,
                refId = draft.id,
                occurredAt = draft.occurredAt,
                // On the entry, because the entry is the thing that falls due.
                dueAt = draft.occurredAt + customer.paymentTermsDays * MILLIS_PER_DAY,
                userId = draft.userId,
                note = null,
                authorisedByUserId = draft.creditAuthorisedByUserId,
            ),
        )
    }

    override suspend fun void(
        saleId: String,
        byUserId: String,
        reason: String,
        atMillis: Long,
    ): Result<Sale> = runCatchingCancellable {
        database.inTransaction {
            val header = requireNotNull(dao.getById(saleId)) { "sale not found: $saleId" }

            // Guarded on COMPLETED inside the statement, so a double submit cannot write two sets
            // of compensating movements.
            val affected = dao.markVoided(saleId, atMillis, byUserId, reason)
            require(affected == 1) { "sale is not open to void: $saleId" }

            ledger.recordAll(
                dao.getLines(saleId).map { line ->
                    StockMovementEntity(
                        id = ids.newId(),
                        variantId = line.variantId,
                        locationId = header.locationId,
                        quantity = line.quantity,
                        // Same reason, opposite sign: units sold still nets correctly without
                        // every later report having to know what a void is.
                        reason = MovementReason.SALE,
                        refType = REF_VOID,
                        refId = saleId,
                        occurredAt = atMillis,
                        userId = byUserId,
                    )
                },
            )

            requireNotNull(read(saleId)) { "sale vanished mid-void: $saleId" }
        }
    }

    override suspend fun getById(id: String): Result<Sale?> = runCatchingCancellable { read(id) }

    override suspend fun getByReceiptNumber(receiptNumber: Long): Result<Sale?> =
        runCatchingCancellable {
            dao.getByReceiptNumber(receiptNumber)?.let { read(it.id) }
        }

    /** Headers only: a recent-sales list shows totals, and loading every line would be wasteful. */
    override fun observeRecent(limit: Int): Flow<List<Sale>> =
        dao.observeRecent(limit).map { sales -> sales.map { it.toDomain() } }

    private suspend fun read(id: String): Sale? {
        val header = dao.getById(id) ?: return null
        return header.toDomain(
            lines = dao.getLines(id).map { it.toDomain() },
            payments = dao.getPayments(id).map { it.toDomain() },
        )
    }

    companion object {
        const val REF_SALE = "SALE"
        const val REF_VOID = "SALE_VOID"
        const val REF_INVOICE = "INVOICE"
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
