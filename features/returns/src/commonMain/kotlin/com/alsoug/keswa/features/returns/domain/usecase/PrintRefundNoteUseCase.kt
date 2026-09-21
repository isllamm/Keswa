package com.alsoug.keswa.features.returns.domain.usecase

import com.alsoug.keswa.core.domain.model.SaleReturn
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
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

/** Where a refund note got to. "The printer is off" is an ordinary outcome, not a program error. */
sealed interface RefundNoteResult {
    data object Printed : RefundNoteResult
    data object NoPrinter : RefundNoteResult
    data class Unreachable(val detail: String) : RefundNoteResult
}

/**
 * Prints the customer's copy of a refund, after it has already committed.
 *
 * Same arrangement as the sale receipt, and for the same reason: a refund that committed but did
 * not print can be reprinted; one that printed but did not commit is a customer holding paper for
 * money the shop never gave back.
 *
 * The drawer pops for a cash refund — money is coming out of it — and stays shut for a card one.
 *
 * Amounts print negative, because that is what happened. A refund note that looks like a receipt
 * is a refund note somebody will try to return against.
 */
@OptIn(ExperimentalTime::class)
class PrintRefundNoteUseCase(
    private val settings: ISettingsRepository,
    private val renderer: IReceiptRenderer,
    private val transports: TransportFactory,
) {

    suspend operator fun invoke(saleReturn: SaleReturn): Result<RefundNoteResult> = runCatching {
        val shop = settings.get().getOrThrow()
        if (!shop.hasReceiptPrinter) return@runCatching RefundNoteResult.NoPrinter

        val bitmap = renderer.render(saleReturn.toRefundNote(shop), shop.paperWidthDots)
        val paidInCash = saleReturn.refundMethod == TenderMethod.CASH

        transports.create(shop.receiptHost, shop.receiptPort)
            .send(EscPos.document(bitmap, openDrawer = paidInCash))
            .fold(
                onSuccess = { RefundNoteResult.Printed },
                onFailure = {
                    RefundNoteResult.Unreachable(it.message ?: it::class.simpleName.orEmpty())
                },
            )
    }
}

@OptIn(ExperimentalTime::class)
internal fun SaleReturn.toRefundNote(shop: ShopSettings): Receipt = Receipt(
    shopName = shop.shopName,
    shopNameAr = shop.shopNameAr,
    addressLine = shop.addressLine,
    lines = lines.map { line ->
        ReceiptLine(
            quantity = line.quantity,
            description = line.description,
            descriptionAr = "",
            amount = -line.lineRefund,
        )
    },
    subtotal = -subtotal,
    discount = Money.ZERO,
    vat = -tax,
    total = -refundAmount,
    itemCount = itemCount,
    // The return's own id, not the sale's: a refund note is not a receipt, and scanning it should
    // not offer to refund the same goods a second time.
    saleId = id,
    timestamp = formatTimestamp(occurredAt),
    footerAr = "استرجاع",
)

@OptIn(ExperimentalTime::class)
private fun formatTimestamp(millis: Long): String {
    val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val date = "${local.year}-${local.monthNumber.pad()}-${local.dayOfMonth.pad()}"
    return "$date ${local.hour.pad()}:${local.minute.pad()}"
}

private fun Int.pad(): String = toString().padStart(2, '0')
