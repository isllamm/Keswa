package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.dao.StockCountDao
import com.alsoug.keswa.core.database.dao.StockLedgerDao
import com.alsoug.keswa.core.database.dao.StockReceiptDao
import com.alsoug.keswa.core.database.dao.VariantDao
import com.alsoug.keswa.core.database.entities.StockCountEntity
import com.alsoug.keswa.core.database.entities.StockCountLineEntity
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.entities.StockReceiptEntity
import com.alsoug.keswa.core.database.entities.StockReceiptLineEntity
import com.alsoug.keswa.core.database.inTransaction
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.StockCount
import com.alsoug.keswa.core.domain.model.StockMovement
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.money.movingAverageCost
import com.alsoug.keswa.core.domain.repository.CostChange
import com.alsoug.keswa.core.domain.repository.IStockAdjustmentRepository
import com.alsoug.keswa.core.domain.repository.IStockCountRepository
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.domain.repository.PostedReceipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where stock arrives, and where cost stops being hypothetical.
 *
 * Posting is the whole class: movements, the moving-average recalculation and the document's own
 * status change go in together or not at all. Half a received delivery is a stock figure nobody can
 * trust and a cost nobody can explain.
 */
class StockReceiptRepositoryImpl(
    private val database: KeswaDatabase,
    private val dao: StockReceiptDao,
    private val variants: VariantDao,
    private val ledger: StockLedgerDao,
    private val ids: IdGenerator,
    private val now: () -> Long,
) : IStockReceiptRepository {

    override suspend fun createDraft(
        id: String,
        reference: String,
        supplierName: String,
        locationId: String,
        userId: String,
        atMillis: Long,
    ): Result<StockReceipt> = runCatchingCancellable {
        val entity = StockReceiptEntity(
            id = id,
            reference = reference,
            supplierName = supplierName,
            locationId = locationId,
            status = DocumentStatus.DRAFT,
            note = null,
            createdAt = atMillis,
            createdByUserId = userId,
            postedAt = null,
            postedByUserId = null,
            totalCostPiastres = 0,
        )
        dao.insert(entity)
        entity.toDomain()
    }

    override suspend fun putLine(
        id: String,
        receiptId: String,
        variantId: String,
        quantity: Int,
        unitCost: Money,
    ): Result<StockReceipt> = runCatchingCancellable {
        require(quantity > 0) { "a receipt line must add stock" }
        require(!unitCost.isNegative) { "a cost cannot be negative" }

        val receipt = requireNotNull(dao.getById(receiptId)) { "receipt not found: $receiptId" }
        require(receipt.status == DocumentStatus.DRAFT) { "receipt is already posted: $receiptId" }

        // Scanning the same carton twice should correct the line, not add a second one for the
        // same variant — the same reasoning as the till's basket.
        val existing = dao.getLines(receiptId).firstOrNull { it.variantId == variantId }
        dao.upsertLine(
            StockReceiptLineEntity(
                id = existing?.id ?: id,
                receiptId = receiptId,
                lineNumber = existing?.lineNumber ?: dao.nextLineNumber(receiptId),
                variantId = variantId,
                quantity = quantity,
                unitCostPiastres = unitCost.piastres,
                lineTotalPiastres = unitCost.piastres * quantity,
            ),
        )
        read(receiptId)
    }

    override suspend fun removeLine(receiptId: String, lineId: String): Result<StockReceipt> =
        runCatchingCancellable {
            val receipt = requireNotNull(dao.getById(receiptId)) { "receipt not found: $receiptId" }
            require(receipt.status == DocumentStatus.DRAFT) { "receipt is already posted: $receiptId" }
            dao.deleteLine(lineId)
            read(receiptId)
        }

    override suspend fun post(
        receiptId: String,
        userId: String,
        atMillis: Long,
    ): Result<PostedReceipt> = runCatchingCancellable {
        database.inTransaction {
            val header = requireNotNull(dao.getById(receiptId)) { "receipt not found: $receiptId" }
            val lines = dao.getLines(receiptId)
            require(lines.isNotEmpty()) { "an empty receipt has nothing to post" }

            val total = lines.sumOf { it.lineTotalPiastres }
            val affected = dao.markPosted(receiptId, atMillis, userId, total)
            require(affected == 1) { "receipt is not open to post: $receiptId" }

            val changes = lines.map { line ->
                val variant = requireNotNull(variants.getById(line.variantId)) {
                    "variant not found: ${line.variantId}"
                }
                val onHand = ledger.getOnHand(line.variantId, header.locationId)?.quantity ?: 0

                val before = Money.ofPiastres(variant.costPiastres)
                val after = movingAverageCost(
                    onHand = onHand,
                    currentCost = before,
                    receivedQuantity = line.quantity,
                    receiptCost = Money.ofPiastres(line.unitCostPiastres),
                )
                variants.update(variant.copy(costPiastres = after.piastres, updatedAt = atMillis))

                ledger.record(
                    StockMovementEntity(
                        id = ids.newId(),
                        variantId = line.variantId,
                        locationId = header.locationId,
                        quantity = line.quantity,
                        reason = MovementReason.RECEIPT,
                        refType = REF_RECEIPT,
                        refId = receiptId,
                        occurredAt = atMillis,
                        userId = userId,
                        // What this delivery cost, not what the variant costs now: the ledger has
                        // to describe its own cost basis (KD-008).
                        unitCostPiastres = line.unitCostPiastres,
                        note = null,
                    ),
                )

                CostChange(line.variantId, before, after)
            }

            PostedReceipt(read(receiptId), changes)
        }
    }

    override suspend fun getById(id: String): Result<StockReceipt?> = runCatchingCancellable {
        dao.getById(id)?.let { read(id) }
    }

    override suspend fun discardDraft(id: String): Result<Unit> = runCatchingCancellable {
        val affected = dao.deleteDraft(id)
        require(affected == 1) { "only a draft can be discarded: $id" }
    }

    override suspend fun recent(locationId: String, limit: Int): Result<List<StockReceipt>> =
        runCatchingCancellable { dao.recent(locationId, limit).map { it.toDomain() } }

    override fun observeDrafts(locationId: String): Flow<List<StockReceipt>> =
        dao.observeDrafts(locationId).map { drafts -> drafts.map { it.toDomain() } }

    private suspend fun read(id: String): StockReceipt {
        val header = requireNotNull(dao.getById(id)) { "receipt not found: $id" }
        return header.toDomain(dao.getLines(id).map { it.toDomain() })
    }

    companion object {
        const val REF_RECEIPT = "RECEIPT"
    }
}

