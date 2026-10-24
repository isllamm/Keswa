package com.alsoug.keswa.features.sell.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.HeldSale
import com.alsoug.keswa.core.domain.model.HeldSaleLine
import com.alsoug.keswa.core.domain.repository.IHeldSaleRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.model.BasketLine

/**
 * Parks the cart so the till can serve the next person.
 *
 * A customer goes to fetch another colour and there are four people behind them; without this the
 * shop's answer is to ring the sale up and void it, which is worse for everyone including the
 * ledger.
 */
class HoldSaleUseCase(
    private val held: IHeldSaleRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        basket: Basket,
        label: String,
        till: TillContext,
    ): Result<HeldSale> = runCatching {
        require(!basket.isEmpty) { "nothing to hold" }
        val user = requireNotNull(sessions.current.value) { "no session" }.user

        val heldId = ids.newId()
        val timestamp = now()
        held.hold(
            id = heldId,
            label = label.ifBlank { DEFAULT_LABEL },
            locationId = till.locationId,
            userId = user.id,
            atMillis = timestamp,
            lines = basket.lines.mapIndexed { index, line ->
                HeldSaleLine(
                    id = ids.newId(),
                    heldSaleId = heldId,
                    lineNumber = index + 1,
                    variantId = line.variantId,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineDiscount = line.lineDiscount,
                    authorisedByUserId = line.authorisedByUserId,
                )
            },
        ).getOrThrow()
    }

    private companion object {
        const val DEFAULT_LABEL = "Held sale"
    }
}

/**
 * Brings a parked cart back.
 *
 * Prices and discounts are restored **as they were agreed** — a customer told 180 does not come
 * back to 200 because a markdown ended while they were choosing. Stock and description are re-read,
 * because those are facts about now rather than promises made earlier.
 *
 * A line whose variant has since been retired is dropped and reported, rather than resuming a cart
 * that cannot be completed.
 */
class ResumeHeldSaleUseCase(
    private val held: IHeldSaleRepository,
    private val sellables: ISellableRepository,
    private val now: () -> Long,
) {
    data class Resumed(val basket: Basket, val dropped: List<String>)

    suspend operator fun invoke(heldSaleId: String, till: TillContext): Result<Resumed> =
        runCatching {
            val parked = requireNotNull(held.getById(heldSaleId).getOrThrow()) {
                "held sale not found: $heldSaleId"
            }

            val dropped = mutableListOf<String>()
            val lines = parked.lines.mapNotNull { line ->
                val item = sellables
                    .byVariantId(line.variantId, till.priceListId, till.locationId, now())
                    .getOrThrow()

                if (item == null) {
                    dropped += line.variantId
                    null
                } else {
                    BasketLine(
                        variantId = item.variantId,
                        sku = item.sku,
                        description = item.description,
                        descriptionAr = item.descriptionAr,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        listPrice = item.price ?: line.unitPrice,
                        unitCost = item.cost,
                        lineDiscount = line.lineDiscount,
                        onHand = item.onHand,
                        authorisedByUserId = line.authorisedByUserId,
                    )
                }
            }

            held.discard(heldSaleId).getOrThrow()
            Resumed(Basket(lines), dropped)
        }
}

class ListHeldSalesUseCase(private val held: IHeldSaleRepository) {
    suspend operator fun invoke(till: TillContext): Result<List<HeldSale>> =
        held.list(till.locationId)
}

class DiscardHeldSaleUseCase(private val held: IHeldSaleRepository) {
    suspend operator fun invoke(heldSaleId: String): Result<Unit> = held.discard(heldSaleId)
}
