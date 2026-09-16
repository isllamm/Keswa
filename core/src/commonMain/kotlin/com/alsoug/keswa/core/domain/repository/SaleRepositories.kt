package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.HeldSale
import com.alsoug.keswa.core.domain.model.HeldSaleLine
import com.alsoug.keswa.core.domain.model.Payment
import com.alsoug.keswa.core.domain.model.PriceList
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleLine
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.ZReport
import com.alsoug.keswa.core.domain.money.Money
import kotlinx.coroutines.flow.Flow

/**
 * A sale as the till has computed it, before it has a receipt number.
 *
 * A draft has no number because the number is allocated inside the commit — a sale that never
 * commits must not consume one, or the shop's receipts have gaps it cannot explain.
 */
data class SaleDraft(
    val id: String,
    val locationId: String,
    val priceListId: String,
    val userId: String,
    val shiftId: String?,
    val subtotal: Money,
    val discount: Money,
    val tax: Money,
    val total: Money,
    val tendered: Money,
    val change: Money,
    val occurredAt: Long,
    val lines: List<SaleLine>,
    val payments: List<Payment>,
)

interface ISaleRepository {

    /**
     * Commits the sale, its lines, its tenders **and its stock movements** in one transaction, and
     * returns it with the receipt number it was allocated.
     *
     * The movements are derived here rather than passed in, so that no caller can commit a sale
     * without moving the stock it sold. That is the invariant the whole ledger rests on.
     */
    suspend fun record(draft: SaleDraft): Result<Sale>

    /**
     * Marks a sale void and writes the compensating movements, in one transaction.
     *
     * The reversal carries the same `SALE` reason with the opposite sign, so units sold still nets
     * correctly without every later report having to know what a void is.
     */
    suspend fun void(
        saleId: String,
        byUserId: String,
        reason: String,
        atMillis: Long,
    ): Result<Sale>

    suspend fun getById(id: String): Result<Sale?>

    suspend fun getByReceiptNumber(receiptNumber: Long): Result<Sale?>

    fun observeRecent(limit: Int): Flow<List<Sale>>
}

interface IShiftRepository {

    suspend fun open(
        id: String,
        locationId: String,
        userId: String,
        openingFloat: Money,
        atMillis: Long,
    ): Result<Shift>

    /** Closes the shift and returns what it did, with the expected cash frozen onto the row. */
    suspend fun close(
        shiftId: String,
        userId: String,
        countedCash: Money,
        note: String?,
        atMillis: Long,
    ): Result<ZReport>

    suspend fun current(locationId: String): Result<Shift?>

    suspend fun report(shiftId: String): Result<ZReport?>

    fun observeCurrent(locationId: String): Flow<Shift?>
}

interface IHeldSaleRepository {

    suspend fun hold(
        id: String,
        label: String,
        locationId: String,
        userId: String,
        atMillis: Long,
        lines: List<HeldSaleLine>,
    ): Result<HeldSale>

    suspend fun getById(id: String): Result<HeldSale?>

    suspend fun list(locationId: String): Result<List<HeldSale>>

    suspend fun discard(id: String): Result<Unit>

    fun observe(locationId: String): Flow<List<HeldSale>>
}

interface IPriceRepository {

    /** The list the till sells from. Seeded at install, so this is null only on a broken database. */
    suspend fun defaultList(): Result<PriceList?>

    suspend fun ensureDefaultList(id: String, name: String, nameAr: String): Result<PriceList>

    suspend fun effectivePrice(variantId: String, priceListId: String, at: Long): Result<Money?>

    /**
     * Sets a price from [from] onwards, closing whatever was in force.
     *
     * A price change is a new row, not an edit: an old receipt has to remain explicable, and
     * "what was this selling for in March" is a question shops genuinely ask.
     */
    suspend fun setPrice(
        id: String,
        variantId: String,
        priceListId: String,
        price: Money,
        from: Long,
    ): Result<Unit>
}

/** Reads for the till's hot path: one indexed lookup per scan. */
interface ISellableRepository {

    suspend fun byBarcode(
        barcode: String,
        priceListId: String,
        locationId: String,
        at: Long,
    ): Result<SellableItem?>

    suspend fun byVariantId(
        variantId: String,
        priceListId: String,
        locationId: String,
        at: Long,
    ): Result<SellableItem?>

    suspend fun search(
        term: String,
        priceListId: String,
        locationId: String,
        at: Long,
        limit: Int = 25,
    ): Result<List<SellableItem>>
}
