package com.alsoug.keswa.core.platform

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.FilenameFilter

/**
 * Desktop [IFilePicker] implementation using native OS AWT [FileDialog].
 */
class DesktopFilePicker(
    private val dispatchers: DispatcherProvider,
) : IFilePicker {

    override suspend fun pickSpreadsheet(): Result<SpreadsheetFile?> = withContext(dispatchers.main) {
        runCatchingCancellable {
            if (GraphicsEnvironment.isHeadless()) {
                return@runCatchingCancellable null
            }

            val dialog = FileDialog(null as Frame?, "Import Catalogue", FileDialog.LOAD).apply {
                filenameFilter = FilenameFilter { _, name ->
                    val lower = name.lowercase()
                    lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")
                }
                isVisible = true
            }

            val dir = dialog.directory
            val file = dialog.file
            dialog.dispose()

            if (dir != null && file != null) {
                withContext(dispatchers.io) {
                    val f = File(dir, file)
                    if (f.exists() && f.isFile) {
                        SpreadsheetFile(name = f.name, content = f.readText(Charsets.UTF_8))
                    } else {
                        null
                    }
                }
            } else {
                null
            }
        }
    }
}
