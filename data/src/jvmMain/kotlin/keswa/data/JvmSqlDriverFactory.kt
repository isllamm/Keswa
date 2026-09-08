package keswa.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import keswa.core.common.AppPaths
import keswa.data.db.KeswaDatabase
import java.io.File

/**
 * The one legitimate platform-specific piece of :data for Phase 0 — see docs/architecture.md §2's
 * note on SqlDriverFactory. PRAGMAs and the durability rationale are in ADR-004.
 */
class JvmSqlDriverFactory(private val appPaths: AppPaths) {

    fun create(): SqlDriver {
        val dbFile = File(appPaths.databaseFile)
        dbFile.parentFile?.mkdirs()
        val isNewDatabase = !dbFile.exists() || dbFile.length() == 0L

        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        applyPragmas(driver)
        if (isNewDatabase) {
            KeswaDatabase.Schema.create(driver)
        }
        return driver
    }

    private fun applyPragmas(driver: SqlDriver) {
        driver.execute(null, "PRAGMA journal_mode=WAL", 0)
        driver.execute(null, "PRAGMA synchronous=FULL", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        driver.execute(null, "PRAGMA busy_timeout=5000", 0)
        driver.execute(null, "PRAGMA wal_autocheckpoint=1000", 0)
    }
}
