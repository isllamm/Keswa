package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.core.printing.tspl.Tspl
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Prints hang tags for one line of a delivery — either while unpacking (draft) or after posting.
 */
class PrintReceiptLineLabelsUseCase(
    private val receipts: IStockReceiptRepository,
    private val find: FindStockItemUseCase,
    private val ensureBarcode: EnsureVariantBarcodeUseCase,
    private val settings: ISettingsRepository,
    private val sessions: ISessionManager,
    private val transports: TransportFactory,
) {
    suspend operator fun invoke(
        receiptId: String,
        lineId: String,
        copies: Int,
    ): Result<LabelRunResult> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)
        require(copies > 0) { "copies must be positive" }

        val shop = settings.get().getOrThrow()
        if (!shop.hasLabelPrinter) return@runCatching LabelRunResult.NoPrinter

        val receipt = requireNotNull(receipts.getById(receiptId).getOrThrow()) {
            "receipt not found: $receiptId"
        }
        val line = requireNotNull(receipt.lines.firstOrNull { it.id == lineId }) {
            "line not found: $lineId"
        }
        val item = requireNotNull(find.byVariantId(line.variantId, receipt.locationId).getOrThrow()) {
            "variant not found: ${line.variantId}"
        }
        val barcode = ensureBarcode(line.variantId).getOrThrow()

        // On a draft, a line with a new price prints that new price; once posted, the new price is
        // already the live price.
        val price = (if (receipt.isDraft) line.sellPrice ?: item.price else item.price) ?: Money.ZERO

        val spec = LabelSpec(
            widthMm = shop.labelWidthMm,
            heightMm = shop.labelHeightMm,
            gapMm = shop.labelGapMm,
        )
        val payload = Tspl.label(
            spec = spec,
            barcode = barcode,
            price = "EGP ${price.format()}",
            sku = item.sku,
            latinName = item.sku,
            copies = copies,
        )

        transports.create(shop.labelHost, shop.labelPort).send(payload).fold(
            onSuccess = { LabelRunResult.Printed(copies) },
            onFailure = { LabelRunResult.Unreachable(it.message ?: it::class.simpleName.orEmpty()) },
        )
    }
}
