package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.StockCount
import com.alsoug.keswa.core.domain.model.StockMovement
import com.alsoug.keswa.core.domain.repository.IStockAdjustmentRepository
import com.alsoug.keswa.core.domain.repository.IStockCountRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Starts a blind count.
 *
 * `COUNT_STOCK`, which a seller has: the person walking the rail is the person counting, and a
 * count only a manager can start is a count that happens twice a year.
 */
class StartCountUseCase(
    private val counts: IStockCountRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {
    suspend operator fun invoke(locationId: String): Result<StockCount> = runCatching {
        sessions.require(Permission.COUNT_STOCK)
        val user = requireNotNull(sessions.current.value) { "no session" }.user
        counts.start(ids.newId(), locationId, user.id, now()).getOrThrow()
    }
}

/**
 * Records what was on the shelf.
 *
 * Nothing here reads the expected figure, and there is no method on the repository that would let
 * it — that is what keeps the count blind, rather than a promise the screen makes.
 */
class CountVariantUseCase(
    private val counts: IStockCountRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        countId: String,
        variantId: String,
        counted: Int,
    ): Result<StockCount> = runCatching {
        sessions.require(Permission.COUNT_STOCK)
        counts.putLine(ids.newId(), countId, variantId, counted).getOrThrow()
    }
}

/**
 * Settles the count against the ledger.
 *
 * The first moment anybody sees what was expected, and the only moment the count writes anything.
 */
class PostCountUseCase(
    private val counts: IStockCountRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(countId: String, note: String?): Result<StockCount> = runCatching {
        sessions.require(Permission.COUNT_STOCK)
        val user = requireNotNull(sessions.current.value) { "no session" }.user
        counts.post(countId, user.id, note?.trim()?.ifBlank { null }, now()).getOrThrow()
    }
}

class DiscardCountUseCase(
    private val counts: IStockCountRepository,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(countId: String): Result<Unit> = runCatching {
        sessions.require(Permission.COUNT_STOCK)
        counts.discard(countId).getOrThrow()
    }
}

class CurrentCountUseCase(private val counts: IStockCountRepository) {
    suspend operator fun invoke(locationId: String): Result<StockCount?> = counts.current(locationId)
}

class RecentCountsUseCase(private val counts: IStockCountRepository) {
    suspend operator fun invoke(locationId: String): Result<List<StockCount>> =
        counts.recent(locationId)
}

/**
 * A one-off correction outside any document.
 *
 * Requires a reason in words, not just the enum: `DAMAGE` says the category, "three shirts
 * water-damaged in the stockroom" is the fact — and the fact is what makes the ledger an audit
 * trail rather than a list of numbers.
 *
 * `RECEIVE_STOCK` rather than `COUNT_STOCK`: writing stock off by hand is a different power from
 * counting it, and a seller has the second but not the first.
 */
class AdjustStockUseCase(
    private val adjustments: IStockAdjustmentRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        variantId: String,
        locationId: String,
        quantity: Int,
        reason: MovementReason,
        note: String,
    ): Result<StockMovement> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)
        require(reason == MovementReason.ADJUSTMENT || reason == MovementReason.DAMAGE) {
            "a hand adjustment is either an ADJUSTMENT or DAMAGE — anything else has a document"
        }
        val user = requireNotNull(sessions.current.value) { "no session" }.user

        adjustments.adjust(
            id = ids.newId(),
            variantId = variantId,
            locationId = locationId,
            quantity = quantity,
            reason = reason,
            note = note,
            userId = user.id,
            atMillis = now(),
        ).getOrThrow()
    }
}

class StockHistoryUseCase(private val adjustments: IStockAdjustmentRepository) {
    suspend operator fun invoke(variantId: String, locationId: String): Result<List<StockMovement>> =
        adjustments.historyFor(variantId, locationId)
}
