package com.alsoug.keswa.core.printing

import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.printing.model.Receipt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.TextLayout
import java.awt.image.BufferedImage

/**
 * Draws receipts with AWT.
 *
 * Java's text stack shapes Arabic and resolves bidirectional runs correctly, which is the entire
 * problem this class exists to solve: ESC/POS firmware does not. Everything visible is sent to the
 * printer as one raster image, so the firmware never sees a character it could mangle.
 *
 * Deviates from D9 in the architecture plan, which assumed Compose's graphics APIs would serve.
 * They would, but `TextMeasurer` needs a font-family resolver that is itself platform-supplied — so
 * rendering is a platform bridge either way, and AWT is the simpler, better-trodden path here.
 * Android gets its own implementation on `android.graphics.Canvas` in Phase 6.
 */
class DesktopReceiptRenderer : IReceiptRenderer {

    override fun render(receipt: Receipt, widthDots: Int): MonoBitmap = draw(widthDots) { page ->
        page.centre(receipt.shopName, titleFont)
        page.centre(receipt.shopNameAr, arabicFont)
        page.centre(receipt.addressLine, smallFont)
        page.rule()

        receipt.lines.forEach { line ->
            page.leftRight("${line.quantity} × ${line.description}", line.amount.format(), bodyFont)
            if (line.descriptionAr.isNotBlank()) page.right(line.descriptionAr, arabicFont)
        }

        page.rule()
        page.leftRight("Subtotal", receipt.subtotal.format(), bodyFont)
        if (receipt.hasDiscount) page.leftRight("Discount", "-" + receipt.discount.format(), bodyFont)
        page.leftRight("VAT · ض.ق.م", receipt.vat.format(), bodyFont)
        page.leftRight("TOTAL · الإجمالي", receipt.total.format(), titleFont)
        page.rule()

        page.centre("${receipt.itemCount} pcs · قطعة", smallFont)
        page.centre(receipt.timestamp, smallFont)
        page.qr(receipt.saleId)
        page.centre(receipt.saleId, tinyFont)
        page.centre(receipt.footerAr, arabicFont)
    }

    override fun renderTestPage(widthDots: Int): MonoBitmap = draw(widthDots) { page ->
        page.centre("KESWA", titleFont)
        page.centre("Printer test · اختبار الطابعة", arabicFont)
        page.rule()
        page.left("Latin: The quick brown fox", bodyFont)
        page.right("عربي: قميص أكسفورد أزرق مقاس كبير", arabicFont)
        page.left("Digits: 0123456789", bodyFont)
        page.leftRight("EGP", "1,234.56", bodyFont)
        page.rule()
        page.left("If the Arabic above reads right-to-left", smallFont)
        page.left("with joined letters, rendering is correct.", smallFont)
        page.qr("KESWA-TEST")
    }

    /**
     * Two passes: measure the content to size the page, then draw it. A thermal roll has no fixed
     * height, so the bitmap must be exactly as tall as what it holds.
     */
    private fun draw(widthDots: Int, content: (Page) -> Unit): MonoBitmap {
        val measuring = Page(widthDots, measuringImage.createGraphics().withHints(), dryRun = true)
        content(measuring)
        val height = measuring.cursor + PADDING

        val image = BufferedImage(widthDots, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics().withHints()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, widthDots, height)
        graphics.color = Color.BLACK
        content(Page(widthDots, graphics, dryRun = false))
        graphics.dispose()

        return image.toMonoBitmap()
    }

    private fun java.awt.Graphics2D.withHints(): java.awt.Graphics2D = apply {
        // No antialiasing: a thermal head is one bit per dot, so grey edges become noise.
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    }

    private inner class Page(
        val width: Int,
        val graphics: java.awt.Graphics2D,
        val dryRun: Boolean,
    ) {
        var cursor = PADDING

        fun left(text: String, font: Font) = place(text, font) { layout, _ -> PADDING.toFloat() }

        fun right(text: String, font: Font) = place(text, font) { layout, w ->
            (width - PADDING - w)
        }

        fun centre(text: String, font: Font) = place(text, font) { _, w -> (width - w) / 2f }

        fun leftRight(left: String, right: String, font: Font) {
            val layout = layoutOf(left, font) ?: return
            val rightLayout = layoutOf(right, font)
            val lineHeight = layout.ascent + layout.descent + LINE_GAP
            cursor += layout.ascent.toInt()
            if (!dryRun) {
                layout.draw(graphics, PADDING.toFloat(), cursor.toFloat())
                rightLayout?.draw(
                    graphics,
                    width - PADDING - rightLayout.advance,
                    cursor.toFloat(),
                )
            }
            cursor += (layout.descent + LINE_GAP).toInt()
        }

        fun rule() {
            cursor += LINE_GAP
            if (!dryRun) graphics.fillRect(PADDING, cursor, width - 2 * PADDING, 2)
            cursor += 2 + LINE_GAP
        }

        fun qr(content: String) {
            val size = QR_SIZE
            cursor += LINE_GAP
            if (!dryRun) {
                val matrix = QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
                )
                val originX = (width - size) / 2
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        if (matrix.get(x, y)) graphics.fillRect(originX + x, cursor + y, 1, 1)
                    }
                }
            }
            cursor += size + LINE_GAP
        }

        private fun place(text: String, font: Font, x: (TextLayout, Float) -> Float) {
            val layout = layoutOf(text, font) ?: return
            cursor += layout.ascent.toInt()
            if (!dryRun) layout.draw(graphics, x(layout, layout.advance), cursor.toFloat())
            cursor += (layout.descent + LINE_GAP).toInt()
        }

        private fun layoutOf(text: String, font: Font): TextLayout? {
            if (text.isBlank()) return null
            // TextLayout, not drawString: it applies the bidi algorithm and shapes Arabic.
            return TextLayout(text, font, graphics.fontRenderContext)
        }
    }

    private fun BufferedImage.toMonoBitmap(): MonoBitmap {
        val bitmap = MonoBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val luma = ((rgb shr 16 and 0xFF) * 299 + (rgb shr 8 and 0xFF) * 587 + (rgb and 0xFF) * 114) / 1000
                // Hard threshold, not dithering: dithered text turns to mush on a thermal head.
                bitmap[x, y] = luma < THRESHOLD
            }
        }
        return bitmap
    }

    private val measuringImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)

    // SansSerif is a logical font, so the JVM falls back to whatever the OS has with Arabic glyphs.
    private val titleFont = Font(Font.SANS_SERIF, Font.BOLD, 26)
    private val bodyFont = Font(Font.SANS_SERIF, Font.PLAIN, 22)
    private val arabicFont = Font(Font.SANS_SERIF, Font.PLAIN, 24)
    private val smallFont = Font(Font.SANS_SERIF, Font.PLAIN, 18)
    private val tinyFont = Font(Font.MONOSPACED, Font.PLAIN, 15)

    private companion object {
        const val PADDING = 12
        const val LINE_GAP = 6
        const val QR_SIZE = 140
        const val THRESHOLD = 128
    }
}
