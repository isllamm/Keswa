package com.alsoug.keswa.core.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.printing.model.Receipt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Draws receipts with `android.graphics`.
 *
 * The sibling of `DesktopReceiptRenderer`, and for the same reason: shaping Arabic, resolving a
 * font that has the glyphs, and laying out bidirectional text are things the platform already does
 * well and that no amount of shared code reproduces. Everything visible reaches the printer as one
 * raster image, so the firmware never sees a character it could mangle.
 *
 * The *document* and the *raster encoding* either side of this stay pure and testable; only this
 * class has to know what a font is.
 *
 * ⚠️ Unlike the protocol layers, this cannot be covered by a golden-byte test — it depends on the
 * device's own font stack, so what it draws is only verifiable on a device with a printer attached.
 * The layout deliberately mirrors the desktop renderer line for line so the two agree by
 * construction rather than by luck.
 */
class AndroidReceiptRenderer : IReceiptRenderer {

    override fun render(receipt: Receipt, widthDots: Int): MonoBitmap = draw(widthDots) { page ->
        page.centre(receipt.shopName, titlePaint)
        page.centre(receipt.shopNameAr, arabicPaint)
        page.centre(receipt.addressLine, smallPaint)
        page.rule()

        receipt.lines.forEach { line ->
            page.leftRight("${line.quantity} × ${line.description}", line.amount.format(), bodyPaint)
            if (line.descriptionAr.isNotBlank()) page.right(line.descriptionAr, arabicPaint)
        }

        page.rule()
        page.leftRight("Subtotal", receipt.subtotal.format(), bodyPaint)
        if (receipt.hasDiscount) page.leftRight("Discount", "-" + receipt.discount.format(), bodyPaint)
        page.leftRight("VAT · ض.ق.م", receipt.vat.format(), bodyPaint)
        page.leftRight("TOTAL · الإجمالي", receipt.total.format(), titlePaint)
        page.rule()

        page.centre("${receipt.itemCount} pcs · قطعة", smallPaint)
        page.centre(receipt.timestamp, smallPaint)
        page.qr(receipt.saleId)
        page.centre(receipt.saleId, tinyPaint)
        page.centre(receipt.footerAr, arabicPaint)
    }

    override fun renderTestPage(widthDots: Int): MonoBitmap = draw(widthDots) { page ->
        page.centre("KESWA", titlePaint)
        page.centre("Printer test · اختبار الطابعة", arabicPaint)
        page.rule()
        page.left("Latin: The quick brown fox", bodyPaint)
        page.right("عربي: قميص أكسفورد أزرق مقاس كبير", arabicPaint)
        page.left("Digits: 0123456789", bodyPaint)
        page.leftRight("EGP", "1,234.56", bodyPaint)
        page.rule()
        page.left("If the Arabic above reads right-to-left", smallPaint)
        page.left("with joined letters, rendering is correct.", smallPaint)
        page.qr("KESWA-TEST")
    }

    /**
     * Two passes: measure the content to size the page, then draw it. A thermal roll has no fixed
     * height, so the bitmap must be exactly as tall as what it holds.
     */
    private fun draw(widthDots: Int, content: (Page) -> Unit): MonoBitmap {
        val measuring = Page(widthDots, canvas = null)
        content(measuring)
        val height = measuring.cursor + PADDING

        val bitmap = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        content(Page(widthDots, canvas))

        return bitmap.toMonoBitmap()
    }

    private inner class Page(val width: Int, val canvas: Canvas?) {
        var cursor = PADDING

        fun left(text: String, paint: Paint) = place(text, paint) { _ -> PADDING.toFloat() }

        fun right(text: String, paint: Paint) = place(text, paint) { advance ->
            width - PADDING - advance
        }

        fun centre(text: String, paint: Paint) = place(text, paint) { advance ->
            (width - advance) / 2f
        }

        fun leftRight(left: String, right: String, paint: Paint) {
            if (left.isBlank()) return
            val metrics = paint.fontMetrics
            cursor += (-metrics.ascent).toInt()
            canvas?.drawText(left, PADDING.toFloat(), cursor.toFloat(), paint)
            canvas?.drawText(right, width - PADDING - paint.measureText(right), cursor.toFloat(), paint)
            cursor += (metrics.descent + LINE_GAP).toInt()
        }

        fun rule() {
            cursor += LINE_GAP
            canvas?.drawRect(
                PADDING.toFloat(),
                cursor.toFloat(),
                (width - PADDING).toFloat(),
                (cursor + 2).toFloat(),
                rulePaint,
            )
            cursor += 2 + LINE_GAP
        }

        fun qr(content: String) {
            cursor += LINE_GAP
            if (canvas != null) {
                val matrix = QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    QR_SIZE,
                    QR_SIZE,
                    mapOf(
                        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                        EncodeHintType.MARGIN to 1,
                    ),
                )
                val originX = (width - QR_SIZE) / 2
                for (y in 0 until QR_SIZE) {
                    for (x in 0 until QR_SIZE) {
                        if (matrix.get(x, y)) {
                            canvas.drawRect(
                                (originX + x).toFloat(),
                                (cursor + y).toFloat(),
                                (originX + x + 1).toFloat(),
                                (cursor + y + 1).toFloat(),
                                rulePaint,
                            )
                        }
                    }
                }
            }
            cursor += QR_SIZE + LINE_GAP
        }

        private fun place(text: String, paint: Paint, x: (Float) -> Float) {
            if (text.isBlank()) return
            val metrics = paint.fontMetrics
            cursor += (-metrics.ascent).toInt()
            canvas?.drawText(text, x(paint.measureText(text)), cursor.toFloat(), paint)
            cursor += (metrics.descent + LINE_GAP).toInt()
        }
    }

    private fun Bitmap.toMonoBitmap(): MonoBitmap {
        val mono = MonoBitmap(width, height)
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val luma = ((pixel shr 16 and 0xFF) * 299 +
                    (pixel shr 8 and 0xFF) * 587 +
                    (pixel and 0xFF) * 114) / 1000
                // Hard threshold, not dithering: dithered text turns to mush on a thermal head.
                mono[x, y] = luma < THRESHOLD
            }
        }
        return mono
    }

    private fun paintOf(size: Float, bold: Boolean = false, monospace: Boolean = false) =
        Paint().apply {
            color = Color.BLACK
            textSize = size
            // No antialiasing: a thermal head is one bit per dot, so grey edges become noise.
            isAntiAlias = false
            typeface = Typeface.create(
                if (monospace) Typeface.MONOSPACE else Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private val titlePaint = paintOf(26f, bold = true)
    private val bodyPaint = paintOf(22f)
    private val arabicPaint = paintOf(24f)
    private val smallPaint = paintOf(18f)
    private val tinyPaint = paintOf(15f, monospace = true)
    private val rulePaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = false
    }

    private companion object {
        const val PADDING = 12
        const val LINE_GAP = 6
        const val QR_SIZE = 140
        const val THRESHOLD = 128
    }
}
