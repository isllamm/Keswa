package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.data.mapper.toEntity
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.dao.SaleDao
import com.alsoug.keswa.core.database.dao.SaleReturnDao
import com.alsoug.keswa.core.database.dao.StockLedgerDao
import com.alsoug.keswa.core.database.entities.SaleReturnEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.inTransaction
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.ReturnableLine
import com.alsoug.keswa.core.domain.model.SaleReturn
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ISaleReturnRepository
import com.alsoug.keswa.core.domain.repository.ReturnDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where goods come back and money goes out.
 *
 * Same shape as `SaleRepositoryImpl`: one transaction covers the document, its lines and every
 * movement that follows, and the movements are derived here so no caller can record a refund
 * without moving the stock it refunded.
 */
class SaleReturnRepositoryImpl(
    private val database: KeswaDatabase,
    private val dao: SaleReturnDao,
    private val sales: SaleDao,
    private val ledger: StockLedgerDao,
    private val ids: IdGenerator,
) : ISaleReturnRepository {

    override suspend fun record(draft: ReturnDraft): Result<SaleReturn> = runCatchingCancellable {
        require(draft.lines.isNotEmpty()) { "a return must have at least one line" }

        database.inTransaction {
            val entity = SaleReturnEntity(
                id = draft.id,
                returnNumber = dao.nextReturnNumber(),
                originalSaleId = draft.originalSaleId,
                locationId = draft.locationId,
                userId = draft.userId,
                shiftId = draft.shiftId,
                status = SaleStatus.COMPLETED,
                reason = draft.reason,
                refundMethod = draft.refundMethod,
                refundAmountPiastres = draft.refundAmount.piastres,
                subtotalPiastres = draft.subtotal.piastres,
                taxPiastres = draft.tax.piastres,
                occurredAt = draft.occurredAt,
                exchangeSaleId = null,
                authorisedByUserId = draft.authorisedByUserId,
                voidedAt = null,
                voidedByUserId = null,
                voidReason = null,
            )

            dao.insert(entity)
            dao.insertLines(draft.lines.map { it.toEntity() })
            ledger.recordAll(draft.lines.flatMap { line -> movementsFor(draft, line) })

            entity.toDomain(draft.lines)
        }
    }

    /**
     * One movement for a sellable return, two for a damaged one.
     *
     * The garment came back and then was written off. Both happened; both are rows.
     */
    private fun movementsFor(
        draft: ReturnDraft,
        line: com.alsoug.keswa.core.domain.model.SaleReturnLine,
    ): List<StockMovementEntity> {
        val incoming = StockMovementEntity(
            id = ids.newId(),
            variantId = line.variantId,
            locationId = draft.locationId,
            quantity = line.quantity,
            reason = MovementReason.RETURN,
            refType = REF_RETURN,
            refId = draft.id,
            occurredAt = draft.occurredAt,
            userId = draft.userId,
            unitCostPiastres = line.unitCost.piastres,
            note = draft.reason.ifBlank { null },
        )
        if (line.condition == ReturnCondition.SELLABLE) return listOf(incoming)

        return listOf(
            incoming,
            StockMovementEntity(
                id = ids.newId(),
                variantId = line.variantId,
                locationId = draft.locationId,
                quantity = -line.quantity,
                reason = MovementReason.DAMAGE,
                refType = REF_RETURN,
                refId = draft.id,
                occurredAt = draft.occurredAt,
                userId = draft.userId,
                unitCostPiastres = line.unitCost.piastres,
                note = "returned damaged",
            ),
        )
    }

    override suspend fun void(
        returnId: String,
        byUserId: String,
        reason: String,
        atMillis: Long,
    ): Result<SaleReturn> = runCatchingCancellable {
        database.inTransaction {
            val header = requireNotNull(dao.getById(returnId)) { "return not found: $returnId" }

            val affected = dao.markVoided(returnId, atMillis, byUserId, reason)
            require(affected == 1) { "return is not open to void: $returnId" }

            // Reverse whatever the return wrote, whichever shape it was: a sellable line reverses
            // one movement, a damaged line reverses two.
            val original = ledger.getMovementsForReference(REF_RETURN, returnId)
            ledger.recordAll(
                original.map { movement ->
                    movement.copy(
                        id = ids.newId(),
                        quantity = -movement.quantity,
                        refType = REF_RETURN_VOID,
                        occurredAt = atMillis,
                        userId = byUserId,
                        note = "void: $reason",
                    )
                },
            )

            requireNotNull(read(returnId)) { "return vanished mid-void: $returnId" }
        }
    }

    override suspend fun linkExchange(returnId: String, saleId: String): Result<Unit> =
        runCatchingCancellable { dao.linkExchange(returnId, saleId) }

    override suspend fun getById(id: String): Result<SaleReturn?> =
        runCatchingCancellable { read(id) }

    override suspend fun getByNumber(returnNumber: Long): Result<SaleReturn?> =
        runCatchingCancellable { dao.getByNumber(returnNumber)?.let { read(it.id) } }

    override suspend fun returnableLines(saleId: String): Result<List<ReturnableLine>> =
        runCatchingCancellable {
            sales.getLines(saleId).map { line ->
                ReturnableLine(
                    saleLineId = line.id,
                    variantId = line.variantId,
                    description = line.description,
                    soldQuantity = line.quantity,
                    alreadyReturned = dao.alreadyReturned(line.id),
                    // What the customer actually paid for it after every discount, which is what
                    // they get back — not the list price it was marked at.
                    unitPrice = Money.ofPiastres(line.lineTotalPiastres / line.quantity),
                    unitCost = Money.ofPiastres(line.unitCostPiastres),
                )
            }
        }

    override suspend fun lowestSoldPrice(variantId: String): Result<Money?> =
        runCatchingCancellable { dao.lowestSoldPrice(variantId)?.let { Money.ofPiastres(it) } }

    /** Headers only: a recent-returns list shows amounts, and lines are loaded on opening one. */
    override fun observeRecent(limit: Int): Flow<List<SaleReturn>> =
        dao.observeRecent(limit).map { returns -> returns.map { it.toDomain() } }

    private suspend fun read(id: String): SaleReturn? {
        val header = dao.getById(id) ?: return null
        return header.toDomain(dao.getLines(id).map { it.toDomain() })
    }

    companion object {
        const val REF_RETURN = "RETURN"
        const val REF_RETURN_VOID = "RETURN_VOID"
    }
}
