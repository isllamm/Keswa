package com.alsoug.keswa.features.settings.domain.usecase

import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.printing.escpos.EscPos
import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.core.printing.tspl.Tspl

/** Where a print attempt got to — the UI needs to distinguish these to say anything useful. */
sealed interface PrintResult {
    data object Printed : PrintResult
    data object NotConfigured : PrintResult
    data class Unreachable(val detail: String) : PrintResult
}

/**
 * Prints the page that proves a printer works: Arabic, Latin, digits and a QR.
 *
 * The first thing anyone does with a new printer, and the last thing they do before believing a
 * fault report. Failure is a returned value rather than an exception, because "the printer is off"
 * is an ordinary outcome, not an error in the program.
 */
class PrintTestPageUseCase(
    private val settings: ISettingsRepository,
    private val renderer: IReceiptRenderer,
    private val transports: TransportFactory,
) {
    suspend operator fun invoke(): Result<PrintResult> = runCatching {
        val shop = settings.get().getOrThrow()
        if (!shop.hasReceiptPrinter) return@runCatching PrintResult.NotConfigured

        val bitmap = renderer.renderTestPage(shop.paperWidthDots)
        send(shop.receiptHost, shop.receiptPort, EscPos.document(bitmap))
    }

    private suspend fun send(host: String, port: Int, bytes: ByteArray): PrintResult =
        transports.create(host, port).send(bytes).fold(
            onSuccess = { PrintResult.Printed },
            onFailure = { PrintResult.Unreachable(it.message ?: it::class.simpleName.orEmpty()) },
        )
}

/**
 * Prints one hang tag, so the label printer, its media and the barcode can be checked together.
 *
 * Worth proving early and on real stock: a tag that prints beautifully on direct thermal is
 * unreadable after a fortnight in a shop window.
 */
class PrintTestLabelUseCase(
    private val settings: ISettingsRepository,
    private val transports: TransportFactory,
) {
    suspend operator fun invoke(): Result<PrintResult> = runCatching {
        val shop = settings.get().getOrThrow()
        if (!shop.hasLabelPrinter) return@runCatching PrintResult.NotConfigured

        val bytes = Tspl.testLabel(shop.toLabelSpec())
        transports.create(shop.labelHost, shop.labelPort).send(bytes).fold(
            onSuccess = { PrintResult.Printed },
            onFailure = { PrintResult.Unreachable(it.message ?: it::class.simpleName.orEmpty()) },
        )
    }
}

fun ShopSettings.toLabelSpec(): LabelSpec =
    LabelSpec(widthMm = labelWidthMm, heightMm = labelHeightMm, gapMm = labelGapMm)

class GetSettingsUseCase(private val settings: ISettingsRepository) {
    suspend operator fun invoke(): Result<ShopSettings> = settings.get()
}

class SaveSettingsUseCase(private val settings: ISettingsRepository) {
    suspend operator fun invoke(value: ShopSettings): Result<Unit> {
        if (value.receiptPort !in 1..65_535 || value.labelPort !in 1..65_535) {
            return Result.failure(IllegalArgumentException("port must be between 1 and 65535"))
        }
        if (value.paperWidthDots <= 0) {
            return Result.failure(IllegalArgumentException("paper width must be positive"))
        }
        return settings.save(value)
    }
}
