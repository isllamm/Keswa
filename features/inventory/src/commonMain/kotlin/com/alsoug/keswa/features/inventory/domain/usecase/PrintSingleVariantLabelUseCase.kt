package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.core.printing.tspl.Tspl
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Prints barcode labels for a single variant directly to the barcode printer.
 */
class PrintSingleVariantLabelUseCase(
    private val variants: IVariantRepository,
    private val prices: IPriceRepository,
    private val settings: ISettingsRepository,
    private val sessions: ISessionManager,
    private val transports: TransportFactory,
    private val ensureBarcode: EnsureVariantBarcodeUseCase,
    private val now: () -> Long,
) {
    suspend operator fun invoke(variantId: String, copies: Int = 1): Result<LabelRunResult> = runCatching {
        sessions.require(Permission.RECEIVE_STOCK)

        val shop = settings.get().getOrThrow()
        if (!shop.hasLabelPrinter) return@runCatching LabelRunResult.NoPrinter

        val variant = requireNotNull(variants.getById(variantId).getOrThrow()) {
            "variant not found: $variantId"
        }

        val barcode = ensureBarcode(variantId).getOrThrow()
        val priceListId = prices.defaultList().getOrThrow()?.id
        val price = priceListId
            ?.let { prices.effectivePrice(variantId, it, now()).getOrThrow() }
            ?: Money.ZERO

        val spec = LabelSpec(
            widthMm = shop.labelWidthMm,
            heightMm = shop.labelHeightMm,
            gapMm = shop.labelGapMm,
        )

        val payload = Tspl.label(
            spec = spec,
            barcode = barcode,
            price = "EGP ${price.format()}",
            sku = variant.sku,
            latinName = variant.sku,
            copies = copies,
        )

        transports.create(shop.labelHost, shop.labelPort).send(payload).fold(
            onSuccess = { LabelRunResult.Printed(copies) },
            onFailure = { LabelRunResult.Unreachable(it.message ?: it::class.simpleName.orEmpty()) },
        )
    }
}
