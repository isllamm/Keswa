package com.alsoug.keswa.core.printing

import com.alsoug.keswa.core.platform.ILabelNameRenderer
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import kotlin.math.ceil

/**
 * Draws the single-line product and colour name for a TSPL hang tag using Java2D [TextLayout].
 *
 * Java's text stack shapes Arabic and applies the Unicode bidirectional algorithm, whereas TSPL
 * printer firmware does not. Rendering the name to a [MonoBitmap] lets `Tspl.label` send it via
 * `BITMAP`.
 */
class DesktopLabelNameRenderer : ILabelNameRenderer {

    override fun render(text: String, widthDots: Int, maxHeightDots: Int): MonoBitmap {
        require(widthDots > 0 && maxHeightDots > 0) { "dimensions must be positive" }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return MonoBitmap(widthDots, 1)

        val measureGraphics = measuringImage.createGraphics().withHints()
        val frc = measureGraphics.fontRenderContext

        var chosenFont = Font(Font.SANS_SERIF, Font.PLAIN, MIN_FONT_SIZE)
        var fittedText = trimmed
        var chosenLayout = TextLayout(fittedText, chosenFont, frc)

        // Step down from 30 px to 18 px until the text fits both width and height.
        var foundFit = false
        for (size in MAX_FONT_SIZE downTo MIN_FONT_SIZE) {
            val font = Font(Font.SANS_SERIF, Font.PLAIN, size)
            val layout = TextLayout(trimmed, font, frc)
            val totalHeight = ceil(layout.ascent + layout.descent).toInt()
            if (layout.advance <= widthDots && totalHeight <= maxHeightDots) {
                chosenFont = font
                chosenLayout = layout
                foundFit = true
                break
            }
        }

        // If still too wide at 18 px, truncate and append "…" until it fits.
        if (!foundFit) {
            chosenFont = Font(Font.SANS_SERIF, Font.PLAIN, MIN_FONT_SIZE)
            var cutLength = trimmed.length
            while (cutLength > 1) {
                cutLength--
                val candidate = trimmed.substring(0, cutLength).trimEnd() + ELLIPSIS
                val layout = TextLayout(candidate, chosenFont, frc)
                if (layout.advance <= widthDots) {
                    fittedText = candidate
                    chosenLayout = layout
                    break
                }
            }
        }
        measureGraphics.dispose()

        val rawHeight = ceil(chosenLayout.ascent + chosenLayout.descent).toInt() + VERTICAL_PAD
        val height = rawHeight.coerceIn(1, maxHeightDots)

        val image = BufferedImage(widthDots, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics().withHints()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, widthDots, height)
        graphics.color = Color.BLACK

        val isArabic = !chosenLayout.isLeftToRight || fittedText.any { it.code in 0x0600..0x06FF }
        val x = if (isArabic) {
            (widthDots - chosenLayout.advance).coerceAtLeast(0f)
        } else {
            0f
        }
        val baselineY = chosenLayout.ascent.coerceAtMost((height - 1).toFloat())
        chosenLayout.draw(graphics, x, baselineY)
        graphics.dispose()

        return image.toMonoBitmap()
    }

    private fun Graphics2D.withHints(): Graphics2D = apply {
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    }

    private fun BufferedImage.toMonoBitmap(): MonoBitmap {
        val bitmap = MonoBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val luma = ((rgb shr 16 and 0xFF) * 299 +
                    (rgb shr 8 and 0xFF) * 587 +
                    (rgb and 0xFF) * 114) / 1000
                bitmap[x, y] = luma < THRESHOLD
            }
        }
        return bitmap
    }

    private val measuringImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)

    private companion object {
        const val MAX_FONT_SIZE = 30
        const val MIN_FONT_SIZE = 18
        const val VERTICAL_PAD = 2
        const val THRESHOLD = 128
        const val ELLIPSIS = "…"
    }
}
