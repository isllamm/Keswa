package com.alsoug.keswa.features.sell.domain.usecase

import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.printing.escpos.EscPos
import com.alsoug.keswa.core.printing.model.Receipt
import com.alsoug.keswa.core.printing.model.ReceiptLine
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Where a receipt got to. "The printer is off" is an ordinary outcome, not a program error. */
sealed interface ReceiptResult {
    data object Printed : ReceiptResult
    data object NoPrinter : ReceiptResult
    data class Unreachable(val detail: String) : ReceiptResult
}

/**
 * Prints a receipt for a sale that has **already** committed.
 *
 * Called after `CompleteSaleUseCase`, never inside it. A failure here leaves the sale intact and
 * reprintable; the opposite arrangement leaves a customer holding a receipt for a transaction the
 * shop has no record of.
 *
 * The drawer pops only for cash. A card sale that opens the drawer trains everyone to ignore it,
 * and an unexplained open drawer is exactly what a shift count exists to catch.
 */
@OptIn(ExperimentalTime::class)
class PrintReceiptUseCase(
    private val settings: ISettingsRepository,
    private val sales: ISaleRepository,
    private val renderer: IReceiptRenderer,
    private val transports: TransportFactory,
) {

    suspend operator fun invoke(sale: Sale): Result<ReceiptResult> = runCatching {
        val shop = settings.get().getOrThrow()
        if (!shop.hasReceiptPrinter) return@runCatching ReceiptResult.NoPrinter

        val bitmap = renderer.render(sale.toReceipt(shop), shop.paperWidthDots)
        val tookCash = sale.payments.any { it.method == TenderMethod.CASH }

        transports.create(shop.receiptHost, shop.receiptPort)
            .send(EscPos.document(bitmap, openDrawer = tookCash))
            .fold(
                onSuccess = { ReceiptResult.Printed },
                onFailure = { ReceiptResult.Unreachable(it.message ?: it::class.simpleName.orEmpty()) },
            )
    }

    /** Reprints from storage, so a paper jam does not need the cart to still be on screen. */
    suspend fun reprint(saleId: String): Result<ReceiptResult> = runCatching {
        val sale = sales.getById(saleId).getOrThrow()
            ?: return@runCatching ReceiptResult.Unreachable("no such sale")
        invoke(sale).getOrThrow()
    }
}

@OptIn(ExperimentalTime::class)
internal fun Sale.toReceipt(shop: ShopSettings): Receipt = Receipt(
    shopName = shop.shopName,
    shopNameAr = shop.shopNameAr,
    addressLine = shop.addressLine,
    lines = lines.map { line ->
        ReceiptLine(
            quantity = line.quantity,
            description = line.description,
            descriptionAr = line.descriptionAr,
            amount = line.lineTotal,
        )
    },
    subtotal = subtotal,
    discount = discount,
    vat = tax,
    total = total,
    itemCount = itemCount,
    // The QR carries the sale's id rather than its number: Phase 8 scans it to find a sale to
    // return against, and the id is what survives a merge between two tills.
    saleId = id,
    timestamp = formatTimestamp(occurredAt),
)

@OptIn(ExperimentalTime::class)
private fun formatTimestamp(millis: Long): String {
    val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val date = "${local.year}-${local.monthNumber.pad()}-${local.dayOfMonth.pad()}"
    return "$date ${local.hour.pad()}:${local.minute.pad()}"
}

private fun Int.pad(): String = toString().padStart(2, '0')
