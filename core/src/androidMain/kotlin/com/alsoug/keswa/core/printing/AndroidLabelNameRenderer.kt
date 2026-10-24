package com.alsoug.keswa.core.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.alsoug.keswa.core.platform.ILabelNameRenderer
import kotlin.math.ceil

/**
 * Draws the single-line product and colour name for a TSPL hang tag using `android.graphics`.
 *
 * Sibling of `DesktopLabelNameRenderer`: shapes Arabic and resolves bidirectional runs on the
 * handheld before the label is sent to the printer as a TSPL `BITMAP`.
 */
class AndroidLabelNameRenderer : ILabelNameRenderer {

    override fun render(text: String, widthDots: Int, maxHeightDots: Int): MonoBitmap {
        require(widthDots > 0 && maxHeightDots > 0) { "dimensions must be positive" }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return MonoBitmap(widthDots, 1)

        var chosenPaint = paintOf(MIN_FONT_SIZE.toFloat())
        var fittedText = trimmed
        var foundFit = false

        for (size in MAX_FONT_SIZE downTo MIN_FONT_SIZE) {
            val paint = paintOf(size.toFloat())
            val metrics = paint.fontMetrics
            val totalHeight = ceil(-metrics.ascent + metrics.descent).toInt()
            if (paint.measureText(trimmed) <= widthDots && totalHeight <= maxHeightDots) {
                chosenPaint = paint
                foundFit = true
                break
            }
        }

        if (!foundFit) {
            chosenPaint = paintOf(MIN_FONT_SIZE.toFloat())
            var cutLength = trimmed.length
            while (cutLength > 1) {
                cutLength--
                val candidate = trimmed.substring(0, cutLength).trimEnd() + ELLIPSIS
                if (chosenPaint.measureText(candidate) <= widthDots) {
                    fittedText = candidate
                    break
                }
            }
        }

        val metrics = chosenPaint.fontMetrics
        val rawHeight = ceil(-metrics.ascent + metrics.descent).toInt() + VERTICAL_PAD
        val height = rawHeight.coerceIn(1, maxHeightDots)

        val bitmap = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val isArabic = fittedText.any { it.code in 0x0600..0x06FF }
        val advance = chosenPaint.measureText(fittedText)
        val x = if (isArabic) {
            (widthDots - advance).coerceAtLeast(0f)
        } else {
            0f
        }
        val baselineY = (-metrics.ascent).coerceAtMost((height - 1).toFloat())
        canvas.drawText(fittedText, x, baselineY, chosenPaint)

        return bitmap.toMonoBitmap()
    }

    private fun paintOf(size: Float): Paint = Paint().apply {
        color = Color.BLACK
        textSize = size
        isAntiAlias = false
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
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
                mono[x, y] = luma < THRESHOLD
            }
        }
        return mono
    }

    private companion object {
        const val MAX_FONT_SIZE = 30
        const val MIN_FONT_SIZE = 18
        const val VERTICAL_PAD = 2
        const val THRESHOLD = 128
        const val ELLIPSIS = "…"
    }
}
