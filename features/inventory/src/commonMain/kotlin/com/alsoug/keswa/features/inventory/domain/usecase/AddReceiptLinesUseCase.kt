package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.domain.repository.ReceiptLineInput
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/** What was typed against one colour. Text, not numbers: turning it into numbers is a rule. */
data class PickedEntry(
    val item: SellableItem,
    val quantity: String,
    val cost: String,
    val price: String,
)

sealed interface AddLinesResult {
    data class Added(val receipt: StockReceipt, val lines: Int) : AddLinesResult

    /** Nothing was added. Every bad row is named, so they can all be fixed in one go. */
    data class Rejected(val problems: Map<String, EntryProblem>) : AddLinesResult
}

enum class EntryProblem { NOT_A_QUANTITY, NOT_AN_AMOUNT, PRICE_REQUIRED }

class AddReceiptLinesUseCase(
    private val receipts: IStockReceiptRepository,
    private val sessions: ISessionManager,
    private val ensureBarcode: EnsureVariantBarcodeUseCase,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        receiptId: String,
        entries: List<PickedEntry>,
    ): Result<AddLinesResult> = runCatchingCancellable {
        sessions.require(Permission.RECEIVE_STOCK)

        val active = entries.filter { it.quantity.isNotBlank() }
        val problems = linkedMapOf<String, EntryProblem>()
        val parsed = mutableListOf<ReceiptLineInput>()

        for (entry in active) {
            val variantId = entry.item.variantId
            val qty = entry.quantity.trim().toIntOrNull()
            if (qty == null || qty <= 0) {
                problems[variantId] = EntryProblem.NOT_A_QUANTITY
                continue
            }
            val cost = Money.parse(entry.cost)?.takeUnless { it.isNegative }
            if (cost == null) {
                problems[variantId] = EntryProblem.NOT_AN_AMOUNT
                continue
            }
            val sellPrice: Money? = if (entry.price.isBlank()) {
                if (entry.item.price == null) {
                    problems[variantId] = EntryProblem.PRICE_REQUIRED
                    continue
                }
                null
            } else {
                val typedPrice = Money.parse(entry.price)?.takeUnless { it.isNegative }
                if (typedPrice == null) {
                    problems[variantId] = EntryProblem.NOT_AN_AMOUNT
                    continue
                }
                typedPrice.takeIf { it != entry.item.price }
            }

            parsed += ReceiptLineInput(
                id = ids.newId(),
                variantId = variantId,
                quantity = qty,
                unitCost = cost,
                sellPrice = sellPrice,
            )
        }

        if (problems.isNotEmpty()) {
            return@runCatchingCancellable AddLinesResult.Rejected(problems)
        }

        if (parsed.any { it.sellPrice != null }) {
            sessions.require(Permission.MANAGE_CATALOGUE)
        }

        for (line in parsed) {
            ensureBarcode(line.variantId).getOrThrow()
        }

        val receipt = receipts.putLines(receiptId, parsed).getOrThrow()
        AddLinesResult.Added(receipt = receipt, lines = parsed.size)
    }
}
