package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Prints barcode labels for a single variant directly to the barcode printer.
 */
class PrintSingleVariantLabelUseCase(
    private val find: FindStockItemUseCase,
    private val ensureBarcode: EnsureVariantBarcodeUseCase,
    private val tags: HangTagBuilder,
    private val settings: ISettingsRepository,
    private val sessions: ISessionManager,
    private val transports: TransportFactory,
) {
    suspend operator fun invoke(
        variantId: String,
        locationId: String,
        copies: Int = 1,
    ): Result<LabelRunResult> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)

        val shop = settings.get().getOrThrow()
        if (!shop.hasLabelPrinter) return@runCatching LabelRunResult.NoPrinter

        val item = requireNotNull(find.byVariantId(variantId, locationId).getOrThrow()) {
            "variant not found: $variantId"
        }

        val barcode = ensureBarcode(variantId).getOrThrow()
        val price = item.price ?: Money.ZERO

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
