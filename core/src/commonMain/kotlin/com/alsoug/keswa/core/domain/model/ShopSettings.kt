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
     * The language the staff read the app in — `en` or `ar`.
     *
     * A shop setting, not a machine one. A till in Cairo on an English Windows install still has
     * Arabic staff standing at it, so following the operating system would be following the wrong
     * thing. The catalogue's own `nameAr` fields are unaffected either way: a product is named by
     * the shop.
     */
    val languageCode: String = "en",
    /**
     * VAT rate in basis points, **zero by default**.
     *
     * Most small shops are not registered, and printing a VAT line when you are not registered is
     * a legal problem rather than a cosmetic one. 14% is `1_400`.
     */
    val vatBasisPoints: Int = 0,
    /**
     * How long after a sale goods may come back on a seller's own authority.
     *
     * Beyond it a return still happens — it just needs an admin, in place. The permission *is*
     * the policy: what a policy must never be is a rule staff route around by not using the till.
     */
    val returnWindowDays: Int = 14,
    /** No-receipt returns refund at the lowest price the variant ever sold for, and need an admin. */
    val allowNoReceiptReturns: Boolean = false,
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
