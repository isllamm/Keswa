package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.printing.MonoBitmap
import com.alsoug.keswa.core.printing.transport.TcpTransport
import com.alsoug.keswa.core.scanning.ScanAccumulator

/**
 * Everything about this shop's hardware, in one place.
 *
 * Defaults are a working configuration rather than blanks, so a fresh install prints as soon as an
 * address is filled in. Every value is here because it varies by shop or by printer — nothing that
 * is genuinely constant belongs in settings.
 */
data class ShopSettings(
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
    val hasReceiptPrinter: Boolean get() = receiptHost.isNotBlank()
    val hasLabelPrinter: Boolean get() = labelHost.isNotBlank()
}