/**
 * Counts, kept blind.
 *
 * The expected figure is read here for the first time at [post] — not earlier, and not through any
 * other method on this class, because a count that can be peeked at finds nothing.
 */
class StockCountRepositoryImpl(
    private val database: KeswaDatabase,
    private val dao: StockCountDao,
    private val variants: VariantDao,
    private val ledger: StockLedgerDao,
    private val ids: IdGenerator,
) : IStockCountRepository {

    override suspend fun start(
        id: String,
        locationId: String,
        userId: String,
        atMillis: Long,
    ): Result<StockCount> = runCatchingCancellable {
        require(dao.getOpen(locationId) == null) { "a count is already open at $locationId" }

        val entity = StockCountEntity(
            id = id,
            locationId = locationId,
            status = DocumentStatus.DRAFT,
            note = null,
            startedAt = atMillis,
            startedByUserId = userId,
            postedAt = null,
            postedByUserId = null,
        )
        dao.insert(entity)
        entity.toDomain()
    }

    override suspend fun putLine(
        id: String,
        countId: String,
        variantId: String,
        counted: Int,
    ): Result<StockCount> = runCatchingCancellable {
        require(counted >= 0) { "a counted quantity cannot be negative" }

        val count = requireNotNull(dao.getById(countId)) { "count not found: $countId" }
        require(count.status == DocumentStatus.DRAFT) { "count is already posted: $countId" }

        val existing = dao.getLine(countId, variantId)
        dao.upsertLine(
            StockCountLineEntity(
                id = existing?.id ?: id,
                countId = countId,
                lineNumber = existing?.lineNumber ?: dao.nextLineNumber(countId),
                variantId = variantId,
                countedQuantity = counted,
                // Stays null: filled in by post, and nowhere else.
                expectedQuantity = null,
                varianceQuantity = null,
            ),
        )
        read(countId)
    }

    override suspend fun post(
        countId: String,
        userId: String,
        note: String?,
        atMillis: Long,
    ): Result<StockCount> = runCatchingCancellable {
        database.inTransaction {
            val header = requireNotNull(dao.getById(countId)) { "count not found: $countId" }
            val lines = dao.getLines(countId)
            require(lines.isNotEmpty()) { "an empty count has nothing to post" }

            val affected = dao.markPosted(countId, atMillis, userId)
            require(affected == 1) { "count is not open to post: $countId" }

            lines.forEach { line ->
                val expected = ledger.getOnHand(line.variantId, header.locationId)?.quantity ?: 0
                val variance = line.countedQuantity - expected
                dao.settleLine(line.id, expected, variance)

                // A line that agrees writes nothing. A ledger entry for "nothing changed" is noise
                // that makes the real ones harder to find.
                if (variance == 0) return@forEach

                val variant = variants.getById(line.variantId)
                ledger.record(
                    StockMovementEntity(
                        id = ids.newId(),
                        variantId = line.variantId,
                        locationId = header.locationId,
                        quantity = variance,
                        reason = MovementReason.COUNT,
                        refType = REF_COUNT,
                        refId = countId,
                        occurredAt = atMillis,
                        userId = userId,
                        unitCostPiastres = variant?.costPiastres,
                        note = note,
                    ),
                )
            }

            read(countId)
        }
    }

    override suspend fun getById(id: String): Result<StockCount?> = runCatchingCancellable {
        dao.getById(id)?.let { read(id) }
    }

    override suspend fun current(locationId: String): Result<StockCount?> = runCatchingCancellable {
        dao.getOpen(locationId)?.let { read(it.id) }
    }

    override suspend fun discard(id: String): Result<Unit> = runCatchingCancellable {
        val affected = dao.deleteDraft(id)
        require(affected == 1) { "only an open count can be discarded: $id" }
    }

    override suspend fun recent(locationId: String, limit: Int): Result<List<StockCount>> =
        runCatchingCancellable { dao.recent(locationId, limit).map { it.toDomain() } }

    private suspend fun read(id: String): StockCount {
        val header = requireNotNull(dao.getById(id)) { "count not found: $id" }
        return header.toDomain(dao.getLines(id).map { it.toDomain() })
    }

    companion object {
        const val REF_COUNT = "COUNT"
    }
}

class StockAdjustmentRepositoryImpl(
    private val ledger: StockLedgerDao,
    private val variants: VariantDao,
) : IStockAdjustmentRepository {

    override suspend fun adjust(
        id: String,
        variantId: String,
        locationId: String,
        quantity: Int,
        reason: MovementReason,
        note: String,
        userId: String,
        atMillis: Long,
    ): Result<StockMovement> = runCatchingCancellable {
        require(quantity != 0) { "an adjustment of zero changes nothing" }
        require(note.isNotBlank()) { "an adjustment needs a reason in words" }

        val movement = StockMovementEntity(
            id = id,
            variantId = variantId,
            locationId = locationId,
            quantity = quantity,
            reason = reason,
            refType = null,
            refId = null,
            occurredAt = atMillis,
            userId = userId,
            unitCostPiastres = variants.getById(variantId)?.costPiastres,
            note = note.trim(),
        )
        ledger.record(movement)
        movement.toDomain()
    }

    override suspend fun historyFor(
        variantId: String,
        locationId: String,
    ): Result<List<StockMovement>> = runCatchingCancellable {
        ledger.getMovements(variantId, locationId).map { it.toDomain() }
    }
}
