package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.printing.MonoBitmap
import com.alsoug.keswa.core.printing.transport.TcpTransport
import com.alsoug.keswa.core.scanning.ScanAccumulator

/**
 * Everything that varies by shop: who it is, what it charges tax at, and what it prints on.
 *
 * Defaults are a working configuration rather than blanks, so a fresh install prints as soon as an
 * address is filled in. Every value is here because it varies by shop or by printer — nothing that
 * is genuinely constant belongs in settings.
 */
data class ShopSettings(
    val shopName: String = "Keswa",
    val shopNameAr: String = "كسوة",
    val addressLine: String = "",
    /**
     * VAT rate in basis points, **zero by default**.
     *
     * Most small shops are not registered, and printing a VAT line when you are not registered is
     * a legal problem rather than a cosmetic one. 14% is `1_400`.
     */
    val vatBasisPoints: Int = 0,
    val receiptHost: String = "",
    val receiptPort: Int = TcpTransport.DEFAULT_PORT,
    val paperWidthDots: Int = MonoBitmap.WIDTH_80MM,
    val labelHost: String = "",
    val labelPort: Int = TcpTransport.DEFAULT_PORT,
    val labelWidthMm: Int = 40,
    val labelHeightMm: Int = 30,
    val labelGapMm: Int = 2,
    val scanMaxGapMillis: Long = ScanAccumulator.DEFAULT_MAX_GAP_MILLIS,
) {
    val chargesVat: Boolean get() = vatBasisPoints > 0
    val hasReceiptPrinter: Boolean get() = receiptHost.isNotBlank()
    val hasLabelPrinter: Boolean get() = labelHost.isNotBlank()
}
