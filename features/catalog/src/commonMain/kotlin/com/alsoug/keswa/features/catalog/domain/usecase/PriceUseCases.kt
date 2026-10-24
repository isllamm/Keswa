package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

sealed interface SetPriceResult {
    data object Saved : SetPriceResult
    data object NotAnAmount : SetPriceResult
    data object NoPriceList : SetPriceResult
}

/**
 * Sets what a variant sells for.
 *
 * The gap Phase 5 found: the `price` tables have existed since v1, the catalogue set `cost`, and
 * nothing ever set a selling price — so nothing could be rung up.
 *
 * Writing a price closes the previous one rather than overwriting it, so an old receipt stays
 * explicable and a seasonal markdown leaves a trail.
 */
class SetRetailPriceUseCase(
    private val prices: IPriceRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {
    suspend operator fun invoke(variantId: String, amount: String): Result<SetPriceResult> =
        runCatchingCancellable {
            sessions.require(Permission.MANAGE_CATALOGUE)

            val price = Money.parse(amount) ?: return@runCatchingCancellable SetPriceResult.NotAnAmount
            if (price.isNegative) return@runCatchingCancellable SetPriceResult.NotAnAmount

            val list = prices.defaultList().getOrThrow()
                ?: return@runCatchingCancellable SetPriceResult.NoPriceList

            prices.setPrice(ids.newId(), variantId, list.id, price, now()).getOrThrow()
            SetPriceResult.Saved
        }
}

class GetRetailPriceUseCase(
    private val prices: IPriceRepository,
    private val now: () -> Long,
) {
    suspend operator fun invoke(variantId: String): Result<Money?> = runCatchingCancellable {
        val list = prices.defaultList().getOrThrow() ?: return@runCatchingCancellable null
        prices.effectivePrice(variantId, list.id, now()).getOrThrow()
    }
}
