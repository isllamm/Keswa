package com.alsoug.keswa.core.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import kotlinx.coroutines.withContext

/**
 * Android [IFilePicker] implementation.
 *
 * Bridges the UI activity / contract callback with the domain/presentation layer.
 */
class AndroidFilePicker(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : IFilePicker {

    private var launcher: (suspend () -> Uri?)? = null

    /**
     * Binds the platform Activity or Compose launcher callback.
     */
    fun bindLauncher(launcher: (suspend () -> Uri?)?) {
        this.launcher = launcher
    }

    override suspend fun pickSpreadsheet(): Result<SpreadsheetFile?> = runCatchingCancellable {
        val launch = launcher ?: error("File picker launcher not attached")
        val uri = launch() ?: return@runCatchingCancellable null

        withContext(dispatchers.io) {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Could not read file content from $uri")

            val name = resolveFileName(uri) ?: "imported_catalogue.csv"
            SpreadsheetFile(name = name, content = content)
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        }.getOrNull()
    }
}
