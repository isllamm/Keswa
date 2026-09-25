package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.domain.repository.PostedReceipt
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Where this install keeps its stock.
 *
 * Deliberately not the till's `TillContext`: receiving needs a location and nothing else, and a
 * price list has no business in a stockroom. `features:A` cannot import `features:B` in any case.
 */
class ResolveStockLocationUseCase(private val locations: ILocationRepository) {
    suspend operator fun invoke(): Result<String> = runCatching {
        requireNotNull(locations.default().getOrThrow()) {
            "no default location — a fresh install seeds one at startup"
        }.id
    }
}

class StartReceiptUseCase(
    private val receipts: IStockReceiptRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        reference: String,
        supplierName: String,
        locationId: String,
    ): Result<StockReceipt> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)
        val user = requireNotNull(sessions.current.value) { "no session" }.user

        receipts.createDraft(
            id = ids.newId(),
            reference = reference.trim().ifBlank { "Delivery" },
            supplierName = supplierName.trim(),
            locationId = locationId,
            userId = user.id,
            atMillis = now(),
        ).getOrThrow()
    }
}

/**
 * Adds a variant to a delivery, or corrects the line already there for it.
 *
 * Receiving by **colour quantity** is the whole shape of this: "100 t-shirts, 20 red, 30 blue" is
 * four calls to this function, one per colour, because a colour is the only variant axis and stock
 * hangs off the variant.
 */
class AddReceiptLineUseCase(
    private val receipts: IStockReceiptRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
    private val ensureBarcode: EnsureVariantBarcodeUseCase? = null,
) {
    suspend operator fun invoke(
        receiptId: String,
        variantId: String,
        quantity: Int,
        unitCost: Money,
    ): Result<StockReceipt> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)
        ensureBarcode?.invoke(variantId)?.getOrThrow()
        receipts.putLine(ids.newId(), receiptId, variantId, quantity, unitCost).getOrThrow()
    }
}

class RemoveReceiptLineUseCase(
    private val receipts: IStockReceiptRepository,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(receiptId: String, lineId: String): Result<StockReceipt> =
        runCatching {
            sessions.require(Permission.RECEIVE_STOCK)
            receipts.removeLine(receiptId, lineId).getOrThrow()
        }
}

/**
 * Commits the delivery: stock in, costs recalculated, document closed — all together.
 *
 * Returns the cost changes as well as the receipt, because a supplier quietly raising a price is
 * exactly what an owner wants to be told, and this is the moment it becomes visible.
 */
class PostReceiptUseCase(
    private val receipts: IStockReceiptRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(receiptId: String): Result<PostedReceipt> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)
        val user = requireNotNull(sessions.current.value) { "no session" }.user
        receipts.post(receiptId, user.id, now()).getOrThrow()
    }
}

class DiscardReceiptUseCase(
    private val receipts: IStockReceiptRepository,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(receiptId: String): Result<Unit> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)
        receipts.discardDraft(receiptId).getOrThrow()
    }
}

class GetReceiptUseCase(private val receipts: IStockReceiptRepository) {
    suspend operator fun invoke(receiptId: String): Result<StockReceipt?> =
        receipts.getById(receiptId)
}

class RecentReceiptsUseCase(private val receipts: IStockReceiptRepository) {
    suspend operator fun invoke(locationId: String): Result<List<StockReceipt>> =
        receipts.recent(locationId)
}

class ObserveDraftReceiptsUseCase(private val receipts: IStockReceiptRepository) {
    operator fun invoke(locationId: String): kotlinx.coroutines.flow.Flow<List<StockReceipt>> =
        receipts.observeDrafts(locationId)
}

/** Cohesive facade grouping all receiving use cases to prevent constructor bloat. */
data class ReceivingUseCases(
    val start: StartReceiptUseCase,
    val addLine: AddReceiptLineUseCase,
    val removeLine: RemoveReceiptLineUseCase,
    val post: PostReceiptUseCase,
    val discard: DiscardReceiptUseCase,
    val getById: GetReceiptUseCase,
    val recent: RecentReceiptsUseCase,
    val observeDrafts: ObserveDraftReceiptsUseCase,
)
