package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.data.mapper.toEntity
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.dao.SaleDao
import com.alsoug.keswa.core.database.dao.StockLedgerDao
import com.alsoug.keswa.core.database.entities.SaleEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.inTransaction
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.SaleDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where a sale becomes permanent.
 *
 * Everything a sale implies — header, lines, tenders, stock — happens in one transaction, and the
 * stock movements are built here rather than accepted from the caller so that there is no way to
 * commit a sale without moving what it sold.
 */
class SaleRepositoryImpl(
    private val database: KeswaDatabase,
    private val dao: SaleDao,
    private val ledger: StockLedgerDao,
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

            entity.toDomain(draft.lines, draft.payments)
        }
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
    }
}
