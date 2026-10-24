package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Prints hang tags for one receipt line (before or after posting) with the user-chosen copy count.
 *
 * Price rule:
 * - Draft line with a new `sellPrice` -> prints the line's new price.
 * - Draft line without `sellPrice` -> prints the live price.
 * - Posted receipt -> prints the live price (the new price is already live).
 */
class PrintReceiptLineLabelsUseCase(
    private val receipts: IStockReceiptRepository,
    private val find: FindStockItemUseCase,
    private val ensureBarcode: EnsureVariantBarcodeUseCase,
    private val tags: HangTagBuilder,
    private val settings: ISettingsRepository,
    private val sessions: ISessionManager,
    private val transports: TransportFactory,
) {
    suspend operator fun invoke(
        receiptId: String,
        lineId: String,
        copies: Int,
    ): Result<LabelRunResult> = runCatchingCancellable {
        require(copies >= 1) { "copies must be at least 1" }
        sessions.require(Permission.RECEIVE_STOCK)

        val shop = settings.get().getOrThrow()
        if (!shop.hasLabelPrinter) return@runCatchingCancellable LabelRunResult.NoPrinter

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
        val price = (if (receipt.isDraft) line.sellPrice ?: item.price else item.price) ?: Money.ZERO

        val spec = LabelSpec(
            widthMm = shop.labelWidthMm,
            heightMm = shop.labelHeightMm,
            gapMm = shop.labelGapMm,
        )

        val payload = tags.build(
            spec = spec,
            item = item,
            barcode = barcode,
            price = price,
            copies = copies,
        )

        transports.create(shop.labelHost, shop.labelPort).send(payload).fold(
            onSuccess = { LabelRunResult.Printed(copies) },
            onFailure = { LabelRunResult.Unreachable(it.message ?: it::class.simpleName.orEmpty()) },
        )
    }
}
