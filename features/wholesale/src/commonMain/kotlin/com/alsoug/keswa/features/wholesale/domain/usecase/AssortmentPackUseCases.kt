package com.alsoug.keswa.features.wholesale.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.AssortmentPack
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.money.allocate
import com.alsoug.keswa.core.domain.repository.IAssortmentPackRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/** One variant out of an expanded carton, priced with its share of the carton's price. */
data class ExpandedPackLine(
    val item: SellableItem,
    val quantity: Int,
    val unitPrice: Money,
    val lineTotal: Money,
    val packId: String,
    val packName: String,
)

sealed interface PackExpansion {
    data class Expanded(val lines: List<ExpandedPackLine>) : PackExpansion
    data object NotFound : PackExpansion
    data object Empty : PackExpansion
    /** Variants the pack names that no longer sell — reported rather than silently dropped. */
    data class Incomplete(val lines: List<ExpandedPackLine>, val missing: List<String>) : PackExpansion
}

/**
 * Turns "a carton: 20 navy, 30 white, 10 beige, one price" into ordinary basket lines.
 *
 * **Expanding is the whole design.** The alternative — a sale line that points at a pack rather
 * than a variant — would mean a nullable `variantId`, and then every stock query, every analytics
 * rollup and every return in the app grows a special case for a row that is not really an item.
 * Stock moves per variant because stock *is* per variant.
 *
 * The carton's price is spread with `Money.allocate`, weighted by each variant's own list price so
 * the split is defensible, and by largest remainder so **the lines sum to exactly the price that
 * was quoted**. A carton whose lines add up to a pound less than the invoice is the wholesale
 * version of the classic POS bug.
 *
 * Where a variant has no list price the weights fall back to quantity, because a pack of unpriced
 * stock still has to be sellable at the carton price.
 */
class ExpandAssortmentPackUseCase(
    private val packs: IAssortmentPackRepository,
    private val sellables: ISellableRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {

    suspend operator fun invoke(
        packId: String,
        priceListId: String,
        locationId: String,
        cartons: Int = 1,
    ): Result<PackExpansion> = runCatching {
        sessions.require(Permission.SELL)
        require(cartons > 0) { "a carton count must be positive" }

        val pack = packs.getById(packId).getOrThrow() ?: return@runCatching PackExpansion.NotFound
        if (pack.lines.isEmpty()) return@runCatching PackExpansion.Empty

        val missing = mutableListOf<String>()
        val resolved = pack.lines.mapNotNull { line ->
            val item = sellables.byVariantId(line.variantId, priceListId, locationId, now())
                .getOrThrow()
            if (item == null) {
                missing += line.variantId
                null
            } else {
                item to line.quantity * cartons
            }
        }

        if (resolved.isEmpty()) {
            return@runCatching PackExpansion.Incomplete(emptyList(), missing)
        }

        val total = pack.price * cartons
        val weights = weightsFor(resolved)
        val shares = total.allocate(weights)

        val lines = resolved.mapIndexed { index, (item, quantity) ->
            ExpandedPackLine(
                item = item,
                quantity = quantity,
                // The per-piece figure is derived from the share, so rounding lands on the line
                // total rather than being multiplied back up into a discrepancy.
                unitPrice = Money.ofPiastres(shares[index].piastres / quantity),
                lineTotal = shares[index],
                packId = pack.id,
                packName = pack.name,
            )
        }

        if (missing.isEmpty()) PackExpansion.Expanded(lines) else PackExpansion.Incomplete(lines, missing)
    }

    /**
     * Weight by what each variant is worth, so an expensive garment carries more of the carton's
     * price than a cheap one. Falls back to quantity when nothing in the pack has a list price.
     */
    private fun weightsFor(resolved: List<Pair<SellableItem, Int>>): List<Int> {
        val byValue = resolved.map { (item, quantity) ->
            ((item.price?.piastres ?: 0L) * quantity).toInt()
        }
        return if (byValue.sum() > 0) byValue else resolved.map { it.second }
    }
}

class CreatePackUseCase(
    private val packs: IAssortmentPackRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(name: String, nameAr: String, price: Money): Result<AssortmentPack> =
        runCatching {
            sessions.require(Permission.MANAGE_CATALOGUE)
            packs.create(ids.newId(), name, nameAr, price).getOrThrow()
        }
}

class AddPackLineUseCase(
    private val packs: IAssortmentPackRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        packId: String,
        variantId: String,
        quantity: Int,
    ): Result<AssortmentPack> = runCatching {
        sessions.require(Permission.MANAGE_CATALOGUE)
        packs.addLine(ids.newId(), packId, variantId, quantity).getOrThrow()
    }
}

class ListPacksUseCase(private val packs: IAssortmentPackRepository) {
    suspend operator fun invoke(): Result<List<AssortmentPack>> = packs.getAll()
}
