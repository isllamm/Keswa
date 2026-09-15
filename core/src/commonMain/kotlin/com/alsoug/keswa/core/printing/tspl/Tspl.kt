package com.alsoug.keswa.core.printing.tspl

import com.alsoug.keswa.core.printing.MonoBitmap

/**
 * Label size and media settings. Printer-specific, so configuration rather than constants.
 */
data class LabelSpec(
    val widthMm: Int = 40,
    val heightMm: Int = 30,
    val gapMm: Int = 2,
    val density: Int = 8,
    val speed: Int = 4,
    val dpi: Int = 203,
) {
    val widthDots: Int get() = widthMm * dpi / 25
}

/**
 * TSPL commands for hang tags.
 *
 * TSPL is ASCII, so most of this is text — but the product name is sent as a bitmap for the same
 * reason receipts are (see [com.alsoug.keswa.core.printing.escpos.EscPos.document]): the firmware
 * will not shape Arabic.
 *
 * Tags must be printed on **thermal transfer** stock with a ribbon, not direct thermal. Direct
 * thermal fades in sunlight and dies against fabric, so the tag is unreadable long before the
 * garment sells.
 */
object Tspl {

    private const val CRLF = "\r\n"

    fun label(
        spec: LabelSpec,
        barcode: String,
        price: String,
        sku: String,
        latinName: String? = null,
        nameBitmap: MonoBitmap? = null,
        copies: Int = 1,
    ): ByteArray {
        require(copies >= 1) { "copies must be at least 1" }

        val head = buildString {
            append("SIZE ${spec.widthMm} mm,${spec.heightMm} mm").append(CRLF)
            append("GAP ${spec.gapMm} mm,0 mm").append(CRLF)
            append("DENSITY ${spec.density}").append(CRLF)
            append("SPEED ${spec.speed}").append(CRLF)
            append("DIRECTION 1").append(CRLF)
            append("CLS").append(CRLF)
        }.encodeToByteArray()

        val name = when {
            nameBitmap != null -> bitmap(x = 10, y = 10, bitmap = nameBitmap)
            latinName != null -> text(x = 10, y = 14, content = latinName).encodeToByteArray()
            else -> ByteArray(0)
        }

        val body = buildString {
            append(text(x = 10, y = 54, content = sku, font = "1"))
            append(text(x = 10, y = 78, content = price, xScale = 2, yScale = 2))
            // EAN-13, 60 dots tall, human-readable digits beneath
            append("BARCODE 10,110,\"EAN13\",60,1,0,2,4,\"$barcode\"").append(CRLF)
            append("PRINT $copies,1").append(CRLF)
        }.encodeToByteArray()

        return head + name + body
    }

    fun text(
        x: Int,
        y: Int,
        content: String,
        font: String = "2",
        rotation: Int = 0,
        xScale: Int = 1,
        yScale: Int = 1,
    ): String =
        "TEXT $x,$y,\"$font\",$rotation,$xScale,$yScale,\"${content.replace("\"", "\\\"")}\"$CRLF"

    /**
     * BITMAP x,y,widthBytes,height,mode,<raw bytes>
     *
     * ⚠️ TSPL inverts the convention ESC/POS uses: in a TSPL bitmap a set bit is **white**, so the
     * data is sent inverted. Vendors document this inconsistently — confirm against the actual
     * printer before the first production run, and flip [invert] if the tag comes out as a negative.
     */
    fun bitmap(x: Int, y: Int, bitmap: MonoBitmap, mode: Int = 0, invert: Boolean = true): ByteArray {
        val header = "BITMAP $x,$y,${bitmap.rowBytes},${bitmap.height},$mode,".encodeToByteArray()
        val payload = if (invert) {
            ByteArray(bitmap.data.size) { (bitmap.data[it].toInt().inv() and 0xFF).toByte() }
        } else {
            bitmap.data.copyOf()
        }
        return header + payload + CRLF.encodeToByteArray()
    }

    /** A self-test label, for proving the printer and media before any real stock is tagged. */
    fun testLabel(spec: LabelSpec): ByteArray =
        label(
            spec = spec,
            barcode = "2000000000015",
            price = "EGP 0.00",
            sku = "TEST-LABEL",
            latinName = "Keswa test",
        )
}
