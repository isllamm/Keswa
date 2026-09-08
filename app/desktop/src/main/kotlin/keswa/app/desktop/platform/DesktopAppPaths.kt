package keswa.app.desktop.platform

import keswa.core.common.AppPaths
import java.io.File

/**
 * Windows target: `%PROGRAMDATA%\Keswa\...` (docs/architecture.md §7.1) — shared across Windows
 * accounts, never under Program Files. macOS/Linux fall back to a normal per-user app-data
 * location so the app is actually runnable on a dev machine; production ships on Windows.
 * `KESWA_DATA_DIR` overrides everything, for tests and USB-drive installs.
 */
class DesktopAppPaths : AppPaths {

    private val root: File = run {
        System.getenv("KESWA_DATA_DIR")?.let { return@run File(it) }

        val os = System.getProperty("os.name").orEmpty().lowercase()
        when {
            os.contains("win") -> File(System.getenv("PROGRAMDATA") ?: """C:\ProgramData""", "Keswa")
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/Keswa")
            else -> File(System.getProperty("user.home"), ".local/share/keswa")
        }
    }

    override val databaseFile: String = File(root, "data/keswa.db").absolutePath
    override val backupDir: String = File(root, "backups").absolutePath
    override val logDir: String = File(root, "logs").absolutePath
    override val exportsDir: String = File(root, "exports").absolutePath
    override val configDir: String = File(root, "config").absolutePath

    init {
        listOf(databaseFile, backupDir, logDir, exportsDir, configDir).forEach { path ->
            val dir = if (path == databaseFile) File(path).parentFile else File(path)
            dir?.mkdirs()
        }
    }
}
