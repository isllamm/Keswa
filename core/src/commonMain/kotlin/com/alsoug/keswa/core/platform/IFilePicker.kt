package com.alsoug.keswa.core.platform

/**
 * Platform bridge for user file picking (KD-005, KD-004).
 *
 * Implemented natively per target:
 * - Desktop: native OS file dialog via AWT FileDialog
 * - Android: Storage Access Framework / Document contract
 */
interface IFilePicker {
    /**
     * Prompts the user to pick a text spreadsheet file (.csv, .tsv, .txt).
     * Returns null if the user cancelled the dialog.
     */
    suspend fun pickSpreadsheet(): Result<SpreadsheetFile?>
}

/**
 * The name and text contents of a picked spreadsheet file.
 */
data class SpreadsheetFile(
    val name: String,
    val content: String,
)
