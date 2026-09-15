package com.alsoug.keswa.core.platform

import com.alsoug.keswa.core.printing.MonoBitmap
import com.alsoug.keswa.core.printing.model.Receipt

/**
 * Draws a receipt as dots.
 *
 * A platform bridge (ADR-018) because glyph rasterisation is: shaping Arabic, resolving a font that
 * has the glyphs, and laying out bidirectional text are all things the platform already does well
 * and that no amount of shared code reproduces.
 *
 * The *document* and the *raster encoding* either side of it stay pure and testable — this is the
 * only part that has to know what a font is.
 */
interface IReceiptRenderer {

    /** [widthDots] is 576 for 80 mm paper, 384 for 58 mm. */
    fun render(receipt: Receipt, widthDots: Int = MonoBitmap.WIDTH_80MM): MonoBitmap

    /** A page that exercises Arabic, Latin, digits and the QR, for proving a printer. */
    fun renderTestPage(widthDots: Int = MonoBitmap.WIDTH_80MM): MonoBitmap
}
