package keswa.core.common

/**
 * Where this install keeps its data. Windows target: `%PROGRAMDATA%\Keswa\...` — see
 * docs/architecture.md §7.1. The interface lives here so :data can depend on it without knowing
 * which OS it's running on; the concrete resolution is bound in :app:desktop.
 */
interface AppPaths {
    val databaseFile: String
    val backupDir: String
    val logDir: String
    val exportsDir: String
    val configDir: String
}
