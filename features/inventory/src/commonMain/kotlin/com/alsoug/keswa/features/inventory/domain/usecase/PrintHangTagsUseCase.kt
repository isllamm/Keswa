package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.core.printing.tspl.Tspl
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/** Where a label run got to. Failure is a returned value: "the printer is off" is not a bug. */
sealed interface LabelRunResult {
    data class Printed(val tags: Int) : LabelRunResult
    data object NoPrinter : LabelRunResult
    data class Unreachable(val detail: String) : LabelRunResult
    /** Lines that could not be tagged, and why — a run does not silently skip them. */
    data class Incomplete(val tags: Int, val skipped: List<String>) : LabelRunResult
}

/**
 * Prints hang tags for a delivery that has just been received.
 *
 * Printing from the receipt's own lines is the point: the quantities are already right, so nobody
 * types "how many navy mediums did we get" a second time and gets it wrong.
 *
 * **One tag per piece**, not per line. Twenty shirts need twenty tags, and a printer that produces
 * one tag for a carton of twenty has misunderstood what a hang tag is for.
 */
class PrintHangTagsUseCase(
    private val receipts: IStockReceiptRepository,
    private val variants: IVariantRepository,
    private val prices: IPriceRepository,
    private val settings: ISettingsRepository,
    private val sessions: ISessionManager,
    private val transports: TransportFactory,
    private val now: () -> Long,
) {

    suspend operator fun invoke(receiptId: String): Result<LabelRunResult> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)

        val shop = settings.get().getOrThrow()
        if (!shop.hasLabelPrinter) return@runCatching LabelRunResult.NoPrinter

        val receipt = requireNotNull(receipts.getById(receiptId).getOrThrow()) {
            "receipt not found: $receiptId"
        }
        val priceListId = prices.defaultList().getOrThrow()?.id

        val spec = LabelSpec(
            widthMm = shop.labelWidthMm,
            heightMm = shop.labelHeightMm,
            gapMm = shop.labelGapMm,
        )

        val skipped = mutableListOf<String>()
        var tags = 0
        val payload = buildList {
            receipt.lines.forEach { line ->
                val variant = variants.getById(line.variantId).getOrThrow()
                val barcode = variants.barcodesFor(line.variantId).getOrThrow()
                    .firstOrNull { it.isPrimary }?.barcode

                // A tag with no barcode cannot be scanned at the till, which is the only reason it
                // exists — so it is reported rather than printed blank.
                if (variant == null || barcode == null) {
                    skipped += variant?.sku ?: line.variantId
                    return@forEach
                }

                val price = priceListId
                    ?.let { prices.effectivePrice(line.variantId, it, now()).getOrThrow() }
                    ?: Money.ZERO

                add(
                    Tspl.label(
                        spec = spec,
                        barcode = barcode,
                        price = "EGP ${price.format()}",
                        sku = variant.sku,
                        latinName = variant.sku,
                        copies = line.quantity,
                    ),
                )
                tags += line.quantity
            }
        }

        if (payload.isEmpty()) {
            return@runCatching LabelRunResult.Incomplete(tags = 0, skipped = skipped)
        }

        val bytes = payload.reduce { all, next -> all + next }
        transports.create(shop.labelHost, shop.labelPort).send(bytes).fold(
            onSuccess = {
                if (skipped.isEmpty()) {
                    LabelRunResult.Printed(tags)
                } else {
                    LabelRunResult.Incomplete(tags, skipped)
                }
            },
            onFailure = { LabelRunResult.Unreachable(it.message ?: it::class.simpleName.orEmpty()) },
        )
    }
}
