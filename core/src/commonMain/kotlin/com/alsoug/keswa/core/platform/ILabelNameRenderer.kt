package com.alsoug.keswa.core.platform

import com.alsoug.keswa.core.printing.MonoBitmap

/**
 * Draws one line of text as dots, for the name on a hang tag.
 *
 * A platform bridge for the same reason as [IReceiptRenderer]: TSPL firmware cannot shape Arabic,
 * so the name is sent as a picture.
 */
interface ILabelNameRenderer {
    /** Fits [text] in [widthDots] × [maxHeightDots]: shrinks the font first, then cuts it with "…". */
    fun render(text: String, widthDots: Int, maxHeightDots: Int): MonoBitmap
}
