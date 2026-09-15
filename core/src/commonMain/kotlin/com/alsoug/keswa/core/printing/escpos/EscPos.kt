package com.alsoug.keswa.core.printing.escpos

import com.alsoug.keswa.core.printing.MonoBitmap

/**
 * ESC/POS command bytes.
 *
 * Pure byte generation with no platform dependency, so the whole protocol is unit-testable against
 * recorded fixtures with no printer attached — see `EscPosTest`.
 */
object EscPos {

    private const val ESC = 0x1B.toByte()
    private const val GS = 0x1D.toByte()

    /** ESC @ — reset to a known state. Send first, always. */
    fun initialise(): ByteArray = byteArrayOf(ESC, 0x40)

    /** ESC d n — feed n lines. */
    fun feed(lines: Int): ByteArray {
        require(lines in 0..255) { "feed takes 0..255 lines" }
        return byteArrayOf(ESC, 0x64, lines.toByte())
    }

    /** GS V B n — feed and partial cut, leaving a tab so the receipt does not drop. */
    fun cut(feedBefore: Int = 4): ByteArray =
        feed(feedBefore) + byteArrayOf(GS, 0x56, 0x42, 0x00)

    /**
     * ESC p m t1 t2 — pulse the drawer kick connector.
     *
     * The drawer is wired through the printer, which is why opening it is a print command and why
     * a browser cannot do it (§2 of the architecture plan).
     */
    fun openDrawer(): ByteArray = byteArrayOf(ESC, 0x70, 0x00, 0x19, 0xFA.toByte())

    /**
     * GS v 0 — raster bit image.
     *
     * The width is sent in **bytes**, not dots, so a bitmap whose width is not a multiple of eight
     * is transmitted with its row padding included. [MonoBitmap] already stores it that way.
     */
    fun raster(bitmap: MonoBitmap): ByteArray {
        val widthBytes = bitmap.rowBytes
        val height = bitmap.height
        val header = byteArrayOf(
            GS, 0x76, 0x30, 0x00,
            (widthBytes and 0xFF).toByte(), (widthBytes shr 8 and 0xFF).toByte(),
            (height and 0xFF).toByte(), (height shr 8 and 0xFF).toByte(),
        )
        return header + bitmap.data
    }

    /**
     * A complete receipt: reset, image, cut.
     *
     * Everything visible is one raster image rather than text commands. Thermal firmware mangles
     * Arabic — CP864/CP1256 confusion, no letter shaping, no ligatures, no right-to-left — and no
     * amount of code-page juggling fixes it reliably. Sending pixels sidesteps the firmware
     * entirely, and costs nothing for Latin text.
     */
    fun document(bitmap: MonoBitmap, openDrawer: Boolean = false): ByteArray =
        initialise() +
            raster(bitmap) +
            cut() +
            (if (openDrawer) openDrawer() else ByteArray(0))
}
