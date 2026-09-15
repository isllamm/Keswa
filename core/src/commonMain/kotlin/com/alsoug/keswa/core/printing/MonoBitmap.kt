package com.alsoug.keswa.core.printing

/**
 * A 1-bit image, stored exactly as a thermal printer wants it.
 *
 * Rows are packed MSB-first into whole bytes, so a 576-dot row is 72 bytes and a 100-dot row is 13
 * with the last four bits unused. That padding is the shape the ESC/POS raster command expects,
 * which is why it lives here rather than being re-derived at send time.
 *
 * `true` means a dot is burned — black on paper.
 */
class MonoBitmap(val width: Int, val height: Int) {

    init {
        require(width > 0 && height > 0) { "bitmap must have positive dimensions" }
    }

    /** Bytes per row, rounded up — the padding the raster command assumes. */
    val rowBytes: Int = (width + 7) / 8

    val data: ByteArray = ByteArray(rowBytes * height)

    operator fun set(x: Int, y: Int, on: Boolean) {
        if (x !in 0 until width || y !in 0 until height) return
        val index = y * rowBytes + (x shr 3)
        val mask = (0x80 ushr (x and 7)).toByte()
        data[index] = if (on) {
            (data[index].toInt() or mask.toInt()).toByte()
        } else {
            (data[index].toInt() and mask.toInt().inv()).toByte()
        }
    }

    operator fun get(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        val index = y * rowBytes + (x shr 3)
        val mask = 0x80 ushr (x and 7)
        return (data[index].toInt() and mask) != 0
    }

    fun fill(on: Boolean) {
        data.fill(if (on) 0xFF.toByte() else 0)
    }

    companion object {
        /** 80 mm paper at 203 dpi. 58 mm is 384. Never hardcode either at a call site. */
        const val WIDTH_80MM = 576
        const val WIDTH_58MM = 384
    }
}
